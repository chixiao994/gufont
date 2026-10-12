package com.gufont.app.engine.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.gufont.app.data.model.BinarySettings
import com.gufont.app.data.model.Glyph
import com.gufont.app.engine.font.FontGlyph
import com.gufont.app.engine.font.GlyphConverter
import com.gufont.app.engine.image.BinarizeEngine
import com.gufont.app.engine.image.BitmapIO
import com.gufont.app.engine.potrace.Potrace
import com.gufont.app.engine.potrace.PotraceParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GlyphPipeline {

    /**
     * 单字处理：
     *   raw/edited 位图 → 二值化 → Potrace → FontGlyph
     */
    suspend fun process(
        context: Context,
        glyph: Glyph,
        binary: BinarySettings,
        potraceParams: PotraceParams = PotraceParams()
    ): FontGlyph? = withContext(Dispatchers.Default) {
        val srcBytes = loadGlyphBytes(context, glyph)
            ?: return@withContext null
        val src = android.graphics.BitmapFactory.decodeByteArray(
            srcBytes, 0, srcBytes.size,
            android.graphics.BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return@withContext null

        val binaryBmp = BinarizeEngine.binarize(
            src, binary.mode, binary.threshold, binary.invert
        )
        src.recycle()

        val paths = Potrace.trace(binaryBmp, potraceParams)
        binaryBmp.recycle()

        if (paths.isEmpty()) return@withContext null

        GlyphConverter.convert(
            paths,
            src.width,
            src.height,
            glyph.codepoint,
            unitsPerEm = 1000
        )
    }

    private suspend fun loadGlyphBytes(context: Context, glyph: Glyph): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val fileName = glyph.edited ?: glyph.raw
                // TODO: 传 URI 进来 —— 这里暂时返回 null，由调用方传入 URI
                null
            } catch (_: Exception) { null }
        }
}
