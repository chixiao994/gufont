package com.gufont.app.engine.font

import com.gufont.app.engine.potrace.PotracePath
import com.gufont.app.engine.potrace.SegmentTag
import kotlin.math.roundToInt

/**
 * 把 Potrace 输出的路径转成字体坐标系中的 Glyph。
 *
 * Potrace 坐标：左上原点，Y 向下
 * 字体坐标：左下原点，Y 向上
 * 单位：缩放到 unitsPerEm（默认 1000）
 */
object GlyphConverter {

    fun convert(
        paths: List<PotracePath>,
        bitmapWidth: Int,
        bitmapHeight: Int,
        codepoint: Int,
        unitsPerEm: Int = 1000
    ): FontGlyph? {
        if (paths.isEmpty()) return null

        // 计算位图内容的包围盒
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
        for (p in paths) {
            for (pt in p.points) {
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
        }
        if (minX > maxX) return null

        val boxW = (maxX - minX).coerceAtLeast(1)
        val boxH = (maxY - minY).coerceAtLeast(1)

        // 留 5% 上下边距
        val targetH = unitsPerEm * 0.90
        val scale = targetH / boxH
        val advance = (boxW * scale + unitsPerEm * 0.10).roundToInt()
            .coerceAtLeast(1)

        val contours = ArrayList<List<GlyphPoint>>()
        for (p in paths) {
            val segs = if (p.optimizedSegments.isNotEmpty())
                p.optimizedSegments else p.curveSegments
            if (segs.isEmpty()) continue

            val pts = ArrayList<GlyphPoint>()
            for (seg in segs) {
                when (seg.tag) {
                    SegmentTag.CORNER -> {
                        val v = seg.c[1]
                        pts.add(GlyphPoint(
                            ((v.x - minX) * scale).toFloat(),
                            ((boxH - (v.y - minY)) * scale).toFloat(),
                            true
                        ))
                    }
                    SegmentTag.CURVETO -> {
                        val ctrl = seg.c[1]
                        val end = seg.c[2]
                        pts.add(GlyphPoint(
                            ((ctrl.x - minX) * scale).toFloat(),
                            ((boxH - (ctrl.y - minY)) * scale).toFloat(),
                            false
                        ))
                        pts.add(GlyphPoint(
                            ((end.x - minX) * scale).toFloat(),
                            ((boxH - (end.y - minY)) * scale).toFloat(),
                            true
                        ))
                    }
                }
            }
            if (pts.size >= 2) contours.add(pts)
        }
        if (contours.isEmpty()) return null

        return FontGlyph(codepoint, advance, contours)
    }
}
