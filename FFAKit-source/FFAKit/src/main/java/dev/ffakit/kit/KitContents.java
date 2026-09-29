package dev.ffakit.kit;

import dev.ffakit.util.ItemStackUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.EnumMap;
import java.util.Objects;

/**
 * キットの中身(通常インベントリ36スロット + 防具4種 + オフハンド)。
 * 基本キット・個人カスタム・退避バックアップ・編集中データで共通に使う値クラス。
 * 内部では常にItemStackを複製して保持する(元のItemStack参照を破壊しない)。
 */
public final class KitContents {
    public static final int STORAGE_SIZE = 36;

    private final ItemStack[] storage = new ItemStack[STORAGE_SIZE];
    private final EnumMap<EquipSlot, ItemStack> equip = new EnumMap<>(EquipSlot.class);

    public ItemStack getStorage(int index) {
        return storage[index];
    }

    public void setStorage(int index, ItemStack item) {
        storage[index] = ItemStackUtil.clean(item);
    }

    public ItemStack getEquip(EquipSlot slot) {
        return equip.get(slot);
    }

    public void setEquip(EquipSlot slot, ItemStack item) {
        ItemStack c = ItemStackUtil.clean(item);
        if (c == null) {
            equip.remove(slot);
        } else {
            equip.put(slot, c);
        }
    }

    /** プレイヤーの現在の所持品・装備からディープコピーで作る。 */
    public static KitContents fromPlayer(Player player) {
        PlayerInventory inv = player.getInventory();
        KitContents c = new KitContents();
        ItemStack[] st = inv.getStorageContents();
        for (int i = 0; i < STORAGE_SIZE && i < st.length; i++) {
            c.storage[i] = ItemStackUtil.clean(st[i]);
        }
        c.setEquip(EquipSlot.HELMET, inv.getHelmet());
        c.setEquip(EquipSlot.CHESTPLATE, inv.getChestplate());
        c.setEquip(EquipSlot.LEGGINGS, inv.getLeggings());
        c.setEquip(EquipSlot.BOOTS, inv.getBoots());
        c.setEquip(EquipSlot.OFFHAND, inv.getItemInOffHand());
        return c;
    }

    /**
     * プレイヤーの所持品を、このキット内容に「置き換える」。
     * 先に全消去してから設定するので既存アイテムと混ざらず、地面へのドロップも起きない。
     *
     * @param withEquipment true: 防具・オフハンドも置き換え / false: 防具・オフハンドは空にする
     */
    public void applyTo(Player player, boolean withEquipment) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] arr = new ItemStack[STORAGE_SIZE];
        for (int i = 0; i < STORAGE_SIZE; i++) {
            arr[i] = ItemStackUtil.cloneOrNull(storage[i]);
        }
        inv.clear();
        inv.setStorageContents(arr);
        inv.setHelmet(withEquipment ? ItemStackUtil.cloneOrNull(equip.get(EquipSlot.HELMET)) : null);
        inv.setChestplate(withEquipment ? ItemStackUtil.cloneOrNull(equip.get(EquipSlot.CHESTPLATE)) : null);
        inv.setLeggings(withEquipment ? ItemStackUtil.cloneOrNull(equip.get(EquipSlot.LEGGINGS)) : null);
        inv.setBoots(withEquipment ? ItemStackUtil.cloneOrNull(equip.get(EquipSlot.BOOTS)) : null);
        ItemStack off = withEquipment ? ItemStackUtil.cloneOrNull(equip.get(EquipSlot.OFFHAND)) : null;
        inv.setItemInOffHand(off == null ? new ItemStack(Material.AIR) : off);
    }

    public KitContents deepCopy() {
        KitContents c = new KitContents();
        for (int i = 0; i < STORAGE_SIZE; i++) {
            c.storage[i] = ItemStackUtil.cloneOrNull(storage[i]);
        }
        for (var e : equip.entrySet()) {
            c.equip.put(e.getKey(), e.getValue().clone());
        }
        return c;
    }

    /** キット編集本・GUI部品をキットに保存しないよう取り除く。 */
    public void removeSpecialItems() {
        for (int i = 0; i < STORAGE_SIZE; i++) {
            if (ItemStackUtil.isEditorBook(storage[i]) || ItemStackUtil.isGuiItem(storage[i])) {
                storage[i] = null;
            }
        }
        equip.values().removeIf(it -> ItemStackUtil.isEditorBook(it) || ItemStackUtil.isGuiItem(it));
    }

    /** 中身が同じか(未変更判定用)。 */
    public boolean sameAs(KitContents other) {
        for (int i = 0; i < STORAGE_SIZE; i++) {
            if (!Objects.equals(storage[i], other.storage[i])) {
                return false;
            }
        }
        for (EquipSlot s : EquipSlot.values()) {
            if (!Objects.equals(equip.get(s), other.equip.get(s))) {
                return false;
            }
        }
        return true;
    }
}
