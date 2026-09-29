package dev.ffakit.gui;

import dev.ffakit.kit.EditingSession;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** キット編集GUI専用のHolder。 */
public final class KitEditHolder implements InventoryHolder {
    private final EditingSession session;
    private Inventory inventory;

    public KitEditHolder(EditingSession session) {
        this.session = session;
    }

    public EditingSession session() {
        return session;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
