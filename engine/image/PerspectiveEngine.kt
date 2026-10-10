package com.gufont.app.engine.image

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * 四点透视校正（从工具 2 移植，纯 Kotlin，无 OpenCV）。
 * 输入：源位图 + 4 个归一化角点（顺序：左上、右上、右下、左下）
 * 输出：校正后的位图，尺寸为四边形上下边/左右边长度的较大者
 */
object PerspectiveEngine {

    fun correct(src: Bitmap, normalizedCorners: List<Offset>): Bitmap {
        require(normalizedCorners.size == 4) { "需要 4 个角点" }

        val pixelPts = normalizedCorners.map {
            Offset(it.x * src.width, it.y * src.height)
        }
        val (tl, tr, br, bl) = orderPoints(pixelPts)

        val wTop = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val wBot = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val outW = max(wTop, wBot).toInt().coerceAtLeast(2)

        val hL = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val hR = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
        val outH = max(hL, hR).toInt().coerceAtLeast(2)

        val dstCorners = listOf(
            Offset(0f, 0f),
            Offset((outW - 1).toFloat(), 0f),
            Offset((outW - 1).toFloat(), (outH - 1).toFloat()),
            Offset(0f, (outH - 1).toFloat())
        )
        val srcCorners = listOf(tl, tr, br, bl)
        val h = solveHomography(dstCorners, srcCorners)

        val srcPixels = IntArray(src.width * src.height)
        src.getPixels(srcPixels, 0, src.width, 0, 0, src.width, src.height)

        val outPixels = IntArray(outW * outH)
        var idx = 0
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                val denom = h[6] * x + h[7] * y + 1.0
                val sx = (h[0] * x + h[1] * y + h[2]) / denom
                val sy = (h[3] * x + h[4] * y + h[5]) / denom
                outPixels[idx++] = bilinear(srcPixels, src.width, src.height, sx, sy)
            }
        }

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        out.setPixels(outPixels, 0, outW, 0, 0, outW, outH)
        return out
    }

    fun orderPoints(pts: List<Offset>): List<Offset> {
        require(pts.size == 4)
        val cx = pts.map { it.x }.average()
        val cy = pts.map { it.y }.average()
        val sorted = pts.sortedBy { atan2(it.y - cy, it.x - cx) }
        val tlIdx = sorted.indices.minBy { sorted[it].x + sorted[it].y }
        return listOf(
            sorted[tlIdx],
            sorted[(tlIdx + 1) % 4],
            sorted[(tlIdx + 2) % 4],
            sorted[(tlIdx + 3) % 4]
        )
    }

    private fun solveHomography(from: List<Offset>, to: List<Offset>): DoubleArray {
        val m = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val fx = from[i].x.toDouble(); val fy = from[i].y.toDouble()
            val tx = to[i].x.toDouble();   val ty = to[i].y.toDouble()
            m[i * 2]     = doubleArrayOf(fx, fy, 1.0, 0.0, 0.0, 0.0, -fx * tx, -fy * tx, tx)
            m[i * 2 + 1] = doubleArrayOf(0.0, 0.0, 0.0, fx, fy, 1.0, -fx * ty, -fy * ty, ty)
        }
        for (col in 0 until 8) {
            var pivot = col
            for (r in col + 1 until 8) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            val tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp
            for (r in col + 1 until 8) {
                val f = m[r][col] / m[col][col]
                for (k in col..8) m[r][k] -= f * m[col][k]
            }
        }
        val sol = DoubleArray(8)
        for (i in 7 downTo 0) {
            var s = m[i][8]
            for (j in i + 1 until 8) s -= m[i][j] * sol[j]
            sol[i] = s / m[i][i]
        }
        return sol
    }

    private fun bilinear(pixels: IntArray, w: Int, h: Int, x: Double, y: Double): Int {
        if (x < 0 || y < 0 || x >= w - 1 || y >= h - 1) {
            val xi = x.toInt().coerceIn(0, w - 1)
            val yi = y.toInt().coerceIn(0, h - 1)
            return pixels[yi * w + xi]
        }
        val x0 = x.toInt(); val y0 = y.toInt()
        val dx = x - x0; val dy = y - y0
        val p00 = pixels[y0 * w + x0]
        val p10 = pixels[y0 * w + x0 + 1]
        val p01 = pixels[(y0 + 1) * w + x0]
        val p11 = pixels[(y0 + 1) * w + x0 + 1]

        fun ch(c00: Int, c10: Int, c01: Int, c11: Int, sh: Int): Int {
            val top = ((c00 ushr sh and 0xFF) * (1 - dx) + (c10 ushr sh and 0xFF) * dx)
            val bot = ((c01 ushr sh and 0xFF) * (1 - dx) + (c11 ushr sh and 0xFF) * dx)
            return (top * (1 - dy) + bot * dy).toInt().coerceIn(0, 255)
        }
        val a = ch(p00, p10, p01, p11, 24)
        val r = ch(p00, p10, p01, p11, 16)
        val g = ch(p00, p10, p01, p11, 8)
        val b = ch(p00, p10, p01, p11, 0)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
