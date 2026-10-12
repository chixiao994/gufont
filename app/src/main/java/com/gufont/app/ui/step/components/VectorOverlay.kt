package com.gufont.app.ui.step.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import com.gufont.app.engine.potrace.PotracePath
import com.gufont.app.engine.potrace.SegmentTag

@Composable
fun VectorOverlay(
    paths: List<PotracePath>,
    imageWidth: Int,
    imageHeight: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.fillMaxSize()) {
        if (paths.isEmpty()) return@Canvas
        val combined = Path().apply { fillType = PathFillType.EvenOdd }

        for (p in paths) {
            val segs = if (p.optimizedSegments.isNotEmpty())
                p.optimizedSegments else p.curveSegments
            if (segs.isEmpty()) continue
            val first = segs.first()
            val start = if (first.tag == SegmentTag.CORNER) first.c[1] else first.c[0]
            combined.moveTo(
                offsetX + start.x.toFloat() * scale,
                offsetY + start.y.toFloat() * scale
            )
            for (seg in segs) {
                when (seg.tag) {
                    SegmentTag.CORNER -> {
                        val v = seg.c[1]
                        combined.lineTo(
                            offsetX + v.x.toFloat() * scale,
                            offsetY + v.y.toFloat() * scale
                        )
                    }
                    SegmentTag.CURVETO -> {
                        val ctrl = seg.c[1]; val end = seg.c[2]
                        combined.quadraticTo(
                            offsetX + ctrl.x.toFloat() * scale,
                            offsetY + ctrl.y.toFloat() * scale,
                            offsetX + end.x.toFloat() * scale,
                            offsetY + end.y.toFloat() * scale
                        )
                    }
                }
            }
            combined.close()
        }
        drawPath(
            combined,
            Color(0xFFFF4081),
            style = Stroke(width = 3f)
        )
    }
}
