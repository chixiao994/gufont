package com.gufont.app.engine.image

import android.graphics.Bitmap
import com.gufont.app.data.model.Page

/**
 * 从校正后的整页图里裁出：
 *   - 单列窄条（带左右重叠）
 *   - 单行字块（带上下重叠）
 */
object GlyphCutter {

    /**
     * 裁出当前列。
     * @param colFromLeft 从左往右 0-based 的列索引
     * @param hOverlap 左右重叠像素
     */
    fun cropColumn(
        corrected: Bitmap,
        page: Page,
        colFromLeft: Int,
        hOverlap: Int
    ): Bitmap {
        val geo = page.geometry ?: error("页面几何未设置")
        val W = corrected.width
        val H = corrected.height
        val n = geo.columnCount
        if (n <= 0) error("列数无效")

        val dividers = geo.columnDividers
        val left = if (colFromLeft == 0) 0f else dividers.getOrNull(colFromLeft - 1) ?: 0f
        val right = if (colFromLeft == n - 1) 1f else dividers.getOrNull(colFromLeft) ?: 1f

        var x0 = (left * W).toInt().coerceIn(0, W - 1)
        var x1 = (right * W).toInt().coerceIn(1, W)
        val half = hOverlap / 2
        x0 = (x0 - half).coerceAtLeast(0)
        x1 = (x1 + half).coerceAtMost(W)
        if (x1 <= x0) x1 = (x0 + 1).coerceAtMost(W)

        return Bitmap.createBitmap(corrected, x0, 0, x1 - x0, H)
    }

    /**
     * 从列窄条裁出第 rowIdx 个字块。
     * @param dividers 列内行线（归一化 0~1 升序）
     * @param rows 该列的行数
     * @param vOverlap 上下重叠像素
     */
    fun cropRow(
        column: Bitmap,
        dividers: List<Float>,
        rowIdx: Int,
        rows: Int,
        vOverlap: Int
    ): Bitmap {
        val W = column.width
        val H = column.height
        if (rows <= 0) error("行数无效")

        val y0f = if (rowIdx == 0) 0f else dividers.getOrNull(rowIdx - 1) ?: 0f
        val y1f = if (rowIdx == rows - 1) 1f else dividers.getOrNull(rowIdx) ?: 1f

        var y0 = (y0f * H).toInt().coerceIn(0, H - 1)
        var y1 = (y1f * H).toInt().coerceIn(1, H)
        val half = vOverlap / 2
        y0 = (y0 - half).coerceAtLeast(0)
        y1 = (y1 + half).coerceAtMost(H)
        if (y1 <= y0) y1 = (y0 + 1).coerceAtMost(H)

        return Bitmap.createBitmap(column, 0, y0, W, y1 - y0)
    }
}
