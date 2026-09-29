package dev.ffakit.kit;

import dev.ffakit.FFAKitPlugin;
import dev.ffakit.storage.KitStorage;
import dev.ffakit.storage.PlayerKitStorage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** キットの登録・順序・検索を管理する。 */
public final class KitManager {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_-]{1,32}$");

    private final FFAKitPlugin plugin;
    private final KitStorage storage;
    private final PlayerKitStorage playerStorage;
    /** 登録順を保持。キーは小文字化したid(大文字小文字を区別しない)。 */
    private final LinkedHashMap<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(FFAKitPlugin plugin, KitStorage storage, PlayerKitStorage playerStorage) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerStorage = playerStorage;
    }

    public void load() throws IOException {
        kits.clear();
        kits.putAll(storage.load());
    }

    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** キット名の検証。問題なければ null、問題があれば日本語エラーメッセージ。 */
    public String validateName(String name) {
        if (name == null || name.isEmpty()) {
            return "キット名が空です。";
        }
        if (name.length() > 32) {
            return "キット名が長すぎます(32文字まで)。";
        }
        // '/' '\' '.' 空白などパス操作に使える文字はすべて拒否(英数字・日本語・_ - のみ許可)
        if (!NAME_PATTERN.matcher(name).matches()) {
            return "キット名に使えない文字が含まれています(文字・数字・_ - のみ使えます)。";
        }
        return null;
    }

    public Kit getKit(String name) {
        return name == null ? null : kits.get(normalize(name));
    }

    public List<Kit> getAll() {
        return new ArrayList<>(kits.values());
    }

    public List<String> getDisplayNames() {
        List<String> names = new ArrayList<>();
        for (Kit k : kits.values()) {
            names.add(k.getDisplayName());
        }
        return names;
    }

    /**
     * 基本キットを保存する。存在しなければ新規作成(末尾に追加)、あれば上書き(登録順は変えない)。
     *
     * @return 新規作成ならtrue
     */
    public boolean setBase(String name, KitContents contents) throws IOException {
        String id = normalize(name);
        Kit existing = kits.get(id);
        if (existing != null) {
            KitContents old = existing.getBase();
            existing.setBase(contents.deepCopy());
            try {
                storage.save(kits.values());
            } catch (IOException e) {
                existing.setBase(old);
                throw e;
            }
            return false;
        }
        Material icon = configIcon(id);
        Kit kit = new Kit(id, name, icon != null ? icon : Material.CHEST, contents.deepCopy());
        kits.put(id, kit);
        try {
            storage.save(kits.values());
        } catch (IOException e) {
            kits.remove(id);
            throw e;
        }
        return true;
    }

    /**
     * キットを完全削除する(基本キット・アイコン設定・登録情報・全プレイヤーの個人カスタム)。
     * 他のキットには影響しない。
     *
     * @return 削除した個人カスタムの数
     */
    public int delete(String name) throws IOException {
        String id = normalize(name);
        Kit removed = kits.remove(id);
        if (removed == null) {
            return 0;
        }
        try {
            storage.save(kits.values());
        } catch (IOException e) {
            kits.put(id, removed); // 失敗時は元に戻す(順序は末尾になるが再読込で復元される)
            throw e;
        }
        int customs = playerStorage.removeKitFromAll(id);
        removeConfigIcon(id);
        return customs;
    }

    /** GUIに表示するアイコン。config.yml の設定を優先、無ければ保存済みアイコン、最後にCHEST。 */
    public Material resolveIcon(Kit kit) {
        Material m = configIcon(kit.getId());
        if (m != null) {
            return m;
        }
        return kit.getIcon() != null ? kit.getIcon() : Material.CHEST;
    }

    private Material configIcon(String id) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("kits");
        if (section == null) {
            return null;
        }
        for (String key : section.getKeys(false)) {
            if (normalize(key).equals(id)) {
                String name = section.getString(key + ".icon");
                if (name != null) {
                    Material m = Material.matchMaterial(name);
                    if (m != null && m.isItem() && !m.isAir()) {
                        return m;
                    }
                }
            }
        }
        return null;
    }

    private void removeConfigIcon(String id) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("kits");
        if (section == null) {
            return;
        }
        boolean changed = false;
        for (String key : new ArrayList<>(section.getKeys(false))) {
            if (normalize(key).equals(id)) {
                plugin.getConfig().set("kits." + key, null);
                changed = true;
            }
        }
        if (changed) {
            plugin.saveConfig();
        }
    }

    /** 未使用警告避け。 */
    @SuppressWarnings("unused")
    private static void unused(Map<String, String> m) {
    }
}
