package com.gufont.app.engine.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.gufont.app.data.model.BinarySettings
import com.gufont.app.engine.font.FontGlyph
import com.gufont.app.engine.font.GlyphConverter
import com.gufont.app.engine.image.BinarizeEngine
import com.gufont.app.engine.potrace.Potrace
import com.gufont.app.engine.potrace.PotraceParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GlyphPipeline {

    suspend fun process(
        uri: Uri,
        context: Context,
        codepoint: Int,
        binary: BinarySettings,
        potraceParams: PotraceParams = PotraceParams()
    ): FontGlyph? = withContext(Dispatchers.Default) {
        val srcBytes = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) { null } ?: return@withContext null

        val src: Bitmap = BitmapFactory.decodeByteArray(
            srcBytes, 0, srcBytes.size,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        ) ?: return@withContext null

        val w = src.width
        val h = src.height

        val binaryBmp = BinarizeEngine.binarize(
            src, binary.mode, binary.threshold, binary.invert
        )
        src.recycle()

        val paths = Potrace.trace(binaryBmp, potraceParams)
        binaryBmp.recycle()

        if (paths.isEmpty()) return@withContext null

        GlyphConverter.convert(paths, w, h, codepoint, unitsPerEm = 1000)
    }
}
