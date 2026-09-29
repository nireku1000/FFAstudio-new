package dev.ffakit.command;

import dev.ffakit.FFAKitPlugin;
import dev.ffakit.kit.Kit;
import dev.ffakit.kit.KitContents;
import dev.ffakit.util.ItemStackUtil;
import dev.ffakit.util.Msg;
import dev.ffakit.util.PermissionUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;

/** /ffakit book | setbase | delete | reset (すべてOPレベル4以上)。 */
public final class FFAKitCommand implements TabExecutor {
    private final FFAKitPlugin plugin;

    public FFAKitCommand(FFAKitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof BlockCommandSender) {
            Msg.error(sender, "このコマンドはコマンドブロックからは実行できません。");
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "book" -> book(sender);
            case "setbase" -> setBase(sender, args);
            case "delete" -> delete(sender, args);
            case "reset" -> reset(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender sender) {
        Msg.error(sender, "使い方: /ffakit book | /ffakit setbase <キット名> | /ffakit delete <キット名> | /ffakit reset <プレイヤー> <キット名>");
    }

    private boolean checkLevel4(CommandSender sender) {
        if (!PermissionUtil.hasLevel4(sender)) {
            Msg.error(sender, "このコマンドを使用するには、OPレベル4以上が必要です。");
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- /ffakit book

    private void book(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            Msg.error(sender, "このコマンドはプレイヤーのみ実行できます。");
            return;
        }
        if (!checkLevel4(sender)) {
            return;
        }
        ItemStackUtil.giveOrDrop(player, ItemStackUtil.createEditorBook());
        Msg.info(player, "キット編集本を渡しました。左クリックでキット選択GUIを開きます。");
    }

    // ---------------------------------------------------------------- /ffakit setbase <kitName>

    private void setBase(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Msg.error(sender, "このコマンドはプレイヤーのみ実行できます。");
            return;
        }
        if (!checkLevel4(sender)) {
            return;
        }
        if (args.length < 2) {
            Msg.error(sender, "引数が足りません。使い方: /ffakit setbase <キット名>");
            return;
        }
        String name = args[1];
        String error = plugin.kits().validateName(name);
        if (error != null) {
            Msg.error(sender, error);
            return;
        }
        if (plugin.editGui().isEditing(player)) {
            Msg.error(sender, "キット編集中は使用できません。");
            return;
        }
        // 現在のインベントリ・装備を基本キットとして保存(キット編集本などの特殊アイテムは除く)
        KitContents contents = KitContents.fromPlayer(player);
        contents.removeSpecialItems();
        try {
            boolean created = plugin.kits().setBase(name, contents);
            Kit kit = plugin.kits().getKit(name);
            String shown = kit != null ? kit.getDisplayName() : name;
            Msg.info(sender, created
                    ? "キット「" + shown + "」を新規作成しました。"
                    : "キット「" + shown + "」の基本キットを上書きしました。(個人カスタムは変更されません)");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "基本キットの保存に失敗しました", e);
            Msg.error(sender, "保存に失敗しました。詳細はコンソールを確認してください。");
        }
    }

    // ---------------------------------------------------------------- /ffakit delete <kitName>

    private void delete(CommandSender sender, String[] args) {
        if (!checkLevel4(sender)) {
            return;
        }
        if (args.length < 2) {
            Msg.error(sender, "引数が足りません。使い方: /ffakit delete <キット名>");
            return;
        }
        Kit kit = plugin.kits().getKit(args[1]);
        if (kit == null) {
            Msg.error(sender, "削除対象のキットが存在しません: " + args[1]);
            return;
        }
        // このキットを編集中のプレイヤーは先に安全に終了させる
        plugin.editGui().abortForKit(kit.getId());
        try {
            int customs = plugin.kits().delete(kit.getDisplayName());
            Msg.info(sender, "キット「" + kit.getDisplayName() + "」を完全に削除しました(個人カスタム " + customs + " 件も削除)。");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "キットの削除に失敗しました", e);
            Msg.error(sender, "削除に失敗しました(保存エラー)。詳細はコンソールを確認してください。");
        }
    }

    // ---------------------------------------------------------------- /ffakit reset <player> <kitName>

    private void reset(CommandSender sender, String[] args) {
        if (!checkLevel4(sender)) {
            return;
        }
        if (args.length < 3) {
            Msg.error(sender, "引数が足りません。使い方: /ffakit reset <プレイヤー> <キット名>");
            return;
        }
        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            Msg.error(sender, "プレイヤーが存在しません: " + args[1]);
            return;
        }
        Kit kit = plugin.kits().getKit(args[2]);
        if (kit == null) {
            Msg.error(sender, "キットが存在しません: " + args[2]);
            return;
        }
        UUID uuid = target.getUniqueId();
        plugin.editGui().abortFor(uuid, kit.getId());
        try {
            // 指定UUIDの指定キットの個人カスタムだけ削除(他キット・キット本体は変更しない)
            boolean removed = plugin.playerKits().remove(uuid, kit.getId());
            String who = target.getName() != null ? target.getName() : uuid.toString();
            if (removed) {
                Msg.info(sender, who + " のキット「" + kit.getDisplayName() + "」の個人カスタムを削除しました。次回から基本キットを使用します。");
            } else {
                Msg.error(sender, who + " のキット「" + kit.getDisplayName() + "」には個人カスタムがありません。");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "個人カスタムの削除に失敗しました", e);
            Msg.error(sender, "削除に失敗しました。詳細はコンソールを確認してください。");
        }
    }

    private OfflinePlayer findPlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(name));
        } catch (IllegalArgumentException ignored) {
            // UUIDではない → 名前として検索
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    // ---------------------------------------------------------------- Tab補完

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (!PermissionUtil.hasLevel4(sender)) {
            return result;
        }
        if (args.length == 1) {
            for (String s : List.of("book", "setbase", "delete", "reset")) {
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(s);
                }
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("setbase") || sub.equals("delete")) {
                addKitNames(result, args[1]);
            } else if (sub.equals("reset")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                        result.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            addKitNames(result, args[2]);
        }
        return result;
    }

    private void addKitNames(List<String> out, String prefix) {
        for (String n : plugin.kits().getDisplayNames()) {
            if (n.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                out.add(n);
            }
        }
    }
}
