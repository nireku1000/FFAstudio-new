package dev.ffakit.kit;

import org.bukkit.inventory.ItemStack;

/** 編集GUI上部の防具・オフハンド枠。guiSlot は編集GUI(54スロット)内のスロット番号。 */
public enum EquipSlot {
    HELMET(10, "ヘルメット"),
    CHESTPLATE(11, "チェストプレート"),
    LEGGINGS(12, "レギンス"),
    BOOTS(13, "ブーツ"),
    OFFHAND(15, "オフハンド");

    private final int guiSlot;
    private final String label;

    EquipSlot(int guiSlot, String label) {
        this.guiSlot = guiSlot;
        this.label = label;
    }

    public int guiSlot() {
        return guiSlot;
    }

    public String label() {
        return label;
    }

    public String configKey() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public static EquipSlot fromGuiSlot(int slot) {
        for (EquipSlot s : values()) {
            if (s.guiSlot == slot) {
                return s;
            }
        }
        return null;
    }

    /** このスロットに置けるアイテムか(オフハンドは何でも可)。 */
    public boolean accepts(ItemStack item) {
        String n = item.getType().name();
        return switch (this) {
            case HELMET -> n.endsWith("_HELMET") || n.equals("CARVED_PUMPKIN")
                    || n.endsWith("_HEAD") || n.endsWith("_SKULL");
            case CHESTPLATE -> n.endsWith("_CHESTPLATE") || n.equals("ELYTRA");
            case LEGGINGS -> n.endsWith("_LEGGINGS");
            case BOOTS -> n.endsWith("_BOOTS");
            case OFFHAND -> true;
        };
    }
}
