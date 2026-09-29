package dev.ffakit.storage;

import dev.ffakit.kit.Kit;
import dev.ffakit.kit.KitContents;
import dev.ffakit.util.FileUtil;
import dev.ffakit.util.ItemStackUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;

/** 基本キット(plugins/FFAKit/kits.yml)の読み書き。 */
public final class KitStorage {
    private final File file;
    private final Logger log;

    public KitStorage(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    public LinkedHashMap<String, Kit> load() throws IOException {
        LinkedHashMap<String, Kit> result = new LinkedHashMap<>();
        if (!file.exists()) {
            return result;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (InvalidConfigurationException e) {
            throw new IOException("kits.yml の形式が不正です", e);
        }
        ConfigurationSection kitsSection = yaml.getConfigurationSection("kits");
        if (kitsSection == null) {
            return result;
        }
        List<String> order = new ArrayList<>(yaml.getStringList("order"));
        for (String key : kitsSection.getKeys(false)) {
            if (!order.contains(key)) {
                order.add(key);
            }
        }
        for (String id : order) {
            ConfigurationSection s = kitsSection.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            String name = s.getString("name", id);
            Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
            if (icon == null || !icon.isItem() || icon.isAir()) {
                icon = Material.CHEST;
            }
            ConfigurationSection contents = s.getConfigurationSection("contents");
            if (contents == null) {
                log.warning("キット " + id + " の基本キットが見つかりません(スキップします)。");
                continue;
            }
            result.put(id, new Kit(id, name, icon, ItemStackUtil.readContents(contents, log)));
        }
        return result;
    }

    public void save(Collection<Kit> kits) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> order = new ArrayList<>();
        for (Kit kit : kits) {
            order.add(kit.getId());
            String base = "kits." + kit.getId();
            yaml.set(base + ".name", kit.getDisplayName());
            yaml.set(base + ".icon", kit.getIcon().name());
            KitContents c = kit.getBase();
            ConfigurationSection contents = yaml.createSection(base + ".contents");
            ItemStackUtil.writeContents(contents, c);
        }
        yaml.set("order", order);
        FileUtil.atomicWrite(file, yaml.saveToString());
    }
}
