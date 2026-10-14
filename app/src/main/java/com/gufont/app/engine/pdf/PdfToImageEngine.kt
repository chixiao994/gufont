package com.gufont.app.engine.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.gufont.app.data.model.Page
import com.gufont.app.data.storage.ProjectStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.sqrt

/**
 * PDF → 逐页 PNG（PdfRenderer 渲染，PNG 无损输出）。
 *
 * 说明：
 *   PdfRenderer 会把 PDF 光栅化，无法直出原始 JPEG 字节。
 *   输出统一用 PNG，避免二次 JPEG 压缩损失。
 */
object PdfToImageEngine {

    const val DEFAULT_DPI = 300
    private const val EXPORT_MAX_DPI = 600
    private const val EXPORT_MAX_BITMAP_BYTES = 256L * 1024 * 1024

    suspend fun convert(
        context: Context,
        pdfUri: Uri,
        storage: ProjectStorage,
        dpi: Int = DEFAULT_DPI,
        onProgress: suspend (done: Int, total: Int, name: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {

        // 复制到私有目录，PdfRenderer 需要可随机访问的文件
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

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(
                cachePdf, ParcelFileDescriptor.MODE_READ_ONLY
            )
            renderer = PdfRenderer(pfd)
            val total = renderer.pageCount
            if (total == 0) error("PDF 没有页面")

            for (i in 0 until total) {
                val pageIndex = i + 1
                val bmp = renderBitmap(renderer, i, dpi)
                try {
                    val bytes = ByteArrayOutputStream().use { bos ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                        bos.toByteArray()
                    }
                    val srcName = srcNameOf(pageIndex)
                    storage.writeImage("pages", srcName, bytes, "image/png")

                    val existing = storage.loadPage(pageIndex)
                    val toSave = existing?.copy(srcName = srcName)
                        ?: Page(pageIndex = pageIndex, srcName = srcName)
                    storage.savePage(toSave)

                    onProgress(pageIndex, total, srcName)
                } finally {
                    bmp.recycle()
                }
            }

            return@withContext total
        } finally {
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
                val r = sqrt(maxPixels.toDouble() / pixels.toDouble())
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
