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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.sqrt

/**
 * PDF → 逐页 PNG（多线程渲染）。
 *
 * 每个 worker 有独立的 PdfRenderer（PdfRenderer 非线程安全）。
 * 任务分发用 AtomicInteger，写文件用 Mutex 串行化（SAF 非线程安全）。
 */
object PdfToImageEngine {

    const val DEFAULT_DPI = 300
    private const val EXPORT_MAX_BITMAP_BYTES = 256L * 1024 * 1024
    private const val PER_THREAD_BUDGET_MB = 64L

    /**
     * 自动决定线程数：
     *   看 CPU 核心数和可用堆内存，两者取小。
     */
    fun defaultThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        val maxHeapMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val byCores = cores.coerceIn(2, 6)
        val byMem = (maxHeapMb / PER_THREAD_BUDGET_MB).coerceIn(1, 6).toInt()
        return minOf(byCores, byMem).coerceAtLeast(1)
    }

    suspend fun convert(
        context: Context,
        pdfUri: Uri,
        storage: ProjectStorage,
        dpi: Int = DEFAULT_DPI,
        threads: Int = 0,
        onProgress: suspend (done: Int, total: Int, name: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {

        val workerCount = if (threads <= 0) defaultThreads() else threads.coerceIn(1, 8)

        // 1. 复制 PDF 到私有目录
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

        // 2. 获取总页数
        val total = getPageCount(cachePdf)
        if (total == 0) {
            cachePdf.delete()
            error("PDF 没有页面")
        }

        // 3. 并发渲染
        val nextIndex = AtomicInteger(0)
        val doneCount = AtomicInteger(0)
        val writeMutex = Mutex()

        try {
            coroutineScope {
                val workers = (0 until workerCount).map {
                    launch(Dispatchers.IO) {
                        workerLoop(
                            context, cachePdf, storage, total, dpi,
                            nextIndex, doneCount, writeMutex, onProgress
                        )
                    }
                }
                workers.forEach { it.join() }
            }
            return@withContext total
        } finally {
            try { cachePdf.delete() } catch (_: Throwable) {}
        }
    }

    private suspend fun workerLoop(
        context: Context,
        cachePdf: File,
        storage: ProjectStorage,
        total: Int,
        dpi: Int,
        nextIndex: AtomicInteger,
        doneCount: AtomicInteger,
        writeMutex: Mutex,
        onProgress: suspend (done: Int, total: Int, name: String) -> Unit
    ) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(
                cachePdf, ParcelFileDescriptor.MODE_READ_ONLY
            )
            renderer = PdfRenderer(pfd)

            while (true) {
                val i = nextIndex.getAndIncrement()
                if (i >= total) break

                val pageIndex = i + 1
                val srcName = srcNameOf(pageIndex)

                val bmp: Bitmap? = try {
                    renderBitmap(renderer, i, dpi)
                } catch (_: Throwable) {
                    null
                }

                if (bmp != null) {
                    try {
                        val bytes = ByteArrayOutputStream().use { bos ->
                            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                            bos.toByteArray()
                        }
                        // 串行写文件（SAF 非线程安全）
                        writeMutex.withLock {
                            storage.writeImage("pages", srcName, bytes, "image/png")
                            val existing = storage.loadPage(pageIndex)
                            val toSave = existing?.copy(srcName = srcName)
                                ?: Page(pageIndex = pageIndex, srcName = srcName)
                            storage.savePage(toSave)
                        }
                    } finally {
                        bmp.recycle()
                    }
                }

                val done = doneCount.incrementAndGet()
                try {
                    onProgress(done, total, srcName)
                } catch (_: Throwable) {}
            }
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
    }

    private fun getPageCount(pdfFile: File): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(
                pdfFile, ParcelFileDescriptor.MODE_READ_ONLY
            )
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (_: Throwable) {
            0
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
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
