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
fun SplitCanvas(
    columnBitmap: Bitmap?,
    dividers: List<Float>,
    onDividersChange: (List<Float>) -> Unit,
    markMode: Boolean,
    droppedRows: Set<Int>,
    onToggleRow: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    var dragIndex by remember { mutableStateOf(-1) }
    var downPos by remember { mutableStateOf<Offset?>(null) }
    var downTime by remember { mutableStateOf(0L) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()

        val imgRect: Rect? = remember(columnBitmap, cw, ch) {
            columnBitmap?.let {
                val maxH = ch * 0.88f
                val maxW = cw * 0.72f
                val s = min(maxH / it.height, maxW / it.width)
                val dw = it.width * s
                val dh = it.height * s
                Rect((cw - dw) / 2f, (ch - dh) / 2f, (cw + dw) / 2f, (ch + dh) / 2f)
            }
        }

        val imgBitmap = remember(columnBitmap) { columnBitmap?.asImageBitmap() }

        if (imgBitmap == null || imgRect == null ||
            columnBitmap == null || dividers.isEmpty()
        ) {
            Canvas(Modifier.fillMaxSize()) { }
            return@BoxWithConstraints
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(columnBitmap, imgRect, dividers, markMode) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            downPos = pos
                            downTime = System.currentTimeMillis()
                            if (!markMode) {
                                val nearest = dividers.indices.minByOrNull { i ->
                                    val y = imgRect.top + dividers[i] * imgRect.height
                                    abs(y - pos.y)
                                }
                                if (nearest != null) {
                                    val y = imgRect.top +
                                            dividers[nearest] * imgRect.height
                                    if (abs(y - pos.y) < 60f) {
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
                                val ny = ((pos.y - imgRect.top) / imgRect.height)
                                    .coerceIn(0.005f, 0.995f)
                                val newList = dividers.toMutableList()
                                val lo = if (dragIndex > 0)
                                    newList[dragIndex - 1] + 0.005f else 0.01f
                                val hi = if (dragIndex < newList.size - 1)
                                    newList[dragIndex + 1] - 0.005f else 0.99f
                                newList[dragIndex] = ny.coerceIn(lo, hi)
                                onDividersChange(newList)
                            }
                        },
                        onDragEnd = {
                            if (markMode && downPos != null &&
                                System.currentTimeMillis() - downTime < 300
                            ) {
                                val pos = downPos!!
                                if (imgRect.contains(pos)) {
                                    val ny = (pos.y - imgRect.top) / imgRect.height
                                    var rowIdx = 0
                                    for (i in dividers.indices) {
                                        if (ny < dividers[i]) break
                                        rowIdx = i + 1
                                    }
                                    onToggleRow(rowIdx)
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
                dstOffset = IntOffset(imgRect.left.toInt(), imgRect.top.toInt()),
                dstSize = IntSize(imgRect.width.toInt(), imgRect.height.toInt())
            )

            if (markMode) {
                val boundaries = listOf(0f) + dividers + listOf(1f)
                for (i in 0 until boundaries.size - 1) {
                    if (droppedRows.contains(i)) {
                        val y0 = imgRect.top + boundaries[i] * imgRect.height
                        val y1 = imgRect.top + boundaries[i + 1] * imgRect.height
                        drawRect(
                            color = Color(0x88000000),
                            topLeft = Offset(imgRect.left, y0),
                            size = Size(imgRect.width, y1 - y0)
                        )
                    }
                }
            }

            val extend = 30f
            dividers.forEachIndexed { i, nd ->
                val y = imgRect.top + nd * imgRect.height
                val isDragging = i == dragIndex
                drawLine(
                    color = if (isDragging) Color(0xFFFFB300) else Color(0xFF00E676),
                    start = Offset(imgRect.left - extend, y),
                    end = Offset(imgRect.right + extend, y),
                    strokeWidth = if (isDragging) 6f else 4f
                )
                drawCircle(Color.White, radius = 10f,
                    center = Offset(imgRect.left, y))
                drawCircle(Color.White, radius = 10f,
                    center = Offset(imgRect.right, y))
            }

            val boundaries = listOf(0f) + dividers + listOf(1f)
            for (i in 0 until boundaries.size - 1) {
                val yc = imgRect.top +
                        (boundaries[i] + boundaries[i + 1]) / 2f * imgRect.height
                drawContext.canvas.nativeCanvas.drawText(
                    "${i + 1}",
                    imgRect.left + 16f, yc + 10f,
                    android.graphics.Paint().apply {
                        color = 0xFFFFEB3B.toInt()
                        textSize = 28f
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.LEFT
                    }
                )
            }
        }

        val tp = touchPos
        if (tp != null && dragIndex >= 0) {
            MagnifierCanvas(
                image = imgBitmap,
                imageRectOnCanvas = imgRect,
                imageScale = imgRect.height / columnBitmap.height,
                touchPos = tp,
                canvasSize = IntSize(cw.toInt(), ch.toInt())
            )
        }
    }
}
