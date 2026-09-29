package dev.ffakit.listener;

import dev.ffakit.FFAKitPlugin;
import dev.ffakit.util.ItemStackUtil;
import dev.ffakit.util.Msg;
import dev.ffakit.util.PermissionUtil;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/** キット編集本の操作、ログアウト・キック・死亡などプレイヤー関連の処理。 */
public final class PlayerListener implements Listener {
    private final FFAKitPlugin plugin;

    public PlayerListener(FFAKitPlugin plugin) {
        this.plugin = plugin;
    }

    /** キット編集本: 右クリックでキット選択GUIを開き、効果音を鳴らす。 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        // PersistentDataContainerで判定(表示名では判定しない)
        if (!ItemStackUtil.isEditorBook(e.getItem())) {
            return;
        }
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setCancelled(true);

        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return; // 左クリックは何もしない
        }
        Player player = e.getPlayer();
        if (!PermissionUtil.hasLevel4(player)) {
            Msg.error(player, "この機能を使用するには、OPレベル4以上が必要です。");
            return;
        }
        if (plugin.editGui().isEditing(player)) {
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.25f);
        plugin.selectionGui().open(player, 0);
    }

    /** ログアウト: 元のPlayerInventoryがまだ操作できるこのタイミングで安全に復元する。 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        plugin.editGui().finish(e.getPlayer(), true, true);
    }

    /** キック: 編集状態を安全に終了(キックがキャンセルされた場合は何もしない)。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent e) {
        plugin.editGui().finish(e.getPlayer(), true, true);
    }

    /** ログイン時: 前回の異常終了で残った編集バックアップがあれば復元する。 */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.recoverBackup(e.getPlayer());
    }

    // ---- 編集中の安全対策 ----

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (plugin.editGui().isEditing(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && plugin.editGui().isEditing(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (plugin.editGui().isEditing(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    /** 編集中はダメージを受けない(死亡による所持品ロスト防止)。 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && plugin.editGui().isEditing(p)) {
            e.setCancelled(true);
        }
    }

    /** 編集中に死亡した場合: 元の所持品を復元し、ドロップは出さない(keepInventory)。 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (plugin.editGui().isEditing(p)) {
            plugin.editGui().finish(p, false, true);
            e.setKeepInventory(true);
            e.getDrops().clear();
        }
    }
}
