package com.gufont.app.ui.step.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

@Composable
fun BrushCanvas(
    bitmap: Bitmap?,
    version: Int,
    brushColor: Color,
    brushSizeDp: Float,
    onModified: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()

        val imageRect: Rect? = remember(bitmap, cw, ch) {
            bitmap?.let {
                val s = min(cw / it.width, ch / it.height)
                val dw = it.width * s
                val dh = it.height * s
                Rect((cw - dw) / 2f, (ch - dh) / 2f, (cw + dw) / 2f, (ch + dh) / 2f)
            }
        }

        val imgBitmap = remember(bitmap, version) { bitmap?.asImageBitmap() }

        if (imgBitmap == null || imageRect == null || bitmap == null) {
            Canvas(Modifier.fillMaxSize()) { }
            return@BoxWithConstraints
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(bitmap, imageRect, brushColor, brushSizeDp) {
                    val bmp = bitmap ?: return@pointerInput
                    val scale = imageRect.width / bmp.width
                    val brushPx = brushSizeDp * density.density / scale
                    var last: Offset? = null

                    fun toImg(p: Offset) = Offset(
                        (p.x - imageRect.left) / scale,
                        (p.y - imageRect.top) / scale
                    )

                    fun stroke(from: Offset, to: Offset) {
                        val c = AndroidCanvas(bmp)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = brushPx.coerceAtLeast(1f)
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                            color = brushColor.toArgb()
                        }
                        c.drawLine(from.x, from.y, to.x, to.y, paint)
                    }

                    detectDragGestures(
                        onDragStart = { pos ->
                            touchPos = pos
                            val pt = toImg(pos)
                            last = pt
                            stroke(pt, pt)
                            onModified(bmp)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            touchPos = change.position
                            val pt = toImg(change.position)
                            val prev = last ?: pt
                            stroke(prev, pt)
                            last = pt
                            onModified(bmp)
                        },
                        onDragEnd = { touchPos = null; last = null },
                        onDragCancel = { touchPos = null; last = null }
                    )
                }
        ) {
            drawImage(
                image = imgBitmap,
                dstOffset = IntOffset(imageRect.left.toInt(), imageRect.top.toInt()),
                dstSize = IntSize(imageRect.width.toInt(), imageRect.height.toInt())
            )

            touchPos?.let { p ->
                val brushPxScreen = brushSizeDp * density.density
                drawCircle(
                    color = brushColor.copy(alpha = 0.45f),
                    radius = brushPxScreen / 2f,
                    center = p
                )
            }
        }

        val tp = touchPos
        if (tp != null) {
            MagnifierCanvas(
                image = imgBitmap,
                imageRectOnCanvas = imageRect,
                imageScale = imageRect.width / bitmap.width,
                touchPos = tp,
                canvasSize = IntSize(cw.toInt(), ch.toInt())
            )
        }
    }
}
