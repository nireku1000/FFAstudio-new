package dev.ffakit.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * OPレベル4以上の判定。
 *
 * Paper 26.2 の公開APIにはOPレベルを直接取得する方法を確認できなかったため、
 * サーバー直下の ops.json の "level" を読んで判定する(config.yml の ops-json-check で切替可能)。
 */
public final class PermissionUtil {
    private static Plugin plugin;
    private static long cachedModified = -1;
    private static final Map<UUID, Integer> LEVELS = new HashMap<>();
    private static boolean warned = false;

    private PermissionUtil() {
    }

    public static void init(Plugin p) {
        plugin = p;
    }

    /** 送信者がOPレベル4以上か。コンソールは常にtrue。コマンドブロック等はfalse。 */
    public static boolean hasLevel4(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
            return true;
        }
        if (sender instanceof Player player) {
            return isLevel4(player);
        }
        return false;
    }

    public static boolean isLevel4(Player player) {
        if (!player.isOp()) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("ops-json-check", true)) {
            return true;
        }
        return getOpLevel(player.getUniqueId()) >= 4;
    }

    /** ops.json から指定UUIDのlevelを取得。無い/読めない場合は -1。 */
    private static synchronized int getOpLevel(UUID uuid) {
        Path path = Path.of("ops.json").toAbsolutePath();
        try {
            if (!Files.exists(path)) {
                return -1;
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (modified != cachedModified) {
                reload(path);
                cachedModified = modified;
            }
            return LEVELS.getOrDefault(uuid, -1);
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                plugin.getLogger().log(Level.WARNING,
                        "ops.json を読み込めませんでした。OPレベル判定は拒否になります。"
                                + " config.yml の ops-json-check を false にすると isOp() のみで判定します。", e);
            }
            return -1;
        }
    }

    private static void reload(Path path) throws IOException {
        LEVELS.clear();
        String text = Files.readString(path, StandardCharsets.UTF_8);
        JsonElement root = JsonParser.parseString(text);
        if (!root.isJsonArray()) {
            return;
        }
        JsonArray array = root.getAsJsonArray();
        for (JsonElement el : array) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            if (!obj.has("uuid") || !obj.has("level")) {
                continue;
            }
            try {
                LEVELS.put(UUID.fromString(obj.get("uuid").getAsString()), obj.get("level").getAsInt());
            } catch (IllegalArgumentException ignored) {
                // 不正な行は無視
            }
        }
    }
}
