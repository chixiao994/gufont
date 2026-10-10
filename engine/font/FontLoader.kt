package com.gufont.app.engine.font

import android.graphics.Typeface
import java.io.File

object FontLoader {

    /** 从 TTF 文件创建 Typeface */
    fun fromFile(file: File): Typeface? = try {
        if (!file.exists() || file.length() < 12) null
        else Typeface.createFromFile(file)
    } catch (_: Exception) { null }

    /** 检查 TTF 文件头 */
    fun isFontFile(file: File): Boolean {
        if (!file.exists() || file.length() < 12) return false
        return try {
            val head = ByteArray(4)
            file.inputStream().use { it.read(head) }
            val tag = String(head, Charsets.US_ASCII)
            val isTtf = head[0].toInt() == 0x00 && head[1].toInt() == 0x01 &&
                    head[2].toInt() == 0x00 && head[3].toInt() == 0x00
            isTtf || tag == "OTTO" || tag == "true" || tag == "ttcf"
        } catch (_: Exception) { false }
    }
}
