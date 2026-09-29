package dev.ffakit.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** メッセージ表示用ユーティリティ。 */
public final class Msg {
    private static final Component PREFIX = Component.text("[FFAKit] ", NamedTextColor.GOLD);

    private Msg() {
    }

    public static void error(Audience to, String message) {
        to.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.RED)));
    }

    public static void info(Audience to, String message) {
        to.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GREEN)));
    }
}
