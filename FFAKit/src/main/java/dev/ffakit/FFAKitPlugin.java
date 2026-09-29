package dev.ffakit;

import dev.ffakit.command.FFAKitCommand;
import dev.ffakit.command.KitCommand;
import dev.ffakit.gui.KitEditGUI;
import dev.ffakit.gui.KitSelectionGUI;
import dev.ffakit.kit.KitManager;
import dev.ffakit.listener.GUIListener;
import dev.ffakit.listener.PlayerListener;
import dev.ffakit.storage.BackupStorage;
import dev.ffakit.storage.KitStorage;
import dev.ffakit.storage.PlayerKitStorage;
import dev.ffakit.util.ItemStackUtil;
import dev.ffakit.util.Msg;
import dev.ffakit.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * FFAKit - FFAサーバー用のキット作成・編集・配布プラグイン(Paper 26.2 / Java 25)。
 *
 * 【異常終了についての注意】
 * JVMクラッシュ、OS強制終了、プロセスkillなど、Java/Paperの終了処理自体が実行されない異常終了については、
 * プラグイン単独で100%の復旧を保証できません。本プラグインは編集開始時に元の所持品を
 * plugins/FFAKit/backup/ へ書き出し、次回ログイン時に自動復元することで被害を最小限にします。
 */
public final class FFAKitPlugin extends JavaPlugin {
    private KitManager kitManager;
    private PlayerKitStorage playerKits;
    private BackupStorage backups;
    private KitEditGUI editGui;
    private KitSelectionGUI selectionGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ItemStackUtil.init(this);
        PermissionUtil.init(this);

        KitStorage kitStorage = new KitStorage(new File(getDataFolder(), "kits.yml"), getLogger());
        this.playerKits = new PlayerKitStorage(new File(getDataFolder(), "players"), getLogger());
        this.backups = new BackupStorage(new File(getDataFolder(), "backup"), getLogger());
        this.kitManager = new KitManager(this, kitStorage, playerKits);
        try {
            kitManager.load();
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "kits.yml の読み込みに失敗しました。データ保護のためプラグインを無効化します。", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.editGui = new KitEditGUI(this);
        this.selectionGui = new KitSelectionGUI(this);

        FFAKitCommand ffakit = new FFAKitCommand(this);
        KitCommand kit = new KitCommand(this);
        getCommand("ffakit").setExecutor(ffakit);
        getCommand("ffakit").setTabCompleter(ffakit);
        getCommand("kit").setExecutor(kit);
        getCommand("kit").setTabCompleter(kit);

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // 再読込直後などで、すでにオンラインのプレイヤーに残っているバックアップも復元する
        for (Player p : Bukkit.getOnlinePlayers()) {
            recoverBackup(p);
        }
        getLogger().info("FFAKit を有効化しました。登録キット数: " + kitManager.getAll().size());
    }

    @Override
    public void onDisable() {
        // 編集中のプレイヤーを安全に終了(編集内容を保存 → 元のインベントリ復元 → セッション破棄)
        if (editGui != null) {
            editGui.finishAll();
        }
    }

    /** 前回の異常終了で残った編集バックアップがあれば、元の所持品を復元する。 */
    public void recoverBackup(Player player) {
        if (editGui == null || editGui.isEditing(player)) {
            return;
        }
        try {
            BackupStorage.Backup backup = backups.load(player.getUniqueId());
            if (backup == null) {
                return;
            }
            player.setItemOnCursor(null);
            backup.contents().applyTo(player, true);
            if (backup.cursor() != null) {
                ItemStackUtil.giveOrDrop(player, backup.cursor());
            }
            backups.delete(player.getUniqueId());
            Msg.info(player, "前回のキット編集が異常終了したため、元の所持品を復元しました。");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "編集バックアップの復元に失敗しました: " + player.getName(), e);
        }
    }

    public KitManager kits() {
        return kitManager;
    }

    public PlayerKitStorage playerKits() {
        return playerKits;
    }

    public BackupStorage backups() {
        return backups;
    }

    public KitEditGUI editGui() {
        return editGui;
    }

    public KitSelectionGUI selectionGui() {
        return selectionGui;
    }
}
