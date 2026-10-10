package com.gufont.app.engine.potrace

import kotlin.math.sqrt

data class IntPoint(val x: Int, val y: Int)

data class DPoint(val x: Double, val y: Double) {
    operator fun minus(o: DPoint) = DPoint(x - o.x, y - o.y)
    operator fun plus(o: DPoint) = DPoint(x + o.x, y + o.y)
    operator fun times(k: Double) = DPoint(x * k, y * k)
    fun dot(o: DPoint) = x * o.x + y * o.y
    fun cross(o: DPoint) = x * o.y - y * o.x
    fun length() = sqrt(x * x + y * y)
    fun distanceTo(o: DPoint) = (this - o).length()
}

enum class SegmentTag { CORNER, CURVETO }

data class CurveSegment(
    val tag: SegmentTag,
    val c: Array<DPoint>,
    val alpha: Double = 1.0
)

class PotracePath(val points: List<IntPoint>) {
    var area = 0L
    var optimalPolygon: List<Int> = emptyList()
    var curveSegments: List<CurveSegment> = emptyList()
    var optimizedSegments: List<CurveSegment> = emptyList()
}

data class PotraceParams(
    val turdSize: Int = 8,
    val alphaMax: Double = 1.0,
    val optimizeCurve: Boolean = true,
    val optTolerance: Double = 0.2,
    val minComponentSize: Int = 20,
    val maxHoleSize: Int = 30,
    val rdpEpsilon: Double = 0.8,
    val cornerAngleDeg: Double = 60.0
)
