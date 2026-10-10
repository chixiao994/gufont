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

/**
 * PDF → 逐页 PNG/JPG 导入到项目 pages/ 目录。
 *
 * 使用系统 PdfRenderer，300 DPI 渲染。
 * 每页写两份：
 *   - pages/page_NNN_src.jpg   （位图）
 *   - pages/page_NNN.json      （元数据，pageIndex + srcName）
 */
object PdfToImageEngine {

    const val DEFAULT_DPI = 300

    suspend fun convert(
        context: Context,
        pdfUri: Uri,
        storage: ProjectStorage,
        dpi: Int = DEFAULT_DPI,
        jpegQuality: Int = 92,
        onProgress: suspend (done: Int, total: Int, name: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {

        val pfd: ParcelFileDescriptor =
            context.contentResolver.openFileDescriptor(pdfUri, "r")
                ?: error("无法打开 PDF")

        val renderer = PdfRenderer(pfd)
        try {
            val total = renderer.pageCount
            if (total == 0) error("PDF 没有页面")

            val scale = dpi / 72f

            for (i in 0 until total) {
                val page = renderer.openPage(i)
                try {
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)

                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val pageIndex = i + 1
                    val srcName = srcNameOf(pageIndex)

                    val bytes = ByteArrayOutputStream().use { bos ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, bos)
                        bos.toByteArray()
                    }
                    bmp.recycle()

                    storage.writeImage("pages", srcName, bytes)

                    // 若已有该页 JSON 则保留其它字段，只覆盖 srcName
                    val existing = storage.loadPage(pageIndex)
                    val toSave = existing?.copy(srcName = srcName)
                        ?: Page(pageIndex = pageIndex, srcName = srcName)
                    storage.savePage(toSave)

                    onProgress(pageIndex, total, srcName)
                } finally {
                    page.close()
                }
            }
            total
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    fun srcNameOf(pageIndex: Int): String =
        "page_${pageIndex.toString().padStart(3, '0')}_src.jpg"

    fun correctedNameOf(pageIndex: Int): String =
        "page_${pageIndex.toString().padStart(3, '0')}_corrected.png"
}
