package dev.ffakit.gui;

import dev.ffakit.kit.EditingSession;
import dev.ffakit.util.ItemStackUtil;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Armor Trim GUI(鍛冶台風カスタムChest GUI・27スロット)。
 *
 * 配置: 防具=10 / 素材=13 / 模様=16 / 完成品=22 (それ以外は灰色ガラス)
 * 素材・模様はPaper 26.2のRegistry(RegistryKey.TRIM_MATERIAL / TRIM_PATTERN)から動的に取得する。
 */
public final class ArmorTrimGUI {
    public static final int SIZE = 27;
    public static final int ARMOR_SLOT = 10;
    public static final int MATERIAL_SLOT = 13;
    public static final int PATTERN_SLOT = 16;
    public static final int RESULT_SLOT = 22;

    private ArmorTrimGUI() {
    }

    /** Armor Trim GUIを作る(開くのは呼び出し側)。 */
    public static Inventory create(EditingSession session, ItemStack target) {
        // Registry APIから現在利用可能な素材・模様を動的に列挙
        Registry<TrimMaterial> materialRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
        Registry<TrimPattern> patternRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
        List<TrimMaterial> materials = materialRegistry.stream().toList();
        List<TrimPattern> patterns = patternRegistry.stream().toList();
        List<NamespacedKey> materialKeys = new ArrayList<>();
        for (TrimMaterial m : materials) {
            materialKeys.add(materialRegistry.getKey(m));
        }
        List<NamespacedKey> patternKeys = new ArrayList<>();
        for (TrimPattern p : patterns) {
            patternKeys.add(patternRegistry.getKey(p));
        }

        // 現在の防具に付いているTrimを初期選択にする(無ければ「なし」= -1)
        int mi = -1;
        int pi = -1;
        if (target.getItemMeta() instanceof ArmorMeta armorMeta && armorMeta.hasTrim()) {
            ArmorTrim trim = armorMeta.getTrim();
            if (trim != null) {
                mi = indexOf(materialKeys, materialRegistry.getKey(trim.getMaterial()));
                pi = indexOf(patternKeys, patternRegistry.getKey(trim.getPattern()));
            }
        }

        ArmorTrimHolder holder = new ArmorTrimHolder(session, target.clone(),
                materials, materialKeys, patterns, patternKeys, mi, pi);
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text("Armor Trim 編集"));
        holder.setInventory(inv);

        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, ItemStackUtil.glass());
        }
        inv.setItem(ARMOR_SLOT, ItemStackUtil.asGuiPreview(target,
                List.of(Component.text("(編集対象の防具)", NamedTextColor.GRAY))));
        refresh(holder);
        return inv;
    }

    private static int indexOf(List<NamespacedKey> keys, NamespacedKey key) {
        for (int i = 0; i < keys.size(); i++) {
            if (Objects.equals(keys.get(i), key)) {
                return i;
            }
        }
        return -1;
    }

    /** 素材スロットをクリック: なし → 素材1 → ... → 最後 → なし の循環。 */
    public static void cycleMaterial(ArmorTrimHolder holder) {
        int next = holder.materialIndex() + 1;
        if (next >= holder.materialCount()) {
            next = -1;
        }
        holder.setMaterialIndex(next);
        refresh(holder);
    }

    /** 模様スロットをクリック: なし → 模様1 → ... → 最後 → なし の循環。 */
    public static void cyclePattern(ArmorTrimHolder holder) {
        int next = holder.patternIndex() + 1;
        if (next >= holder.patternCount()) {
            next = -1;
        }
        holder.setPatternIndex(next);
        refresh(holder);
    }

    /** 素材・模様・完成品の表示を現在の選択状態に更新する。 */
    public static void refresh(ArmorTrimHolder holder) {
        Inventory inv = holder.getInventory();
        inv.setItem(MATERIAL_SLOT, materialIcon(holder));
        inv.setItem(PATTERN_SLOT, patternIcon(holder));
        inv.setItem(RESULT_SLOT, resultPreview(holder));
    }

    private static ItemStack materialIcon(ArmorTrimHolder h) {
        NamespacedKey key = h.currentMaterialKey();
        Material icon = key == null ? Material.BARRIER : materialItem(key.getKey());
        String label = key == null ? "なし" : key.getKey();
        return ItemStackUtil.guiItem(icon, Component.text("素材: " + label, NamedTextColor.AQUA),
                List.of(Component.text("クリックで次の素材へ", NamedTextColor.GRAY),
                        Component.text((h.materialIndex() + 1) + " / " + h.materialCount(), NamedTextColor.DARK_GRAY)));
    }

    private static ItemStack patternIcon(ArmorTrimHolder h) {
        NamespacedKey key = h.currentPatternKey();
        Material icon = key == null ? Material.BARRIER : patternItem(key.getKey());
        String label = key == null ? "なし" : key.getKey();
        return ItemStackUtil.guiItem(icon, Component.text("模様: " + label, NamedTextColor.LIGHT_PURPLE),
                List.of(Component.text("クリックで次の模様へ", NamedTextColor.GRAY),
                        Component.text((h.patternIndex() + 1) + " / " + h.patternCount(), NamedTextColor.DARK_GRAY)));
    }

    /** 完成品プレビュー(元の防具に現在の選択を反映したもの)。 */
    private static ItemStack resultPreview(ArmorTrimHolder h) {
        ItemStack applied = applyTrim(h.armor(), h.currentTrim());
        String state = h.currentTrim() == null ? "Trimなし(装飾を外す)" : "Trimを付ける";
        return ItemStackUtil.asGuiPreview(applied, List.of(
                Component.empty(),
                Component.text("完成品: " + state, NamedTextColor.YELLOW),
                Component.text("クリックで確定して戻る", NamedTextColor.GREEN)));
    }

    /**
     * 防具のArmor Trimだけを変更した複製を返す。
     * 名前・Lore・エンチャント・CustomModelData・耐久値などTrim以外のItemMetaは保持する。
     * trim が null ならTrimを外す。
     */
    public static ItemStack applyTrim(ItemStack armor, ArmorTrim trim) {
        ItemStack copy = armor.clone();
        copy.editMeta(ArmorMeta.class, meta -> meta.setTrim(trim));
        return copy;
    }

    /** 素材キー → 表示用アイテム(見つからなければ紙)。 */
    private static Material materialItem(String key) {
        if (key.equals("lapis")) {
            return Material.LAPIS_LAZULI;
        }
        String[] candidates = {key + "_ingot", key, key + "_shard", key + "_brick"};
        for (String c : candidates) {
            Material m = Material.matchMaterial(c);
            if (m != null && m.isItem() && !m.isAir()) {
                return m;
            }
        }
        return Material.PAPER;
    }

    /** 模様キー → 鍛冶型アイテム(見つからなければ紙)。 */
    private static Material patternItem(String key) {
        Material m = Material.matchMaterial(key + "_armor_trim_smithing_template");
        return (m != null && m.isItem() && !m.isAir()) ? m : Material.PAPER;
    }
}
