package dev.ffakit.command;

import dev.ffakit.FFAKitPlugin;
import dev.ffakit.kit.Kit;
import dev.ffakit.kit.KitContents;
import dev.ffakit.util.Msg;
import dev.ffakit.util.SelectorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * /kit give &lt;プレイヤー名|@p|@a&gt; &lt;キット名&gt;
 * プレイヤー・コンソール・コマンドブロック・他プラグインから実行可能。
 */
public final class KitCommand implements TabExecutor {
    private final FFAKitPlugin plugin;

    public KitCommand(FFAKitPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            Msg.error(sender, "使い方: /kit give <プレイヤー名|@p|@a> <キット名>");
            return true;
        }
        if (args.length < 3) {
            Msg.error(sender, "引数が足りません。使い方: /kit give <プレイヤー名|@p|@a> <キット名>");
            return true;
        }
        Kit kit = plugin.kits().getKit(args[2]);
        if (kit == null) {
            Msg.error(sender, "キットが存在しません: " + args[2]);
            return true;
        }
        if (kit.getBase() == null) {
            Msg.error(sender, "基本キットが存在しません: " + kit.getDisplayName());
            return true;
        }

        List<Player> targets;
        try {
            // バニラのセレクター機能(@p / @a)を利用。距離計算などは自前実装しない。
            targets = SelectorUtil.resolvePlayers(sender, args[1]);
        } catch (SelectorUtil.SelectorException e) {
            Msg.error(sender, e.getMessage());
            return true;
        }

        int given = 0;
        for (Player target : targets) {
            if (plugin.editGui().isEditing(target)) {
                Msg.error(sender, target.getName() + " はキット編集中のためスキップしました。");
                continue;
            }
            // 個人カスタムあり → 個人カスタムを使用 / なし → 基本キットを使用
            KitContents custom = plugin.playerKits().get(target.getUniqueId(), kit.getId());
            KitContents contents = custom != null ? custom : kit.getBase();
            // 通常インベントリ・防具・オフハンドを全消去してから置き換える(混ざらない・ドロップしない)
            contents.deepCopy().applyTo(target, true);
            if (!target.equals(sender)) {
                Msg.info(target, "キット「" + kit.getDisplayName() + "」が配布されました。");
            }
            given++;
        }
        Msg.info(sender, "キット「" + kit.getDisplayName() + "」を " + given + " 人に配布しました。");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            if ("give".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                result.add("give");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (String s : List.of("@p", "@a")) {
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(s);
                }
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    result.add(p.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (String n : plugin.kits().getDisplayNames()) {
                if (n.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))) {
                    result.add(n);
                }
            }
        }
        return result;
    }
}
