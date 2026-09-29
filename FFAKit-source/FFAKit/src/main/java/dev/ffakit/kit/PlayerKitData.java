package dev.ffakit.kit;

import java.util.Map;
import java.util.UUID;

/** 1プレイヤー分の個人カスタム(キットid → 中身)。基本キットとは完全に別データ。 */
public record PlayerKitData(UUID playerId, Map<String, KitContents> customs) {
}
