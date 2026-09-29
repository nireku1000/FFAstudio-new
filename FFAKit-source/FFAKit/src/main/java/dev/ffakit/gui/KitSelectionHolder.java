package dev.ffakit.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

/** キット選択GUI専用のHolder(タイトル文字列ではなくHolderで識別する)。 */
public final class KitSelectionHolder implements InventoryHolder {
    private final int page;
    private final Map<Integer, String> slotToKit = new HashMap<>();
    private boolean hasPrev;
    private boolean hasNext;
    private Inventory inventory;

    public KitSelectionHolder(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    public void mapKit(int slot, String kitId) {
        slotToKit.put(slot, kitId);
    }

    public String kitAt(int slot) {
        return slotToKit.get(slot);
    }

    public void setNav(boolean prev, boolean next) {
        this.hasPrev = prev;
        this.hasNext = next;
    }

    public boolean isPrevSlot(int slot) {
        return hasPrev && slot == KitSelectionGUI.PREV_SLOT;
    }

    public boolean isNextSlot(int slot) {
        return hasNext && slot == KitSelectionGUI.NEXT_SLOT;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
