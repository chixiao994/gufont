package com.gufont.app.engine.potrace

/**
 * 合并凸性一致的相邻曲线段。
 * 减少矢量节点数，让曲线更平滑。
 *
 * 合并条件：
 *   1. 两个段都是 CURVETO
 *   2. 前段终点 ≈ 后段起点（距离 < tolerance）
 *   3. 方向一致（叉积 ≥ 0）
 */
object CurveOptimizer {

    fun optimize(path: PotracePath, params: PotraceParams) {
        val segments = path.curveSegments
        if (segments.size < 2) {
            path.optimizedSegments = segments
            return
        }

        val optimized = mutableListOf<CurveSegment>()
        var current = segments[0]

        for (i in 1 until segments.size) {
            val next = segments[i]
            val merged = tryMerge(current, next, params.optTolerance)
            if (merged != null) {
                current = merged
            } else {
                optimized.add(current)
                current = next
            }
        }
        optimized.add(current)
        path.optimizedSegments = optimized
    }

    private fun tryMerge(
        a: CurveSegment, b: CurveSegment, tolerance: Double
    ): CurveSegment? {
        if (a.tag != SegmentTag.CURVETO || b.tag != SegmentTag.CURVETO) return null

        val aEnd = a.c[2]
        val bStart = b.c[0]
        if (aEnd.distanceTo(bStart) > tolerance) return null

        val aVec = a.c[2] - a.c[0]
        val bVec = b.c[2] - b.c[0]
        if (aVec.cross(bVec) < 0) return null

        val newCtrl = DPoint(
            (a.c[1].x + b.c[1].x) * 0.5,
            (a.c[1].y + b.c[1].y) * 0.5
        )
        val newEnd = b.c[2]
        val newAlpha = (a.alpha + b.alpha) * 0.5
        return CurveSegment(
            SegmentTag.CURVETO,
            arrayOf(a.c[0], newCtrl, newEnd),
            newAlpha
        )
    }
}
