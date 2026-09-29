package dev.ffakit.storage;

import dev.ffakit.kit.KitContents;
import dev.ffakit.util.FileUtil;
import dev.ffakit.util.ItemStackUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * 編集中の「元の所持品」のバックアップ(plugins/FFAKit/backup/&lt;UUID&gt;.yml)。
 * 編集開始時に書き、正常終了時に削除する。
 * サーバーがクラッシュしても、次回ログイン時にこのファイルから所持品を復元できる。
 */
public final class BackupStorage {
    public record Backup(KitContents contents, ItemStack cursor) {
    }

    private final File dir;
    private final Logger log;

    public BackupStorage(File dir, Logger log) {
        this.dir = dir;
        this.log = log;
    }

    private File file(UUID uuid) {
        return new File(dir, uuid + ".yml");
    }

    public void save(UUID uuid, KitContents contents, ItemStack cursor) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection s = yaml.createSection("contents");
        ItemStackUtil.writeContents(s, contents);
        yaml.set("cursor", cursor == null ? null : ItemStackUtil.encode(cursor));
        FileUtil.atomicWrite(file(uuid), yaml.saveToString());
    }

    /** バックアップが無ければ null。 */
    public Backup load(UUID uuid) throws IOException {
        File f = file(uuid);
        if (!f.exists()) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(f);
        } catch (InvalidConfigurationException e) {
            throw new IOException("バックアップの形式が不正です: " + f.getName(), e);
        }
        ConfigurationSection s = yaml.getConfigurationSection("contents");
        if (s == null) {
            return null;
        }
        KitContents contents = ItemStackUtil.readContents(s, log);
        String cur = yaml.getString("cursor");
        ItemStack cursor = null;
        if (cur != null && !cur.isEmpty()) {
            try {
                cursor = ItemStackUtil.clean(ItemStackUtil.decode(cur));
            } catch (Exception e) {
                log.warning("バックアップのカーソルアイテムを読み込めませんでした。");
            }
        }
        return new Backup(contents, cursor);
    }

    public void delete(UUID uuid) {
        File f = file(uuid);
        if (f.exists() && !f.delete()) {
            log.warning("バックアップファイルを削除できませんでした: " + f.getName());
        }
    }
}
