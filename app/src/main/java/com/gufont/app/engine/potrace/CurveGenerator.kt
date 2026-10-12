package com.gufont.app.engine.potrace

import kotlin.math.cos

/**
 * 从简化多边形生成曲线段。
 *
 * 思路：
 *   1. 检测角点（相邻两边的转角超过阈值）
 *   2. 相邻角点之间，用中点法生成二次贝塞尔
 *   3. 段类型：
 *      - CORNER：直线段（多边形直边）
 *      - CURVETO：二次贝塞尔
 *   每个 CORNER 段用 (起点, 终点) 表示（c[0]=起点, c[1]=终点）
 *   每个 CURVETO 段用 (起点, 控制点, 终点) 表示
 */
object CurveGenerator {

    fun generate(path: PotracePath, params: PotraceParams) {
        val pts = path.points
        val polygon = path.optimalPolygon
        val n = polygon.size
        if (n < 3) {
            path.curveSegments = emptyList()
            return
        }

        val vertices = polygon.map {
            DPoint(pts[it].x.toDouble(), pts[it].y.toDouble())
        }

        // 角点检测
        val isCorner = BooleanArray(n)
        val cosThr = cos(Math.toRadians(params.cornerAngleDeg))
        for (i in 0 until n) {
            val prev = vertices[(i - 1 + n) % n]
            val cur = vertices[i]
            val next = vertices[(i + 1) % n]
            val v1 = cur - prev
            val v2 = next - cur
            val l1 = v1.length()
            val l2 = v2.length()
            if (l1 < 1e-6 || l2 < 1e-6) {
                isCorner[i] = true
                continue
            }
            val cosA = v1.dot(v2) / (l1 * l2)
            if (cosA < cosThr) isCorner[i] = true
        }

        val segments = mutableListOf<CurveSegment>()

        val cornerIdx = ArrayList<Int>()
        for (i in 0 until n) if (isCorner[i]) cornerIdx.add(i)

        // 全是曲线点：整圈用中点法
        if (cornerIdx.isEmpty()) {
            val firstMid = mid(vertices[n - 1], vertices[0])
            segments.add(CurveSegment(SegmentTag.CORNER, arrayOf(firstMid, firstMid)))
            for (i in 0 until n) {
                val end = mid(vertices[i], vertices[(i + 1) % n])
                segments.add(
                    CurveSegment(
                        SegmentTag.CURVETO,
                        arrayOf(
                            mid(vertices[(i - 1 + n) % n], vertices[i]),
                            vertices[i],
                            end
                        )
                    )
                )
            }
            path.curveSegments = segments
            return
        }

        val startIdx = cornerIdx[0]
        segments.add(
            CurveSegment(
                SegmentTag.CORNER,
                arrayOf(vertices[startIdx], vertices[startIdx])
            )
        )

        var i = startIdx
        var remaining = n
        while (remaining > 0) {
            var steps = 1
            var j = (i + 1) % n
            while (!isCorner[j] && steps < n) {
                j = (j + 1) % n
                steps++
            }

            val P = ArrayList<DPoint>(steps + 1)
            for (s in 0..steps) P.add(vertices[(i + s) % n])

            if (steps == 1) {
                segments.add(CurveSegment(SegmentTag.CORNER, arrayOf(P[0], P[1])))
            } else {
                val M = ArrayList<DPoint>(steps)
                for (s in 0 until steps) M.add(mid(P[s], P[s + 1]))

                segments.add(CurveSegment(SegmentTag.CORNER, arrayOf(P[0], M[0])))
                for (s in 0 until steps - 1) {
                    segments.add(
                        CurveSegment(
                            SegmentTag.CURVETO,
                            arrayOf(M[s], P[s + 1], M[s + 1])
                        )
                    )
                }
                segments.add(
                    CurveSegment(
                        SegmentTag.CORNER,
                        arrayOf(M[steps - 1], P[steps])
                    )
                )
            }

            remaining -= steps
            if (j == startIdx) break
            i = j
        }

        path.curveSegments = segments
    }

    private fun mid(a: DPoint, b: DPoint) =
        DPoint((a.x + b.x) * 0.5, (a.y + b.y) * 0.5)
}
