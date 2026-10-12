package com.gufont.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Page(
    val pageIndex: Int,                          // 1-based
    val srcName: String,                         // 原始页图文件名
    val correctedName: String? = null,           // 校正后图文件名
    val geometry: PageGeometry? = null,
    val columns: List<ColumnInfo> = emptyList(),
    val state: PageState = PageState.PENDING
)

@Serializable
data class PageGeometry(
    /** 4 个角点，原图归一化 0~1，顺序：左上、右上、右下、左下 */
    val corners: List<List<Float>> = emptyList(),
    /** 这一页实际用的列数 */
    val columnCount: Int = 12,
    /** 内部竖线 x 位置，校正坐标 0~1，升序，长度 = columnCount - 1 */
    val columnDividers: List<Float> = emptyList(),
    /** 左右重叠像素 */
    val columnOverlap: Int = 0
)

@Serializable
data class ColumnInfo(
    /** 1-based，从右往左 */
    val colIndex: Int,
    /** 这一列实际用的行数 */
    val rows: Int = 10,
    /** 行线 y 位置，列内归一化 0~1，升序，长度 = rows - 1 */
    val dividers: List<Float> = emptyList(),
    /** 上下重叠像素 */
    val rowOverlap: Int = 0,
    /** 是否被用户标记为丢弃 */
    val dropped: Boolean = false,
    val confirmed: Boolean = false
)

enum class PageState {
    PENDING,         // 未校正
    CORRECTED,       // 已校正
    COLUMNS_SET,     // 已分列
    SPLIT_DONE       // 已分割单字
}
