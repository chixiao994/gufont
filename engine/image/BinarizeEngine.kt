package com.gufont.app.engine.image

import android.graphics.Bitmap

object BinarizeEngine {

    fun binarize(
        src: Bitmap,
        mode: String,
        threshold: Int,
        invert: Boolean
    ): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src

        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (r * 77 + g * 150 + b * 29) shr 8
        }

        val bin = IntArray(w * h)
        when (mode) {
            "fixed" -> {
                val t = threshold.coerceIn(0, 255)
                for (i in gray.indices) bin[i] = if (gray[i] > t) 255 else 0
            }
            "adaptive" -> adaptiveThreshold(gray, bin, w, h, 15, 10)
            else -> {
                val t = otsu(gray)
                for (i in gray.indices) bin[i] = if (gray[i] > t) 255 else 0
            }
        }

        val out = IntArray(w * h)
        for (i in bin.indices) {
            var v = bin[i]
            if (invert) v = 255 - v
            out[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(out, 0, w, 0, 0, w, h)
        }
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

    private fun adaptiveThreshold(
        gray: IntArray, out: IntArray, w: Int, h: Int,
        blockSize: Int, c: Int
    ) {
        val stride = w + 1
        val integral = LongArray(stride * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            val rowOff = (y + 1) * stride
            val prevOff = y * stride
            val srcOff = y * w
            for (x in 0 until w) {
                rowSum += gray[srcOff + x].toLong()
                integral[rowOff + x + 1] = integral[prevOff + x + 1] + rowSum
            }
        }
        val r = blockSize / 2
        for (y in 0 until h) {
            for (x in 0 until w) {
                val x1 = (x - r).coerceAtLeast(0)
                val y1 = (y - r).coerceAtLeast(0)
                val x2 = (x + r).coerceAtMost(w - 1)
                val y2 = (y + r).coerceAtMost(h - 1)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)
                val sum = integral[(y2 + 1) * stride + (x2 + 1)] -
                        integral[y1 * stride + (x2 + 1)] -
                        integral[(y2 + 1) * stride + x1] +
                        integral[y1 * stride + x1]
                val mean = sum / count
                out[y * w + x] = if (gray[y * w + x] > mean - c) 255 else 0
            }
        }
    }
}
