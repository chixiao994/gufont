package com.gufont.app.data.model

enum class Step(val id: Int, val label: String) {
    IMPORT(0, "导入 PDF"),
    CORRECT(1, "校正 + 分列"),
    SPLIT(2, "分割单字"),
    EDIT(3, "编辑"),
    INSTALL(4, "安装字体");

    companion object {
        fun fromId(id: Int): Step? = values().firstOrNull { it.id == id }
    }
}

@kotlinx.serialization.Serializable
data class StepState(
    /** 每步骤的完成状态；未完成的步骤不可直达 */
    val completed: Boolean = false,
    /** 该步骤已处理的进度数（页/列/字） */
    val done: Int = 0,
    /** 该步骤的总数 */
    val total: Int = 0
)
