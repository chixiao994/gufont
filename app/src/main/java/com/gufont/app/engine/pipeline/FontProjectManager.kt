package com.gufont.app.engine.pipeline

import android.content.Context
import com.gufont.app.data.model.GlyphState
import com.gufont.app.data.model.Project
import com.gufont.app.data.storage.ProjectStorage
import com.gufont.app.engine.font.FontGlyph
import com.gufont.app.engine.font.TtfBuilder
import com.gufont.app.engine.potrace.PotraceParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FontProjectManager {

    suspend fun rebuildFont(
        context: Context,
        storage: ProjectStorage,
        project: Project
    ): Pair<Int, List<Pair<String, String>>> = withContext(Dispatchers.Default) {

        val gi = storage.loadGlyphIndex()
        val fontGlyphs = ArrayList<FontGlyph>()
        val issues = ArrayList<Pair<String, String>>()
        val usedCodepoints = HashSet<Int>()

        val potraceParams = PotraceParams(
            turdSize = project.settings.potraceTurdSize,
            alphaMax = project.settings.potraceAlphaMax,
            optTolerance = project.settings.potraceOptTolerance
        )

        for (g in gi.glyphs) {
            if (g.state == GlyphState.SKIPPED) continue
            if (g.codepoint <= 0) continue

            if (!usedCodepoints.add(g.codepoint)) {
                issues.add(g.code to "重码 U+${g.codepoint.toString(16).uppercase()}")
                continue
            }

            val fileName = g.edited ?: g.raw
            val uri = storage.openFileUri("glyphs", fileName) ?: continue

            try {
                val fg = GlyphPipeline.process(
                    uri = uri,
                    context = context,
                    codepoint = g.codepoint,
                    binary = g.binaryOverride ?: project.settings.toBinarySettings(),
                    potraceParams = potraceParams
                )
                if (fg == null) {
                    issues.add(g.code to "轮廓为空")
                } else {
                    fontGlyphs.add(fg)
                }
            } catch (e: Exception) {
                issues.add(g.code to "处理异常：${e.message}")
            }
        }

        if (fontGlyphs.isEmpty()) {
            return@withContext Pair(0, issues)
        }

        val bytes = TtfBuilder(project.settings.fontFamily).build(fontGlyphs)
        storage.writeBytes("font", "output.ttf", bytes)

        Pair(fontGlyphs.size, issues)
    }
}
