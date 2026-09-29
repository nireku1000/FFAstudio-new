package dev.ffakit.gui;

import dev.ffakit.FFAKitPlugin;
import dev.ffakit.kit.EditingSession;
import dev.ffakit.kit.EquipSlot;
import dev.ffakit.kit.Kit;
import dev.ffakit.kit.KitContents;
import dev.ffakit.util.ItemStackUtil;
import dev.ffakit.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * キット編集GUI(54スロット)と、編集セッション(Map&lt;UUID, EditingSession&gt;)の管理。
 *
 * ■ スロット配置(編集GUI上部 rawスロット 0-53)
 *   10=ヘルメット 11=チェストプレート 12=レギンス 13=ブーツ 15=オフハンド 49=キャンセル(BARRIER)
 *   それ以外は灰色ガラス(完全固定)。
 * ■ 下側36スロット(rawスロット 54-89)は「実際のプレイヤーインベントリ」で、そのまま通常インベントリとして保存される。
 *   rawスロット → PlayerInventoryスロットの変換は {@link #rawToPlayerSlot(int, int)}。
 * ■ 編集中はプレイヤーの実際の防具・オフハンドを空にし、キットの防具はGUI上部にのみ表示する(二重化防止)。
 *
 * ■ 異常終了対策の限界:
 *   JVMクラッシュ・OS強制終了・プロセスkillなど、Java/Paperの終了処理自体が走らない異常終了は、
 *   プラグイン単独では100%復旧を保証できない。ただし編集開始時に backup/&lt;UUID&gt;.yml へ元の所持品を書き出しており、
 *   次回ログイン時に自動復元することで被害を最小限にしている。
 */
public final class KitEditGUI {
    public static final int SIZE = 54;
    public static final int CANCEL_SLOT = 49;

    private final FFAKitPlugin plugin;
    /** 編集セッションをUUID単位で管理 */
    private final Map<UUID, EditingSession> sessions = new HashMap<>();

    public KitEditGUI(FFAKitPlugin plugin) {
        this.plugin = plugin;
    }

    public EditingSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public boolean isEditing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /**
     * 編集GUIのrawスロット(下側) → PlayerInventoryスロットへの変換。
     * チェストGUI下側は、上から順に「メイン27スロット(PlayerInventory 9-35)」→「ホットバー9スロット(0-8)」。
     */
    public static int rawToPlayerSlot(int rawSlot, int topSize) {
        int idx = rawSlot - topSize;
        return idx < 27 ? idx + 9 : idx - 27;
    }

    // ------------------------------------------------------------------ 編集開始

    public void open(Player player, Kit selected) {
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id)) {
            Msg.error(player, "すでにキット編集中です。");
            return;
        }
        Kit kit = plugin.kits().getKit(selected.getId());
        if (kit == null || kit.getBase() == null) {
            Msg.error(player, "基本キットが存在しません。");
            return;
        }

        // 個人カスタムあり → 個人カスタムをロード / なし → 基本キットをロード
        KitContents custom = plugin.playerKits().get(id, kit.getId());
        boolean hadCustom = custom != null;
        KitContents source = hadCustom ? custom : kit.getBase();

        // 元のPlayerInventory・防具・オフハンド・カーソルをディープコピーで退避
        KitContents original = KitContents.fromPlayer(player);
        ItemStack cursor = ItemStackUtil.clean(player.getItemOnCursor());
        try {
            // クラッシュ対策: ディスクにも先に書き出してから編集を始める
            plugin.backups().save(id, original, cursor);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "バックアップの保存に失敗しました", e);
            Msg.error(player, "バックアップの保存に失敗したため、編集を開始できません。");
            return;
        }

        EditingSession session = new EditingSession(id, kit, original, cursor, source.deepCopy(), hadCustom);
        sessions.put(id, session);

        // キットを一時ロード(下側36スロット = 実プレイヤーインベントリ)。防具・オフハンドは空にする。
        player.setItemOnCursor(null);
        source.deepCopy().applyTo(player, false);

        KitEditHolder holder = new KitEditHolder(session);
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                Component.text("キット編集: " + kit.getDisplayName()));
        holder.setInventory(inv);
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, ItemStackUtil.glass());
        }
        for (EquipSlot slot : EquipSlot.values()) {
            ItemStack it = source.getEquip(slot);
            inv.setItem(slot.guiSlot(), it != null ? it.clone() : placeholder(slot));
        }
        inv.setItem(CANCEL_SLOT, ItemStackUtil.guiItem(Material.BARRIER,
                Component.text("キャンセル", NamedTextColor.RED),
                List.of(Component.text("保存せずに閉じて、元の所持品に戻します", NamedTextColor.GRAY))));
        session.editInventory = inv;
        session.state = EditingSession.State.EDIT_GUI;
        player.openInventory(inv);
        Msg.info(player, "キット「" + kit.getDisplayName() + "」の編集を開始しました"
                + (hadCustom ? "(個人カスタム)" : "(基本キット)") + "。");
    }

    /** 防具・オフハンドが空のときに表示する灰色ガラス。 */
    public ItemStack placeholder(EquipSlot slot) {
        return ItemStackUtil.guiItem(Material.GRAY_STAINED_GLASS_PANE,
                Component.text(slot.label(), NamedTextColor.GRAY), null);
    }

    // ------------------------------------------------------------------ 編集終了 (冪等)

    /**
     * 編集セッションを終了し、元の所持品を復元する。何度呼んでも安全(2回目以降は何もしない)。
     *
     * @param save            true なら編集内容を個人カスタムとして保存(キャンセル済みなら保存しない)
     * @param immediateCursor true なら元のカーソルアイテムをすぐインベントリへ戻す(ログアウト・無効化時)
     */
    public void finish(Player player, boolean save, boolean immediateCursor) {
        EditingSession s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        s.ended = true;

        if (save && !s.cancelled) {
            try {
                // 復元する前に、編集中の内容を読み取る
                KitContents edited = readEdited(s, player);
                Kit kit = plugin.kits().getKit(s.kit.getId());
                // キットが削除済みなら保存しない。未変更かつ個人カスタム未作成なら作らない
                // (基本キット変更が反映されなくなるのを防ぐ)。
                if (kit != null && (s.hadCustom || !edited.sameAs(s.loaded))) {
                    plugin.playerKits().save(player.getUniqueId(), kit.getId(), edited);
                    if (player.isOnline()) {
                        Msg.info(player, "キット「" + kit.getDisplayName() + "」を個人カスタムとして保存しました。");
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "個人カスタムの保存に失敗しました", e);
                Msg.error(player, "保存に失敗しました。編集内容は破棄され、元の所持品に戻ります。");
            }
        }

        // 元のPlayerInventory・防具・オフハンドを復元(ディープコピー)
        player.setItemOnCursor(null);
        s.original.deepCopy().applyTo(player, true);
        plugin.backups().delete(player.getUniqueId());

        // 元のカーソルアイテムを復元
        ItemStack cursor = s.originalCursor;
        if (cursor != null) {
            if (immediateCursor || !plugin.isEnabled()) {
                ItemStackUtil.giveOrDrop(player, cursor);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || isEditing(player)) {
                        ItemStackUtil.giveOrDrop(player, cursor);
                        return;
                    }
                    if (ItemStackUtil.isEmpty(player.getItemOnCursor())) {
                        player.setItemOnCursor(cursor.clone());
                    } else {
                        ItemStackUtil.giveOrDrop(player, cursor);
                    }
                });
            }
        }
    }

    /** 編集中のGUIの内容(下側36スロット + 上部の防具・オフハンド)を読み取る。 */
    public KitContents readEdited(EditingSession s, Player player) {
        KitContents c = new KitContents();
        ItemStack[] st = player.getInventory().getStorageContents();
        for (int i = 0; i < KitContents.STORAGE_SIZE && i < st.length; i++) {
            c.setStorage(i, st[i]);
        }
        for (EquipSlot slot : EquipSlot.values()) {
            ItemStack it = s.editInventory.getItem(slot.guiSlot());
            c.setEquip(slot, ItemStackUtil.isGuiItem(it) ? null : it);
        }
        c.removeSpecialItems();
        return c;
    }

    /** プラグイン無効化時: 編集中の全プレイヤーを安全に終了する。 */
    public void finishAll() {
        for (EditingSession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId);
            if (p != null) {
                finish(p, true, true);
                p.closeInventory();
            } else {
                sessions.remove(s.playerId);
            }
        }
    }

    /** 指定キットを編集中のセッションを(保存せず)強制終了する。キット削除時用。 */
    public void abortForKit(String kitId) {
        for (EditingSession s : new ArrayList<>(sessions.values())) {
            if (s.kit.getId().equals(kitId)) {
                Player p = Bukkit.getPlayer(s.playerId);
                if (p != null) {
                    finish(p, false, true);
                    p.closeInventory();
                    Msg.error(p, "編集中のキットが削除・リセットされたため、編集を終了しました。");
                }
            }
        }
    }

    /** 指定プレイヤーが指定キットを編集中なら(保存せず)強制終了する。reset用。 */
    public void abortFor(UUID uuid, String kitId) {
        EditingSession s = sessions.get(uuid);
        if (s != null && s.kit.getId().equals(kitId)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                finish(p, false, true);
                p.closeInventory();
                Msg.error(p, "編集中のキットが削除・リセットされたため、編集を終了しました。");
            }
        }
    }

    // ------------------------------------------------------------------ GUI切替 (編集GUI <-> Armor Trim GUI)

    private boolean isActive(Player player, EditingSession s) {
        return player.isOnline() && !s.ended && sessions.get(player.getUniqueId()) == s;
    }

    private ItemStack getTargetItem(Player player, EditingSession s, EditingSession.TrimTarget t) {
        return t.inTop() ? s.editInventory.getItem(t.slot()) : player.getInventory().getItem(t.slot());
    }

    private void setTargetItem(Player player, EditingSession s, EditingSession.TrimTarget t, ItemStack item) {
        if (t.inTop()) {
            s.editInventory.setItem(t.slot(), item);
        } else {
            player.getInventory().setItem(t.slot(), item);
        }
    }

    /** 防具の右クリック: Armor Trim GUIを開く(クリックイベント内では開かず、次tickで開く)。 */
    public void requestTrim(Player player, EditingSession s, EditingSession.TrimTarget target) {
        s.transitioning = true; // 編集GUIが閉じるイベントを「終了」と誤認しない
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!isActive(player, s)) {
                    return;
                }
                ItemStack item = getTargetItem(player, s, target);
                if (!ItemStackUtil.isTrimmable(item)) {
                    return;
                }
                Inventory inv = ArmorTrimGUI.create(s, item);
                s.trimInventory = inv;
                s.trimTarget = target;
                s.state = EditingSession.State.TRIM_GUI;
                player.openInventory(inv);
            } finally {
                s.transitioning = false;
            }
        });
    }

    /** Armor Trim GUIから編集GUIへ戻る(同じInventoryを開き直すので内容は保持される)。 */
    public void returnToEdit(Player player, EditingSession s) {
        s.transitioning = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (!isActive(player, s)) {
                    return;
                }
                s.state = EditingSession.State.EDIT_GUI;
                s.trimTarget = null;
                player.openInventory(s.editInventory);
            } finally {
                s.transitioning = false;
            }
        });
    }

    /** 完成品スロットクリック: 選択中のTrimを元の防具へ反映し、編集GUIへ戻る。 */
    public void applyTrim(Player player, ArmorTrimHolder holder) {
        EditingSession s = holder.session();
        EditingSession.TrimTarget target = s.trimTarget;
        if (target == null) {
            return;
        }
        ItemStack current = getTargetItem(player, s, target);
        if (ItemStackUtil.isTrimmable(current)) {
            // Armor Trimだけを変更(他のItemMetaは保持)
            ItemStack edited = ArmorTrimGUI.applyTrim(current, holder.currentTrim());
            setTargetItem(player, s, target, edited);
        }
        returnToEdit(player, s);
    }
}
