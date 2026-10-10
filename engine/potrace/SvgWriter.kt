package com.gufont.app.engine.potrace

import java.util.Locale
import kotlin.math.abs

/**
 * 把 Potrace 结果写成 SVG 文件（可选，用于调试或导出）。
 * 使用 M / L / Q 命令（二次贝塞尔），与 TTF 兼容。
 */
object SvgWriter {

    fun toSvg(paths: List<PotracePath>, width: Int, height: Int): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
        sb.append("viewBox=\"0 0 $width $height\" width=\"$width\" height=\"$height\">\n")
        sb.append("<path fill=\"#000000\" fill-rule=\"evenodd\" d=\"")
        for (path in paths) {
            val segments = if (path.optimizedSegments.isNotEmpty())
                path.optimizedSegments else path.curveSegments
            if (segments.isEmpty()) continue
            appendPath(sb, segments)
        }
        sb.append("\"/>\n</svg>\n")
        return sb.toString()
    }

    private fun appendPath(sb: StringBuilder, segments: List<CurveSegment>) {
        val first = segments.firstOrNull() ?: return
        val start = when (first.tag) {
            SegmentTag.CORNER -> first.c[1]
            SegmentTag.CURVETO -> first.c[0]
        }
        sb.append('M').append(f(start.x)).append(' ').append(f(start.y))
        for (seg in segments) {
            when (seg.tag) {
                SegmentTag.CORNER -> {
                    val v = seg.c[1]
                    sb.append('L').append(f(v.x)).append(' ').append(f(v.y))
                }
                SegmentTag.CURVETO -> {
                    val ctrl = seg.c[1]
                    val end = seg.c[2]
                    sb.append('Q').append(f(ctrl.x)).append(' ').append(f(ctrl.y))
                        .append(' ').append(f(end.x)).append(' ').append(f(end.y))
                }
            }
        }
        sb.append('Z')
    }

    private fun f(v: Double): String {
        val i = v.toInt()
        return if (abs(i.toDouble() - v) < 1e-6) i.toString()
        else String.format(Locale.US, "%.1f", v)
    }
}
