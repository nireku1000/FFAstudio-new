package dev.ffakit.gui;

import dev.ffakit.kit.EditingSession;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.List;

/** Armor Trim GUI専用のHolder。選択状態(なし = -1)もここで管理する。 */
public final class ArmorTrimHolder implements InventoryHolder {
    private final EditingSession session;
    private final ItemStack armor;
    private final List<TrimMaterial> materials;
    private final List<NamespacedKey> materialKeys;
    private final List<TrimPattern> patterns;
    private final List<NamespacedKey> patternKeys;
    /** -1 = 「なし」(TrimMaterialではなく内部状態NONE) */
    private int materialIndex;
    /** -1 = 「なし」(TrimPatternではなく内部状態NONE) */
    private int patternIndex;
    private Inventory inventory;

    public ArmorTrimHolder(EditingSession session, ItemStack armor,
                           List<TrimMaterial> materials, List<NamespacedKey> materialKeys,
                           List<TrimPattern> patterns, List<NamespacedKey> patternKeys,
                           int materialIndex, int patternIndex) {
        this.session = session;
        this.armor = armor;
        this.materials = materials;
        this.materialKeys = materialKeys;
        this.patterns = patterns;
        this.patternKeys = patternKeys;
        this.materialIndex = materialIndex;
        this.patternIndex = patternIndex;
    }

    public EditingSession session() {
        return session;
    }

    public ItemStack armor() {
        return armor;
    }

    public int materialCount() {
        return materials.size();
    }

    public int patternCount() {
        return patterns.size();
    }

    public int materialIndex() {
        return materialIndex;
    }

    public int patternIndex() {
        return patternIndex;
    }

    public void setMaterialIndex(int i) {
        this.materialIndex = i;
    }

    public void setPatternIndex(int i) {
        this.patternIndex = i;
    }

    public NamespacedKey currentMaterialKey() {
        return materialIndex < 0 ? null : materialKeys.get(materialIndex);
    }

    public NamespacedKey currentPatternKey() {
        return patternIndex < 0 ? null : patternKeys.get(patternIndex);
    }

    /** 素材・模様の両方が選ばれているときだけTrimを返す。どちらかが「なし」なら null(=Trimなし)。 */
    public ArmorTrim currentTrim() {
        if (materialIndex < 0 || patternIndex < 0) {
            return null;
        }
        return new ArmorTrim(materials.get(materialIndex), patterns.get(patternIndex));
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
