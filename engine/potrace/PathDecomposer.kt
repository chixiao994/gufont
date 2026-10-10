package com.gufont.app.engine.potrace

import kotlin.math.abs

/**
 * 从二值图提取边界轮廓。
 *
 * 思路：每个前景像素的四条边（上/右/下/左）如果相邻是背景，
 * 就是一条边界边；所有边界边连成闭环就是轮廓。
 * 追踪规则：右转优先，保证不重叠、方向一致。
 */
object PathDecomposer {

    private data class Edge(val x: Int, val y: Int, val dir: Int, val id: Int) {
        val nextX: Int get() = when (dir) {
            0 -> x + 1
            1 -> x
            2 -> x - 1
            else -> x
        }
        val nextY: Int get() = when (dir) {
            0 -> y
            1 -> y + 1
            2 -> y
            else -> y - 1
        }
    }

    fun decompose(
        binary: BooleanArray,
        w: Int, h: Int,
        params: PotraceParams
    ): List<PotracePath> {
        val edges = ArrayList<Edge>()
        var id = 0

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!binary[y * w + x]) continue

                if (y == 0 || !binary[(y - 1) * w + x]) {
                    edges.add(Edge(x, y, 0, id++))
                }
                if (x == w - 1 || !binary[y * w + x + 1]) {
                    edges.add(Edge(x + 1, y, 1, id++))
                }
                if (y == h - 1 || !binary[(y + 1) * w + x]) {
                    edges.add(Edge(x + 1, y + 1, 2, id++))
                }
                if (x == 0 || !binary[y * w + x - 1]) {
                    edges.add(Edge(x, y + 1, 3, id++))
                }
            }
        }

        if (edges.isEmpty()) return emptyList()

        val wPlus = w + 1
        val outByPoint = HashMap<Long, MutableList<Int>>()
        for (e in edges) {
            val key = e.y.toLong() * wPlus + e.x
            outByPoint.getOrPut(key) { mutableListOf() }.add(e.id)
        }

        val visited = BooleanArray(edges.size)
        val paths = mutableListOf<PotracePath>()

        for (startId in edges.indices) {
            if (visited[startId]) continue
            val cycle = traceCycle(edges, outByPoint, visited, startId, wPlus)
            if (cycle != null && cycle.size >= 4) {
                val path = PotracePath(cycle)
                path.area = computeArea(cycle)
                paths.add(path)
            }
        }

        return paths.filter { abs(it.area) >= params.turdSize.toLong() }
    }

    private fun traceCycle(
        edges: List<Edge>,
        outByPoint: Map<Long, List<Int>>,
        visited: BooleanArray,
        startId: Int,
        wPlus: Int
    ): List<IntPoint>? {
        val points = mutableListOf<IntPoint>()
        var curId = startId
        var dirIn = -1
        var guard = 0
        val maxSteps = edges.size * 2 + 16

        while (guard < maxSteps) {
            guard++
            val cur = edges[curId]
            points.add(IntPoint(cur.x, cur.y))
            visited[curId] = true

            val nx = cur.nextX
            val ny = cur.nextY
            val nextKey = ny.toLong() * wPlus + nx
            val candidates = outByPoint[nextKey] ?: break

            val dirOrder = if (dirIn < 0) {
                intArrayOf(0, 1, 2, 3)
            } else {
                intArrayOf(
                    (dirIn + 1) % 4,
                    dirIn,
                    (dirIn + 3) % 4,
                    (dirIn + 2) % 4
                )
            }

            var nextId = -1
            outer@ for (d in dirOrder) {
                for (cid in candidates) {
                    if (visited[cid]) continue
                    if (edges[cid].dir != d) continue
                    nextId = cid
                    break@outer
                }
            }

            if (nextId < 0) break
            curId = nextId
            dirIn = edges[curId].dir
            if (curId == startId) break
        }

        if (points.isNotEmpty()) {
            val first = points.first()
            val last = points.last()
            if (first.x != last.x || first.y != last.y) {
                points.add(IntPoint(first.x, first.y))
            }
        }
        return points
    }

    private fun computeArea(points: List<IntPoint>): Long {
        var area = 0L
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            area += p.x.toLong() * q.y - q.x.toLong() * p.y
        }
        return area / 2
    }
}
