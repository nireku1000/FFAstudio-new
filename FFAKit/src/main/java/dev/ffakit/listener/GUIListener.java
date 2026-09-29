package dev.ffakit.listener;

import dev.ffakit.FFAKitPlugin;
import dev.ffakit.gui.ArmorTrimGUI;
import dev.ffakit.gui.ArmorTrimHolder;
import dev.ffakit.gui.KitEditGUI;
import dev.ffakit.gui.KitEditHolder;
import dev.ffakit.gui.KitSelectionHolder;
import dev.ffakit.kit.EditingSession;
import dev.ffakit.kit.EquipSlot;
import dev.ffakit.kit.Kit;
import dev.ffakit.util.ItemStackUtil;
import dev.ffakit.util.Msg;
import dev.ffakit.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * 3つのGUI(キット選択 / キット編集 / Armor Trim)のInventoryイベント処理。
 * GUIの識別はタイトルではなく InventoryHolder で行う。
 */
public final class GUIListener implements Listener {
    private final FFAKitPlugin plugin;

    public GUIListener(FFAKitPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================== クリック

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (holder instanceof KitSelectionHolder h) {
            handleSelection(e, player, top, h);
        } else if (holder instanceof KitEditHolder h) {
            handleEdit(e, player, top, h);
        } else if (holder instanceof ArmorTrimHolder h) {
            handleTrim(e, player, top, h);
        }
    }

    // ------------------------------------------------------------------ キット選択GUI

