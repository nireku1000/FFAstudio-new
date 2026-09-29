package dev.ffakit.util;

import dev.ffakit.kit.EquipSlot;
import dev.ffakit.kit.KitContents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** ItemStack関連のユーティリティ(保存・読込・特殊アイテム判定)。 */
public final class ItemStackUtil {
    /** キット編集本の識別キー (ffakit:kit_editor_book) */
    private static NamespacedKey bookKey;
    /** GUI用アイテムの識別キー (ffakit:gui_item)。GUI部品が現実に流出しても判別できる。 */
    private static NamespacedKey guiKey;

    private ItemStackUtil() {
    }

    public static void init(Plugin plugin) {
        bookKey = new NamespacedKey(plugin, "kit_editor_book");
        guiKey = new NamespacedKey(plugin, "gui_item");
    }

    // ---------------------------------------------------------------- 基本判定

    public static boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    /** 空なら null、そうでなければ複製を返す(ディープコピー)。 */
    public static ItemStack clean(ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    public static ItemStack cloneOrNull(ItemStack item) {
        return item == null ? null : item.clone();
    }

    /** アーマートリムを設定できる防具か。 */
    public static boolean isTrimmable(ItemStack item) {
        if (isEmpty(item)) {
            return false;
        }
        return item.getItemMeta() instanceof ArmorMeta;
    }

    // ---------------------------------------------------------------- キット編集本 (PersistentDataContainer)

    /** キット編集本を作る。表示名だけでなくPDCで識別する。 */
    public static ItemStack createEditorBook() {
        ItemStack item = new ItemStack(Material.BOOK);
        item.editMeta(meta -> {
            meta.displayName(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize("&c&lキット編集本")
                    .decoration(TextDecoration.ITALIC, false));
            // PersistentDataContainer に識別情報を書き込む (ffakit:kit_editor_book = 1)
            meta.getPersistentDataContainer().set(bookKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /** PDCを見てキット編集本かどうか判定する(表示名は見ない)。 */
    public static boolean isEditorBook(ItemStack item) {
        return hasMarker(item, bookKey);
    }

    public static boolean isGuiItem(ItemStack item) {
        return hasMarker(item, guiKey);
    }

    private static boolean hasMarker(ItemStack item, NamespacedKey key) {
        if (isEmpty(item) || !item.hasItemMeta()) {
            return false;
        }
        Byte v = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.BYTE);
        return v != null && v == 1;
    }

    // ---------------------------------------------------------------- GUI部品

    /** GUI専用アイテム(PDCでGUI部品と印を付ける)。 */
    public static ItemStack guiItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (lore != null && !lore.isEmpty()) {
                List<Component> l = new ArrayList<>();
                for (Component c : lore) {
                    l.add(c.decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(l);
            }
            meta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    /** 装飾用の灰色ガラス板。 */
    public static ItemStack glass() {
        return guiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), null);
    }

    /** 防具のプレビュー用(元のItemMetaを保ったままGUI部品の印とLoreを追加)。 */
    public static ItemStack asGuiPreview(ItemStack base, List<Component> extraLore) {
        ItemStack copy = base.clone();
        copy.editMeta(meta -> {
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            for (Component c : extraLore) {
                lore.add(c.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            meta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
        });
        return copy;
    }

    /** インベントリへ入れ、入らなければ足元へドロップする。 */
    public static void giveOrDrop(Player player, ItemStack item) {
        if (isEmpty(item)) {
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(item.clone());
        for (ItemStack rest : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    // ---------------------------------------------------------------- 保存・読込 (Paper標準のバイト列シリアライズ)

    /** ItemStack保存: Paper標準の serializeAsBytes をBase64化(Armor Trim等のItemMetaを失わない)。 */
    public static String encode(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    /** ItemStack読込: Paper標準の deserializeBytes。 */
    public static ItemStack decode(String base64) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(base64));
    }

    private static ItemStack decodeSafe(String base64, Logger log) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            return clean(decode(base64));
        } catch (Exception e) {
            log.log(Level.WARNING, "アイテムの読み込みに失敗しました(このスロットは空になります)", e);
            return null;
        }
    }

    /** キット内容(36スロット・防具・オフハンド)をYAMLセクションへ書き込む。 */
    public static void writeContents(ConfigurationSection section, KitContents contents) {
        section.set("storage", null);
        ConfigurationSection storage = section.createSection("storage");
        for (int i = 0; i < KitContents.STORAGE_SIZE; i++) {
            ItemStack it = contents.getStorage(i);
            if (it != null) {
                storage.set(String.valueOf(i), encode(it));
            }
        }
        for (EquipSlot slot : EquipSlot.values()) {
            ItemStack it = contents.getEquip(slot);
            section.set(slot.configKey(), it == null ? null : encode(it));
        }
    }

    /** YAMLセクションからキット内容を読み込む。 */
    public static KitContents readContents(ConfigurationSection section, Logger log) {
        KitContents contents = new KitContents();
        ConfigurationSection storage = section.getConfigurationSection("storage");
        if (storage != null) {
            for (String key : storage.getKeys(false)) {
                int idx;
                try {
                    idx = Integer.parseInt(key);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (idx < 0 || idx >= KitContents.STORAGE_SIZE) {
                    continue;
                }
                contents.setStorage(idx, decodeSafe(storage.getString(key), log));
            }
        }
        for (EquipSlot slot : EquipSlot.values()) {
            contents.setEquip(slot, decodeSafe(section.getString(slot.configKey()), log));
        }
        return contents;
    }

    /** 使われていないimport警告避けのダミー参照。 */
    @SuppressWarnings("unused")
    private static void unused(ItemMeta meta) {
    }
}
