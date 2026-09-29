package dev.ffakit.kit;

import org.bukkit.Material;

/** 登録されたキット。id は小文字化した内部名、displayName は最初に登録した表示名。 */
public final class Kit {
    private final String id;
    private final String displayName;
    private Material icon;
    private KitContents base;

    public Kit(String id, String displayName, Material icon, KitContents base) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.base = base;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public KitContents getBase() {
        return base;
    }

    public void setBase(KitContents base) {
        this.base = base;
    }
}
