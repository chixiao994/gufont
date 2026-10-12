package com.gufont.app.ui.step.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.min

@Composable
fun ColumnEditView(
    bitmap: Bitmap?,
    columnDividers: List<Float>,
    onDividersChange: (List<Float>) -> Unit,
    markMode: Boolean = false,
    droppedColumns: Set<Int> = emptySet(),
    onToggleColumn: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    var dragIndex by remember { mutableStateOf(-1) }
    var downPos by remember { mutableStateOf<Offset?>(null) }
    var downTime by remember { mutableStateOf(0L) }

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

        val imgBitmap = remember(bitmap) { bitmap?.asImageBitmap() }

        if (imgBitmap == null || imageRect == null || bitmap == null) {
            Canvas(Modifier.fillMaxSize()) { }
            return@BoxWithConstraints
        }

        val columnCount = columnDividers.size + 1

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(bitmap, imageRect, columnDividers, markMode) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            downPos = pos
                            downTime = System.currentTimeMillis()
                            if (!markMode && imageRect.contains(pos)) {
                                val nearest = columnDividers.indices.minByOrNull { i ->
                                    val x = imageRect.left +
                                            columnDividers[i] * imageRect.width
                                    abs(x - pos.x)
                                }
                                if (nearest != null) {
                                    val x = imageRect.left +
                                            columnDividers[nearest] * imageRect.width
                                    if (abs(x - pos.x) < 60f) {
                                        dragIndex = nearest
                                        touchPos = pos
                                    }
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            if (!markMode && dragIndex >= 0) {
                                val pos = change.position
                                touchPos = pos
                                val nx = ((pos.x - imageRect.left) / imageRect.width)
                                    .coerceIn(0.01f, 0.99f)
                                val newList = columnDividers.toMutableList()
                                val lo = if (dragIndex > 0)
                                    newList[dragIndex - 1] + 0.005f else 0.01f
                                val hi = if (dragIndex < newList.size - 1)
                                    newList[dragIndex + 1] - 0.005f else 0.99f
                                newList[dragIndex] = nx.coerceIn(lo, hi)
                                onDividersChange(newList)
                            }
                        },
                        onDragEnd = {
                            if (markMode && downPos != null &&
                                System.currentTimeMillis() - downTime < 300
                            ) {
                                val pos = downPos!!
                                if (imageRect.contains(pos)) {
                                    val nx = (pos.x - imageRect.left) / imageRect.width
                                    var colIdx = 0
                                    for (i in columnDividers.indices) {
                                        if (nx < columnDividers[i]) break
                                        colIdx = i + 1
                                    }
                                    onToggleColumn(colIdx)
                                }
                            }
                            dragIndex = -1
                            touchPos = null
                            downPos = null
                        },
                        onDragCancel = {
                            dragIndex = -1
                            touchPos = null
                            downPos = null
                        }
                    )
                }
        ) {
            drawImage(
                image = imgBitmap,
                dstOffset = IntOffset(imageRect.left.toInt(), imageRect.top.toInt()),
                dstSize = IntSize(imageRect.width.toInt(), imageRect.height.toInt())
            )

            if (markMode) {
                val boundaries = listOf(0f) + columnDividers + listOf(1f)
                for (i in 0 until boundaries.size - 1) {
                    val x0 = imageRect.left + boundaries[i] * imageRect.width
                    val x1 = imageRect.left + boundaries[i + 1] * imageRect.width
                    if (droppedColumns.contains(i)) {
                        drawRect(
                            color = Color(0x88000000),
                            topLeft = Offset(x0, imageRect.top),
                            size = Size(x1 - x0, imageRect.height)
                        )
                    }
                }
            }

            columnDividers.forEachIndexed { i, nd ->
                val x = imageRect.left + nd * imageRect.width
                val isDragging = i == dragIndex
                drawLine(
                    color = if (isDragging) Color(0xFFFFB300) else Color(0xFF00E676),
                    start = Offset(x, imageRect.top),
                    end = Offset(x, imageRect.bottom),
                    strokeWidth = if (isDragging) 6f else 4f
                )
                drawCircle(Color.White, radius = 12f,
                    center = Offset(x, imageRect.top))
                drawCircle(Color.White, radius = 12f,
                    center = Offset(x, imageRect.bottom))
            }

            val boundaries = listOf(0f) + columnDividers + listOf(1f)
            for (i in 0 until boundaries.size - 1) {
                val xc = imageRect.left +
                        (boundaries[i] + boundaries[i + 1]) / 2f * imageRect.width
                drawContext.canvas.nativeCanvas.drawText(
                    "${columnCount - i}",
                    xc, imageRect.top + 30f,
                    android.graphics.Paint().apply {
                        color = 0xFFFFEB3B.toInt()
                        textSize = 32f
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }

        val tp = touchPos
        if (tp != null && dragIndex >= 0) {
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
