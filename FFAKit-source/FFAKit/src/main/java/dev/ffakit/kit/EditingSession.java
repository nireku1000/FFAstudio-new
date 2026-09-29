package dev.ffakit.kit;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * 1人分のキット編集セッション。
 * 「基本キット」「個人カスタム」「現在のPlayerInventory」「編集中の一時データ」を混同しないよう、
 * それぞれ別フィールドで保持する。
 */
public final class EditingSession {
    public enum State {
        /** キット編集GUIを開いている */
        EDIT_GUI,
        /** Armor Trim GUIを開いている */
        TRIM_GUI
    }

    /** Armor Trim編集対象の場所。inTop=true なら編集GUI上部のスロット、false ならプレイヤーインベントリのスロット。 */
    public record TrimTarget(boolean inTop, int slot) {
    }

    public final UUID playerId;
    /** 編集対象キット */
    public final Kit kit;
    /** 元のPlayerInventory・防具・オフハンド(ディープコピー) */
    public final KitContents original;
    /** 元のカーソルアイテム(ディープコピー) */
    public final ItemStack originalCursor;
    /** 編集開始時にロードした内容(未変更判定用) */
    public final KitContents loaded;
    /** 編集開始時に個人カスタムが既にあったか */
    public final boolean hadCustom;

    public State state = State.EDIT_GUI;
    public Inventory editInventory;
    public Inventory trimInventory;
    public TrimTarget trimTarget;
    /** キャンセルボタンが押された */
    public boolean cancelled;
    /** GUI切替中(閉じるイベントを無視するためのフラグ) */
    public boolean transitioning;
    /** セッション終了済み(二重処理防止) */
    public boolean ended;

    public EditingSession(UUID playerId, Kit kit, KitContents original, ItemStack originalCursor,
                          KitContents loaded, boolean hadCustom) {
        this.playerId = playerId;
        this.kit = kit;
        this.original = original;
        this.originalCursor = originalCursor;
        this.loaded = loaded;
        this.hadCustom = hadCustom;
    }
}
