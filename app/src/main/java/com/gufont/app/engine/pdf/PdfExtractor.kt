package com.gufont.app.engine.pdf

import android.content.Context
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.io.RandomAccessBufferedFileInputStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.File
import java.io.OutputStream

enum class PdfKind { UNKNOWN, SCANNED, VECTOR }

/**
 * 从 PDF 中无损提取原始图像字节。
 *
 * 前提：调用方已用 PDDocument.load 打开 PDF，逐页传入 PDPage。
 *
 * 支持三种直出格式：
 *   - DCTDecode   → JPEG
 *   - JPXDecode   → JPEG 2000
 *   - CCITTFaxDecode → TIFF（传真编码）
 *
 * 其它情况（矢量图、文本）需要走渲染回退。
 */
object PdfExtractor {

    fun detectPdfKind(
        context: Context,
        pdfFile: File,
        sampleSize: Int = 8,
        ratioThreshold: Float = 0.75f
    ): PdfKind {
        var randomRead: RandomAccessBufferedFileInputStream? = null
        return try {
            randomRead = RandomAccessBufferedFileInputStream(pdfFile)
            val memSetting = MemoryUsageSetting.setupMixed(8L * 1024 * 1024)
            PDDocument.load(randomRead, memSetting).use { doc ->
                val total = doc.numberOfPages
                if (total == 0) return PdfKind.UNKNOWN
                val indices = pickSampleIndices(total, sampleSize)
                var scannedCount = 0
                for (i in indices) {
                    val page = doc.getPage(i)
                    if (isFullPageScannedImage(page)) scannedCount++
                }
                val ratio = scannedCount.toFloat() / indices.size
                if (ratio >= ratioThreshold) PdfKind.SCANNED else PdfKind.VECTOR
            }
        } catch (_: Throwable) {
            PdfKind.UNKNOWN
        } finally {
            try { randomRead?.close() } catch (_: Throwable) {}
        }
    }

    fun getPageCount(pdfFile: File): Int {
        var randomRead: RandomAccessBufferedFileInputStream? = null
        return try {
            randomRead = RandomAccessBufferedFileInputStream(pdfFile)
            PDDocument.load(
                randomRead,
                MemoryUsageSetting.setupMixed(2L * 1024 * 1024)
            ).use { it.numberOfPages }
        } catch (_: Throwable) {
            0
        } finally {
            try { randomRead?.close() } catch (_: Throwable) {}
        }
    }

    private fun pickSampleIndices(total: Int, sampleSize: Int): List<Int> {
        if (total <= sampleSize) return (0 until total).toList()
        val step = (total - 1).toFloat() / (sampleSize - 1)
        return (0 until sampleSize).map { (it * step).toInt() }.distinct()
    }

    private fun isFullPageScannedImage(page: PDPage): Boolean {
        return try {
            val resources = page.resources ?: return false
            val pageW = page.mediaBox.width
            val pageH = page.mediaBox.height
            for (name in resources.xObjectNames) {
                val obj = try { resources.getXObject(name) } catch (_: Throwable) { null }
                if (obj is PDImageXObject) {
                    if (obj.width >= pageW * 2 && obj.height >= pageH * 2) return true
                }
            }
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun readFilterNames(cos: COSStream): List<String> {
        val names = mutableListOf<String>()
        val filterObj = cos.getDictionaryObject(COSName.FILTER)
        when (filterObj) {
            is COSName -> names.add(filterObj.name)
            is COSArray -> {
                for (i in 0 until filterObj.size()) {
                    val item = filterObj.getObject(i)
                    if (item is COSName) names.add(item.name)
                }
            }
        }
        return names
    }

    private fun findLargestImage(page: PDPage): PDImageXObject? {
        val resources = page.resources ?: return null
        var best: PDImageXObject? = null
        var bestArea = 0L
        for (name in resources.xObjectNames) {
            val obj = try { resources.getXObject(name) } catch (_: Throwable) { null }
            if (obj is PDImageXObject) {
                val area = obj.width.toLong() * obj.height.toLong()
                if (area > bestArea) {
                    bestArea = area
                    best = obj
                }
            }
        }
        return best
    }

    /** 判断页面主图能否无损直出，返回扩展名或 null */
    fun detectRawFormat(page: PDPage): String? {
        return try {
            val target = findLargestImage(page) ?: return null
            val cos = target.cosObject as? COSStream ?: return null
            val filters = readFilterNames(cos)
            when {
                filters.contains("DCTDecode") -> "jpg"
                filters.contains("JPXDecode") -> "jp2"
                filters.contains("CCITTFaxDecode") -> "tif"
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** 直出原始字节流到 OutputStream */
    fun extractRawBytes(page: PDPage, out: OutputStream): Boolean {
        return try {
            val target = findLargestImage(page) ?: return false
            val cos = target.cosObject as? COSStream ?: return false
            val filters = readFilterNames(cos)
            if (!filters.contains("DCTDecode") &&
                !filters.contains("JPXDecode") &&
                !filters.contains("CCITTFaxDecode")
            ) {
                return false
            }
            cos.createRawInputStream().use { input ->
                input.copyTo(out, bufferSize = 256 * 1024)
            }
            out.flush()
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 估算页面主图对应的原始 DPI，用于渲染回退时匹配原图分辨率 */
    fun estimateRequiredDpi(
        page: PDPage,
        minDpi: Int = 300,
        maxDpi: Int = 600
    ): Int {
        return try {
            val image = findLargestImage(page) ?: return minDpi
            val pageWpt = page.mediaBox.width
            val pageHpt = page.mediaBox.height
            if (pageWpt <= 0f || pageHpt <= 0f) return minDpi
            val dpiW = image.width.toFloat() / pageWpt * 72f
            val dpiH = image.height.toFloat() / pageHpt * 72f
            val effective = minOf(dpiW, dpiH).toInt()
            val withMargin = (effective * 1.05f).toInt()
            withMargin.coerceIn(minDpi, maxDpi)
        } catch (_: Throwable) {
            minDpi
        }
    }
}
