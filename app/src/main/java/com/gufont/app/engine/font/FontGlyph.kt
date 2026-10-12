package com.gufont.app.engine.font

data class GlyphPoint(val x: Float, val y: Float, val onCurve: Boolean)

data class FontGlyph(
    val codepoint: Int,
    val advanceWidth: Int,
    val contours: List<List<GlyphPoint>>
)
