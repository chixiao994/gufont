package com.gufont.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Glyph(
    /** 唯一编码：LZWBS_p003_c02_r08，永不变 */
    val code: String,
    val page: Int,
    val col: Int,
    val row: Int,
    /** 用户命名的 Unicode 码点；0 = 未命名 */
    val codepoint: Int = 0,
    val state: GlyphState = GlyphState.EXTRACTED,
    /** 用户是否手动改过位图 */
    val manualEdited: Boolean = false,
    /** 位图文件名 */
    val raw: String,
    val edited: String? = null,
    /** SVG 文件名 */
    val svg: String? = null,
    /** 字位级二值化参数覆盖，null = 用全局 */
    val binaryOverride: BinarySettings? = null,
    /** 失败原因（矢量化失败 / 重码） */
    val issue: String? = null,
    val updated: String = ""
)

enum class GlyphState {
    EXTRACTED,     // 刚切出
    EDITED,        // 用户编辑过
    VECTORIZED,    // 已矢量化
    NAMED,         // 已命名
    IN_FONT,       // 已入库
    SKIPPED        // 用户抛弃（可重新启用）
}

@Serializable
data class GlyphIndex(
    val glyphs: List<Glyph> = emptyList()
)

fun buildGlyphCode(
    bookAbbr: String,
    annotatorAbbr: String,
    editionAbbr: String,
    page: Int,
    col: Int,
    row: Int
): String {
    val prefix = "$bookAbbr$annotatorAbbr$editionAbbr"
    val p = page.toString().padStart(3, '0')
    val c = col.toString().padStart(2, '0')
    val r = row.toString().padStart(2, '0')
    return "${prefix}_p${p}_c${c}_r${r}"
}