    /** キット選択GUI: キットアイコン(左右クリック)以外は、下側のプレイヤーインベントリも含めて完全に操作禁止。 */
    private void handleSelection(InventoryClickEvent e, Player player, Inventory top, KitSelectionHolder h) {
        e.setCancelled(true); // Shift/数字キー/ダブルクリック/Q/F/ドラッグ等、すべて無効
        if (e.getClickedInventory() != top) {
            return;
        }
        ClickType click = e.getClick();
        // GUI内は左クリックに統一。右クリックでは操作しない。
        if (click != ClickType.LEFT) {
            return;
        }
        int slot = e.getRawSlot();
        if (h.isPrevSlot(slot)) {
            playUi(player, 1.0f, 1.15f);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.selectionGui().open(player, h.page() - 1));
            return;
        }
        if (h.isNextSlot(slot)) {
            playUi(player, 1.0f, 1.15f);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.selectionGui().open(player, h.page() + 1));
            return;
        }
        String kitId = h.kitAt(slot);
        if (kitId == null) {
            return;
        }
        Kit kit = plugin.kits().getKit(kitId);
        if (kit == null) {
            Msg.error(player, "そのキットは存在しません。");
            return;
        }
        if (!PermissionUtil.hasLevel4(player)) {
            Msg.error(player, "この機能を使用するには、OPレベル4以上が必要です。");
            return;
        }
        // クリックイベント内ではGUIを切り替えず、次tickで編集GUIを開く
        playUi(player, 1.0f, 1.35f);
        Bukkit.getScheduler().runTask(plugin, () -> plugin.editGui().open(player, kit));
    }

    // ------------------------------------------------------------------ キット編集GUI

    private void handleEdit(InventoryClickEvent e, Player player, Inventory top, KitEditHolder h) {
        EditingSession s = h.session();
        KitEditGUI gui = plugin.editGui();
        if (gui.getSession(player.getUniqueId()) != s || s.ended || s.state != EditingSession.State.EDIT_GUI) {
            e.setCancelled(true);
            return;
        }
        ClickType click = e.getClick();
        InventoryAction action = e.getAction();

        // ---- 常に禁止する操作 ----
        // Shift(MOVE_TO_OTHER_INVENTORY)・ダブルクリック(COLLECT_TO_CURSOR)は上側のガラスへ影響する。
        // Q/Ctrl+Q/カーソルのドロップは実物が地面に出て複製になる。F(オフハンド交換)・中クリック・クリエイティブも禁止。
        switch (click) {
            case SHIFT_LEFT, SHIFT_RIGHT, DOUBLE_CLICK, DROP, CONTROL_DROP, SWAP_OFFHAND, MIDDLE, CREATIVE,
                 WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT, UNKNOWN -> {
                e.setCancelled(true);
                return;
            }
            default -> {
            }
        }
        switch (action) {
            case MOVE_TO_OTHER_INVENTORY, COLLECT_TO_CURSOR, CLONE_STACK, DROP_ALL_CURSOR, DROP_ONE_CURSOR,
                 DROP_ALL_SLOT, DROP_ONE_SLOT, UNKNOWN -> {
                e.setCancelled(true);
                return;
            }
            default -> {
            }
        }
        if (e.getClickedInventory() == null) {
            e.setCancelled(true);
            return;
        }

        int raw = e.getRawSlot();

        // ---- 上側(編集GUI) ----
        if (e.getClickedInventory() == top) {
            e.setCancelled(true); // 既定は禁止。許可する操作だけ手動で処理する
            if (click == ClickType.NUMBER_KEY) {
                return;
            }
            if (raw == KitEditGUI.CANCEL_SLOT) {
                if (click == ClickType.LEFT) {
                    // キャンセルボタン: 保存せず閉じて、元の所持品・カーソルを復元
                    s.cancelled = true;
                    playUi(player, 0.9f, 0.8f);
                    Bukkit.getScheduler().runTask(plugin, player::closeInventory);
                }
                return;
            }
            EquipSlot slot = EquipSlot.fromGuiSlot(raw);
            if (slot == null) {
                return; // 装飾ガラスは完全固定
            }
            handleEquipClick(player, s, top, slot, click);
            return;
        }

        // ---- 下側(実プレイヤーインベントリ = キットの通常インベントリ) ----
        // GUI内の操作は左クリックに統一。右クリックは何もしない。
        if (click == ClickType.RIGHT) {
            e.setCancelled(true);
            return;
        }
        // 左クリックは通常のインベントリ操作を許可
    }

    /** 上側の防具・オフハンド枠のクリックを手動処理する。 */
    private void handleEquipClick(Player player, EditingSession s, Inventory top, EquipSlot slot, ClickType click) {
        if (click != ClickType.LEFT) {
            return;
        }
        int guiSlot = slot.guiSlot();
        ItemStack slotItem = top.getItem(guiSlot);
        boolean slotEmpty = ItemStackUtil.isEmpty(slotItem) || ItemStackUtil.isGuiItem(slotItem);
        ItemStack cursor = player.getItemOnCursor();
        boolean cursorEmpty = ItemStackUtil.isEmpty(cursor);

        // 装備済み防具を左クリックすると装飾(Armor Trim)画面へ。
        // 右クリックはGUI内では使用しない。
        if (!slotEmpty && cursorEmpty && ItemStackUtil.isTrimmable(slotItem)) {
            tryOpenTrim(player, s, new EditingSession.TrimTarget(true, guiSlot));
            playUi(player, 1.0f, 1.25f);
            return;
        }

        if (slotEmpty) {
            if (cursorEmpty || !slot.accepts(cursor)) {
                return;
            }
            int put = 1;
            ItemStack placed = cursor.clone();
            placed.setAmount(put);
            top.setItem(guiSlot, placed);
            ItemStack rest = cursor.clone();
            rest.setAmount(cursor.getAmount() - put);
            player.setItemOnCursor(rest.getAmount() <= 0 ? null : rest);
        } else if (cursorEmpty) {
            // 取り外し: アイテムをカーソルへ、枠はガラスに戻す
            player.setItemOnCursor(slotItem.clone());
            top.setItem(guiSlot, plugin.editGui().placeholder(slot));
        } else {
            // 交換
            if (!slot.accepts(cursor) || (slot != EquipSlot.OFFHAND && cursor.getAmount() > 1)) {
                return;
            }
            ItemStack old = slotItem.clone();
            top.setItem(guiSlot, cursor.clone());
            player.setItemOnCursor(old);
        }
        playUi(player, 0.8f, 1.0f);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    private void tryOpenTrim(Player player, EditingSession s, EditingSession.TrimTarget target) {
        if (!ItemStackUtil.isEmpty(player.getItemOnCursor())) {
            Msg.error(player, "カーソルのアイテムを置いてから、防具を右クリックしてください。");
            return;
        }
        plugin.editGui().requestTrim(player, s, target);
    }

    // ------------------------------------------------------------------ Armor Trim GUI

    /** Armor Trim GUI: 素材・模様・完成品以外は操作禁止。 */
    private void handleTrim(InventoryClickEvent e, Player player, Inventory top, ArmorTrimHolder h) {
        e.setCancelled(true);
        EditingSession s = h.session();
        if (plugin.editGui().getSession(player.getUniqueId()) != s || s.ended
                || s.state != EditingSession.State.TRIM_GUI || s.transitioning) {
            return;
        }
        if (e.getClickedInventory() != top) {
            return;
        }
        ClickType click = e.getClick();
        if (click != ClickType.LEFT) {
            return;
        }
        switch (e.getRawSlot()) {
            case ArmorTrimGUI.MATERIAL_SLOT -> {
                ArmorTrimGUI.cycleMaterial(h);
                playUi(player, 0.75f, 1.15f);
            }
            case ArmorTrimGUI.PATTERN_SLOT -> {
                ArmorTrimGUI.cyclePattern(h);
                playUi(player, 0.75f, 1.35f);
            }
            case ArmorTrimGUI.RESULT_SLOT -> {
                playUi(player, 1.0f, 1.55f);
                plugin.editGui().applyTrim(player, h);
            }
            default -> {
            }
        }
    }

    /** GUI操作用の軽いクリック音。音量・ピッチを操作ごとに変えて単調にならないようにする。 */
    private void playUi(Player player, float volume, float pitch) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, volume, pitch);
    }

    // ================================================================== ドラッグ

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        InventoryHolder holder = top.getHolder(false);
        if (holder instanceof KitSelectionHolder || holder instanceof ArmorTrimHolder) {
            e.setCancelled(true);
            return;
        }
        if (holder instanceof KitEditHolder h) {
            EditingSession s = h.session();
            if (s.ended || s.state != EditingSession.State.EDIT_GUI) {
                e.setCancelled(true);
                return;
            }
            // 上側(編集GUI)にかかるドラッグは禁止。下側だけのドラッグは許可。
            for (int raw : e.getRawSlots()) {
                if (raw < top.getSize()) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    // ================================================================== 閉じる

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = e.getInventory().getHolder(false);
        if (holder instanceof KitEditHolder h) {
            EditingSession s = h.session();
            if (s.ended || s.transitioning || plugin.editGui().getSession(player.getUniqueId()) != s) {
                return;
            }
            // 通常のGUI閉じる操作: 個人カスタムとして保存し、元の所持品・カーソルを復元して終了
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.8f, 1.0f);
            plugin.editGui().finish(player, true, false);
        } else if (holder instanceof ArmorTrimHolder h) {
            EditingSession s = h.session();
            if (s.ended || s.transitioning || plugin.editGui().getSession(player.getUniqueId()) != s) {
                return;
            }
            // Escなどで閉じた場合: Trimの変更は破棄して編集GUIへ戻る
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.7f, 1.2f);
            plugin.editGui().returnToEdit(player, s);
        }
    }
}
