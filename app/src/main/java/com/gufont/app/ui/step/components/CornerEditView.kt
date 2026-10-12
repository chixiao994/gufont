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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/**
 * 4 点校正编辑器：显示原图 + 4 个可拖角点。
 * 手指拖动角点时，实时更新角点位置；拖动时显示放大镜。
 *
 * corners 为归一化坐标 0~1（左上、右上、右下、左下），onChange 回调新值。
 */
@Composable
fun CornerEditView(
    bitmap: Bitmap?,
    corners: List<Offset>,
    onCornersChange: (List<Offset>) -> Unit,
    modifier: Modifier = Modifier
) {
    var touchPos by remember { mutableStateOf<Offset?>(null) }
    var dragIndex by remember { mutableStateOf(-1) }

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

        if (imgBitmap == null || imageRect == null) {
            Canvas(Modifier.fillMaxSize()) { }
            return@BoxWithConstraints
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(bitmap, imageRect) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            if (imageRect.contains(pos)) {
                                // 找最近的角点
                                val nearest = corners.indices.minByOrNull { i ->
                                    val p = Offset(
                                        imageRect.left + corners[i].x * imageRect.width,
                                        imageRect.top + corners[i].y * imageRect.height
                                    )
                                    (p - pos).getDistance()
                                }
                                if (nearest != null && (Offset(
                                        imageRect.left + corners[nearest].x * imageRect.width,
                                        imageRect.top + corners[nearest].y * imageRect.height
                                    ) - pos).getDistance() < 80f
                                ) {
                                    dragIndex = nearest
                                    touchPos = pos
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            val i = dragIndex
                            if (i >= 0) {
                                val pos = change.position
                                touchPos = pos
                                val nx = ((pos.x - imageRect.left) / imageRect.width).coerceIn(0f, 1f)
                                val ny = ((pos.y - imageRect.top) / imageRect.height).coerceIn(0f, 1f)
                                val newCorners = corners.toMutableList()
                                newCorners[i] = Offset(nx, ny)
                                onCornersChange(newCorners)
                            }
                        },
                        onDragEnd = { dragIndex = -1; touchPos = null },
                        onDragCancel = { dragIndex = -1; touchPos = null }
                    )
                }
        ) {
            drawImage(
                image = imgBitmap,
                dstOffset = IntOffset(imageRect.left.toInt(), imageRect.top.toInt()),
                dstSize = IntSize(imageRect.width.toInt(), imageRect.height.toInt())
            )

            // 四边形
            if (corners.size == 4) {
                val screenPts = corners.map {
                    Offset(
                        imageRect.left + it.x * imageRect.width,
                        imageRect.top + it.y * imageRect.height
                    )
                }
                val path = Path().apply {
                    moveTo(screenPts[0].x, screenPts[0].y)
                    for (i in 1..3) lineTo(screenPts[i].x, screenPts[i].y)
                    close()
                }
                drawPath(path, Color(0xFF00E676), style = Stroke(width = 4f))

                screenPts.forEach { p ->
                    drawCircle(Color(0x66FF0000), radius = 26f, center = p)
                    drawCircle(Color.Red, radius = 15f, center = p)
                    drawCircle(Color.White, radius = 7f, center = p)
                }
            }
        }

        // 放大镜
        val tp = touchPos
        if (tp != null && dragIndex >= 0 && imgBitmap != null) {
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
