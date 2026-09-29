package dev.ffakit.gui;

import dev.ffakit.FFAKitPlugin;
import dev.ffakit.kit.Kit;
import dev.ffakit.util.ItemStackUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * キット選択GUI(54スロット)。
 * キット数が54以下なら全54スロットに登録順で左上から配置。
 * 55個以上ならページング(1ページ45個、下段45=前へ / 49=ページ表示 / 53=次へ)。
 */
public final class KitSelectionGUI {
    public static final int SIZE = 54;
    public static final int PREV_SLOT = 45;
    public static final int INDICATOR_SLOT = 49;
    public static final int NEXT_SLOT = 53;

    private final FFAKitPlugin plugin;

    public KitSelectionGUI(FFAKitPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int requestedPage) {
        List<Kit> kits = plugin.kits().getAll();
        boolean paging = kits.size() > SIZE;
        int perPage = paging ? 45 : SIZE;
        int pages = Math.max(1, (kits.size() + perPage - 1) / perPage);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        KitSelectionHolder holder = new KitSelectionHolder(page);
        String title = "キット選択" + (paging ? " (" + (page + 1) + "/" + pages + ")" : "");
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text(title));
        holder.setInventory(inv);

        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < kits.size(); i++) {
            Kit kit = kits.get(start + i);
            Material icon = plugin.kits().resolveIcon(kit);
            ItemStack item = ItemStackUtil.guiItem(icon,
                    Component.text(kit.getDisplayName(), NamedTextColor.WHITE),
                    List.of(Component.text("クリックで編集", NamedTextColor.GRAY)));
            inv.setItem(i, item);
            holder.mapKit(i, kit.getId());
        }

        if (paging) {
            for (int s = 45; s < SIZE; s++) {
                inv.setItem(s, ItemStackUtil.glass());
            }
            boolean prev = page > 0;
            boolean next = page < pages - 1;
            holder.setNav(prev, next);
            if (prev) {
                inv.setItem(PREV_SLOT, ItemStackUtil.guiItem(Material.ARROW,
                        Component.text("前のページ", NamedTextColor.YELLOW), null));
            }
            if (next) {
                inv.setItem(NEXT_SLOT, ItemStackUtil.guiItem(Material.ARROW,
                        Component.text("次のページ", NamedTextColor.YELLOW), null));
            }
            inv.setItem(INDICATOR_SLOT, ItemStackUtil.guiItem(Material.PAPER,
                    Component.text((page + 1) + " / " + pages, NamedTextColor.WHITE), null));
        }
        player.openInventory(inv);
    }
}
