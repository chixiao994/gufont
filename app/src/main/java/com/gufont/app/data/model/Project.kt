package com.gufont.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val schemaVersion: Int = 1,
    val created: String = "",
    val updated: String = "",
    val book: BookInfo = BookInfo(),
    val settings: FontSettings = FontSettings(),
    val layout: LayoutSettings = LayoutSettings(),
    val steps: Map<Int, StepState> = defaultSteps(),
    val lastStep: Int = Step.IMPORT.id
) {
    companion object {
        fun defaultSteps(): Map<Int, StepState> = Step.values()
            .associate { it.id to StepState() }
    }
}

@Serializable
data class BookInfo(
    val title: String = "",
    val annotator: String = "",
    val edition: String = "",
    val bookAbbr: String = "",
    val annotatorAbbr: String = "",
    val editionAbbr: String = ""
) {
    fun codePrefix(): String = "$bookAbbr$annotatorAbbr$editionAbbr"
}

@Serializable
data class FontSettings(
    val unitsPerEm: Int = 1000,
    val fontFamily: String = "GuFont",
    // 全局二值化默认参数（进入编辑时弹窗确认，之后可改）
    val binaryMode: String = "otsu",
    val binaryThreshold: Int = 128,
    val binaryInvert: Boolean = false,
    // Potrace 参数
    val potraceTurdSize: Int = 8,
    val potraceAlphaMax: Double = 1.0,
    val potraceOptTolerance: Double = 0.2
) {
    fun toBinarySettings() = BinarySettings(
        mode = binaryMode,
        threshold = binaryThreshold,
        invert = binaryInvert
    )
}

@Serializable
data class LayoutSettings(
    // 分列：全局 + 当前页
    val globalColumnCount: Int = 12,
    val globalColumnOverlap: Int = 0,
    // 分行：全局 + 当前列
    val globalRowCount: Int = 10,
    val globalRowOverlap: Int = 0
)

@Serializable
data class BinarySettings(
    val mode: String = "otsu",      // otsu / fixed / adaptive
    val threshold: Int = 128,
    val invert: Boolean = false
)
