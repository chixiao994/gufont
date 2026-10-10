package com.gufont.app.engine.potrace

import kotlin.math.abs
import kotlin.math.hypot

/**
 * Douglas-Peucker 多边形简化。
 * 输入：密集的像素边界点
 * 输出：稀疏的关键顶点索引列表
 */
object PolygonFitter {

    fun fit(path: PotracePath, params: PotraceParams) {
        val pts = path.points
        val n = pts.size
        if (n < 3) {
            path.optimalPolygon = emptyList()
            return
        }
        path.optimalPolygon = rdp(pts, params.rdpEpsilon.toFloat())
    }

    private fun rdp(pts: List<IntPoint>, eps: Float): List<Int> {
        val n = pts.size
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true
        rdpRec(pts, 0, n - 1, eps, keep)
        return pts.indices.filter { keep[it] }
    }

    private fun rdpRec(
        pts: List<IntPoint>, s: Int, e: Int, eps: Float, keep: BooleanArray
    ) {
        if (e <= s + 1) return
        var maxD = 0f
        var idx = -1
        val a = pts[s]; val b = pts[e]
        for (i in s + 1 until e) {
            val d = perpDist(pts[i], a, b)
            if (d > maxD) { maxD = d; idx = i }
        }
        if (maxD > eps && idx > 0) {
            keep[idx] = true
            rdpRec(pts, s, idx, eps, keep)
            rdpRec(pts, idx, e, eps, keep)
        }
    }

    private fun perpDist(p: IntPoint, a: IntPoint, b: IntPoint): Float {
        val dx = (b.x - a.x).toFloat()
        val dy = (b.y - a.y).toFloat()
        val len = hypot(dx, dy)
        if (len < 0.001f) {
            return hypot((p.x - a.x).toFloat(), (p.y - a.y).toFloat())
        }
        return abs(dx * (p.y - a.y) - dy * (p.x - a.x)) / len
    }
}
