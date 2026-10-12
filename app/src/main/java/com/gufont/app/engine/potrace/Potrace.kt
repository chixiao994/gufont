package com.gufont.app.engine.potrace

import android.graphics.Bitmap

object Potrace {

    fun trace(
        bitmap: Bitmap,
        params: PotraceParams = PotraceParams()
    ): List<PotracePath> {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h) { i ->
            val p = px[i]
            ((p shr 16 and 0xFF) * 299 +
                    (p shr 8 and 0xFF) * 587 +
                    (p and 0xFF) * 114) / 1000
        }
        val threshold = otsu(gray)
        var binary = BooleanArray(w * h) { gray[it] < threshold }

        if (params.minComponentSize > 0) {
            binary = removeSmallComponents(binary, w, h, params.minComponentSize)
        }
        if (params.maxHoleSize > 0) {
            binary = fillSmallHoles(binary, w, h, params.maxHoleSize)
        }

        val paths = PathDecomposer.decompose(binary, w, h, params)

        for (path in paths) {
            PolygonFitter.fit(path, params)
            CurveGenerator.generate(path, params)
            if (params.optimizeCurve) {
                CurveOptimizer.optimize(path, params)
            }
        }
        return paths
    }

    private fun removeSmallComponents(
        binary: BooleanArray, w: Int, h: Int, minSize: Int
    ): BooleanArray {
        val result = binary.copyOf()
        val visited = BooleanArray(w * h)
        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)

        for (i in binary.indices) {
            if (!binary[i] || visited[i]) continue
            val comp = mutableListOf<Int>()
            val queue = ArrayDeque<Int>()
            queue.add(i)
            visited[i] = true
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                comp.add(cur)
                val cx = cur % w
                val cy = cur / w
                for (d in 0 until 4) {
                    val nx = cx + dx[d]
                    val ny = cy + dy[d]
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                    val ni = ny * w + nx
                    if (visited[ni] || !binary[ni]) continue
                    visited[ni] = true
                    queue.add(ni)
                }
            }
            if (comp.size < minSize) {
                for (idx in comp) result[idx] = false
            }
        }
        return result
    }

    private fun fillSmallHoles(
        binary: BooleanArray, w: Int, h: Int, maxSize: Int
    ): BooleanArray {
        val result = binary.copyOf()
        val visited = BooleanArray(w * h)
        val dx = intArrayOf(1, -1, 0, 0)
        val dy = intArrayOf(0, 0, 1, -1)

        // 1) 从边界 flood fill 出所有"外部背景"
        val queue = ArrayDeque<Int>()
        for (x in 0 until w) {
            for (y in intArrayOf(0, h - 1)) {
                val i = y * w + x
                if (!result[i] && !visited[i]) {
                    visited[i] = true
                    queue.add(i)
                }
            }
        }
        for (y in 0 until h) {
            for (x in intArrayOf(0, w - 1)) {
                val i = y * w + x
                if (!result[i] && !visited[i]) {
                    visited[i] = true
                    queue.add(i)
                }
            }
        }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val cx = cur % w
            val cy = cur / w
            for (d in 0 until 4) {
                val nx = cx + dx[d]
                val ny = cy + dy[d]
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                val ni = ny * w + nx
                if (result[ni] || visited[ni]) continue
                visited[ni] = true
                queue.add(ni)
            }
        }

        // 2) 未被标记的背景 = 内部孔洞，找出来，面积小的填补
        val holeVisited = BooleanArray(w * h)
        for (i in result.indices) {
            if (result[i] || visited[i] || holeVisited[i]) continue
            val hole = mutableListOf<Int>()
            val q2 = ArrayDeque<Int>()
            q2.add(i)
            holeVisited[i] = true
            while (q2.isNotEmpty()) {
                val cur = q2.removeFirst()
                hole.add(cur)
                val cx = cur % w
                val cy = cur / w
                for (d in 0 until 4) {
                    val nx = cx + dx[d]
                    val ny = cy + dy[d]
                    if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue
                    val ni = ny * w + nx
                    if (result[ni] || visited[ni] || holeVisited[ni]) continue
                    holeVisited[ni] = true
                    q2.add(ni)
                }
            }
            if (hole.size <= maxSize) {
                for (idx in hole) result[idx] = true
            }
        }
        return result
    }

    private fun otsu(gray: IntArray): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v.coerceIn(0, 255)]++
        val total = gray.size
        var sumAll = 0.0
        for (i in 0..255) sumAll += i.toDouble() * hist[i]
        var sumB = 0.0; var wB = 0; var best = 0.0; var thr = 128
        for (i in 0..255) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i.toDouble() * hist[i]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val v = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (v > best) { best = v; thr = i }
        }
        return thr
    }
}
