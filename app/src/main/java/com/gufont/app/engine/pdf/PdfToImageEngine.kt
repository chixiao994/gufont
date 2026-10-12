package com.gufont.app.engine.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.gufont.app.data.model.Page
import com.gufont.app.data.storage.ProjectStorage
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.io.RandomAccessBufferedFileInputStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * PDF → 逐页图像，导入到项目 pages/ 目录。
 *
 * 策略：
 *   1. 用 PDFBox 抽样判定 PDF 类型
 *   2. 扫描件：优先直出原始 JPEG/JP2/TIFF 字节（真正无损）
 *   3. 矢量/文本：PdfRenderer 按匹配 DPI 渲染 PNG
 *   4. 内存保护：每 150 页重开 PDDocument
 */
object PdfToImageEngine {

    const val DEFAULT_DPI = 300
    private const val EXPORT_MAX_DPI = 600
    private const val EXPORT_MAX_BITMAP_BYTES = 256L * 1024 * 1024
    private const val RESTART_EVERY = 150
    private const val PDFBOX_CACHE_BYTES = 64L * 1024 * 1024

    suspend fun convert(
        context: Context,
        pdfUri: Uri,
        storage: ProjectStorage,
        dpi: Int = DEFAULT_DPI,
        onProgress: suspend (done: Int, total: Int, name: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {

        // 1. 复制 PDF 到私有目录（PDFBox 需要随机访问文件）
        val cachePdf = File(context.cacheDir, "import_source.pdf")
        try {
            if (cachePdf.exists()) cachePdf.delete()
            context.contentResolver.openInputStream(pdfUri)?.use { ins ->
                cachePdf.outputStream().use { outs ->
                    ins.copyTo(outs, 256 * 1024)
                }
            } ?: error("无法读取 PDF")
        } catch (e: Exception) {
            throw IllegalStateException("复制 PDF 失败：${e.message}")
        }

        // 2. 判定类型
        val kind = PdfExtractor.detectPdfKind(context, cachePdf)

        // 3. 准备 PdfRenderer（用于回退渲染）
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        var randomRead: RandomAccessBufferedFileInputStream? = null
        var doc: PDDocument? = null

        var total = 0
        var directCount = 0
        var renderCount = 0
        var restartCount = 0

        try {
            randomRead = RandomAccessBufferedFileInputStream(cachePdf)
            doc = PDDocument.load(
                randomRead,
                MemoryUsageSetting.setupMixed(PDFBOX_CACHE_BYTES)
            )
            total = doc.numberOfPages
            if (total == 0) error("PDF 没有页面")

            for (i in 0 until total) {
                val pageIndex = i + 1
                var handled = false
                var srcName = ""

                // A. 尝试直出原始字节
                val page = try { doc!!.getPage(i) } catch (_: Throwable) { null }
                if (page != null) {
                    val rawFormat = PdfExtractor.detectRawFormat(page)
                    if (rawFormat != null) {
                        val candidateName = "page_${pageIndex.toString().padStart(3, '0')}_src.$rawFormat"
                        val mime = when (rawFormat) {
                            "jpg" -> "image/jpeg"
                            "jp2" -> "image/jp2"
                            "tif" -> "image/tiff"
                            else -> "application/octet-stream"
                        }

                        val ok = try {
                            val bytes = ByteArrayOutputStream().use { bos ->
                                BufferedOutputStream(bos, 256 * 1024).use { bout ->
                                    PdfExtractor.extractRawBytes(page, bout)
                                }
                                bos.toByteArray()
                            }
                            if (bytes.isNotEmpty()) {
                                storage.writeBinary("pages", candidateName, mime, bytes)
                                true
                            } else false
                        } catch (_: Throwable) { false }

                        if (ok) {
                            handled = true
                            directCount++
                            srcName = candidateName
                        }
                    }
                }

                // B. 回退：渲染 PNG
                if (!handled) {
                    if (renderer == null) {
                        pfd = ParcelFileDescriptor.open(
                            cachePdf, ParcelFileDescriptor.MODE_READ_ONLY
                        )
                        renderer = PdfRenderer(pfd!!)
                    }
                    val targetDpi = if (page != null)
                        PdfExtractor.estimateRequiredDpi(page, dpi, EXPORT_MAX_DPI)
                    else dpi
                    val bmp = renderBitmap(renderer!!, i, targetDpi)
                    try {
                        val bytes = ByteArrayOutputStream().use { bos ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                            bos.toByteArray()
                        }
                        srcName = "page_${pageIndex.toString().padStart(3, '0')}_src.png"
                        storage.writeBinary("pages", srcName, "image/png", bytes)
                        renderCount++
                    } finally {
                        bmp.recycle()
                    }
                }

                // C. 更新 page JSON
                val existing = storage.loadPage(pageIndex)
                val toSave = existing?.copy(srcName = srcName)
                    ?: Page(pageIndex = pageIndex, srcName = srcName)
                storage.savePage(toSave)

                onProgress(pageIndex, total, srcName)

                // D. 内存保护：每 N 页重开 PDDocument
                if (i < total - 1 && (i + 1) % RESTART_EVERY == 0) {
                    try { doc?.close() } catch (_: Throwable) {}
                    try { randomRead?.close() } catch (_: Throwable) {}
                    doc = null
                    randomRead = null
                    System.gc()
                    randomRead = RandomAccessBufferedFileInputStream(cachePdf)
                    doc = PDDocument.load(
                        randomRead,
                        MemoryUsageSetting.setupMixed(PDFBOX_CACHE_BYTES)
                    )
                    restartCount++
                }
            }

            return@withContext total
        } finally {
            try { doc?.close() } catch (_: Throwable) {}
            try { randomRead?.close() } catch (_: Throwable) {}
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
            try { cachePdf.delete() } catch (_: Throwable) {}
        }
    }

    private fun renderBitmap(
        renderer: PdfRenderer, index: Int, targetDpi: Int
    ): Bitmap {
        val page = renderer.openPage(index)
        try {
            val scale = targetDpi / 72f
            var w = (page.width * scale).toInt().coerceAtLeast(1)
            var h = (page.height * scale).toInt().coerceAtLeast(1)

            val maxPixels = EXPORT_MAX_BITMAP_BYTES / 4L
            val pixels = w.toLong() * h.toLong()
            if (pixels > maxPixels) {
                val r = kotlin.math.sqrt(maxPixels.toDouble() / pixels.toDouble())
                w = (w * r).toInt().coerceAtLeast(1)
                h = (h * r).toInt().coerceAtLeast(1)
            }

            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            try { page.close() } catch (_: Throwable) {}
        }
    }

    fun srcNameOf(pageIndex: Int): String =
        "page_${pageIndex.toString().padStart(3, '0')}_src.png"

    fun correctedNameOf(pageIndex: Int): String =
        "page_${pageIndex.toString().padStart(3, '0')}_corrected.png"
}
