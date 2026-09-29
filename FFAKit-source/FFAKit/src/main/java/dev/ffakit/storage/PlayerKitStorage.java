package dev.ffakit.storage;

import dev.ffakit.kit.KitContents;
import dev.ffakit.util.FileUtil;
import dev.ffakit.util.ItemStackUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 個人カスタム(plugins/FFAKit/players/&lt;UUID&gt;.yml)の読み書き。基本キットとは完全に別ファイル。 */
public final class PlayerKitStorage {
    private final File dir;
    private final Logger log;

    public PlayerKitStorage(File dir, Logger log) {
        this.dir = dir;
        this.log = log;
    }

    private File file(UUID uuid) {
        return new File(dir, uuid + ".yml");
    }

    private YamlConfiguration loadYaml(File f) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (!f.exists()) {
            return yaml;
        }
        try {
            yaml.load(f);
        } catch (InvalidConfigurationException e) {
            // 壊れたファイルを空として上書きしないよう、エラーにする
            throw new IOException(f.getName() + " の形式が不正です", e);
        }
        return yaml;
    }

    /** 個人カスタムを取得。無ければ(または読めなければ) null。 */
    public KitContents get(UUID uuid, String kitId) {
        try {
            YamlConfiguration yaml = loadYaml(file(uuid));
            ConfigurationSection s = yaml.getConfigurationSection("kits." + kitId);
            if (s == null) {
                return null;
            }
            return ItemStackUtil.readContents(s, log);
        } catch (IOException e) {
            log.log(Level.SEVERE, "個人カスタムの読み込みに失敗しました: " + uuid, e);
            return null;
        }
    }

    public boolean has(UUID uuid, String kitId) {
        try {
            return loadYaml(file(uuid)).contains("kits." + kitId);
        } catch (IOException e) {
            return false;
        }
    }

    public void save(UUID uuid, String kitId, KitContents contents) throws IOException {
        File f = file(uuid);
        YamlConfiguration yaml = loadYaml(f);
        yaml.set("kits." + kitId, null);
        ConfigurationSection s = yaml.createSection("kits." + kitId);
        ItemStackUtil.writeContents(s, contents);
        FileUtil.atomicWrite(f, yaml.saveToString());
    }

    /** 指定プレイヤーの指定キットの個人カスタムだけ削除。 */
    public boolean remove(UUID uuid, String kitId) throws IOException {
        File f = file(uuid);
        YamlConfiguration yaml = loadYaml(f);
        if (!yaml.contains("kits." + kitId)) {
            return false;
        }
        yaml.set("kits." + kitId, null);
        FileUtil.atomicWrite(f, yaml.saveToString());
        return true;
    }

    /** 全プレイヤーの、指定キットの個人カスタムを削除(キット削除時)。削除件数を返す。 */
    public int removeKitFromAll(String kitId) throws IOException {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) {
            return 0;
        }
        int count = 0;
        IOException failure = null;
        for (File f : files) {
            try {
                YamlConfiguration yaml = loadYaml(f);
                if (yaml.contains("kits." + kitId)) {
                    yaml.set("kits." + kitId, null);
                    FileUtil.atomicWrite(f, yaml.saveToString());
                    count++;
                }
            } catch (IOException e) {
                log.log(Level.SEVERE, "個人カスタムの削除に失敗しました: " + f.getName(), e);
                failure = e;
            }
        }
        if (failure != null) {
            throw failure;
        }
        return count;
    }
}
