package dev.ffakit.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /kit give の対象解決。@p / @a などはPaper(バニラ)のセレクター機能に任せる。 */
public final class SelectorUtil {
    private SelectorUtil() {
    }

    public static final class SelectorException extends Exception {
        public SelectorException(String message) {
            super(message);
        }
    }

    public static List<Player> resolvePlayers(CommandSender sender, String arg) throws SelectorException {
        if (arg.startsWith("@")) {
            List<Entity> entities;
            try {
                // バニラのセレクター解決 (@p は実行者の位置から最も近いプレイヤー)
                entities = Bukkit.selectEntities(sender, arg);
            } catch (IllegalArgumentException e) {
                throw new SelectorException("セレクターを解決できませんでした: " + arg);
            }
            List<Player> players = new ArrayList<>();
            for (Entity entity : entities) {
                if (entity instanceof Player p) {
                    players.add(p);
                }
            }
            if (players.isEmpty()) {
                if (arg.startsWith("@p")) {
                    throw new SelectorException("@p を解決できませんでした。実行者に位置情報が無いか、近くにプレイヤーがいません。");
                }
                throw new SelectorException("セレクターに一致するプレイヤーがいません: " + arg);
            }
            return players;
        }
        Player player = Bukkit.getPlayerExact(arg);
        if (player == null) {
            throw new SelectorException("プレイヤーが見つかりません(オンラインのみ指定できます): " + arg);
        }
        return List.of(player);
    }
}
