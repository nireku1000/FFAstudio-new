package dev.ffakit.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** ファイル保存用ユーティリティ。 */
public final class FileUtil {
    private FileUtil() {
    }

    /**
     * 安全な保存: 一時ファイルへ書き込み → 成功したら本ファイルへ置換。
     * 書き込み中にサーバーが止まっても、本ファイルは壊れない。
     */
    public static void atomicWrite(File target, String content) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("フォルダを作成できません: " + parent);
        }
        File tmp = new File(parent, target.getName() + ".tmp");
        Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
