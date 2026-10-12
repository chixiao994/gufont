package com.gufont.app.ui.step.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * 放大镜组件：给定图像位图、图像在画布上的位置和缩放、手指位置，
 * 在手指左上角画一个 3 倍放大的圆形预览。
 */
@Composable
fun MagnifierCanvas(
    image: ImageBitmap,
    imageRectOnCanvas: Rect,
    imageScale: Float,
    touchPos: Offset,
    canvasSize: IntSize,
    modifier: Modifier = Modifier,
    magnifierSizeDp: Int = 140,
    magnifyFactor: Float = 3f,
    gapDp: Int = 70
) {
    val magSizePx = with(androidx.compose.ui.platform.LocalDensity.current) {
        magnifierSizeDp.dp.toPx()
    }
    val gapPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        gapDp.dp.toPx()
    }
    val magR = magSizePx / 2f

    Canvas(modifier = modifier.fillMaxSize()) {
        // 触摸点十字/圆点
        drawCircle(Color(0xFFFF2222), radius = 4f, center = touchPos)
        drawCircle(Color.White, radius = 2f, center = touchPos)

        // 放大镜位置：左上角，越界贴边
        val desiredCx = touchPos.x - gapPx - magR
        val desiredCy = touchPos.y - gapPx - magR
        val maxCx = (size.width - magR).coerceAtLeast(magR)
        val maxCy = (size.height - magR).coerceAtLeast(magR)
        val magCx = desiredCx.coerceIn(magR, maxCx)
        val magCy = desiredCy.coerceIn(magR, maxCy)

        val magLeft = magCx - magR
        val magTop = magCy - magR

        val circlePath = Path().apply {
            addOval(Rect(magLeft, magTop, magLeft + magSizePx, magTop + magSizePx))
        }
        clipPath(circlePath) {
            drawRect(color = Color(0xFF14141A))
            val imgPos = Offset(
                (touchPos.x - imageRectOnCanvas.left) / imageScale,
                (touchPos.y - imageRectOnCanvas.top) / imageScale
            )
            val srcW = magSizePx / (imageScale * magnifyFactor)
            val srcLeft = (imgPos.x - srcW / 2f).toInt().coerceAtLeast(0)
            val srcTop = (imgPos.y - srcW / 2f).toInt().coerceAtLeast(0)
            val safeW = srcW.toInt().coerceAtMost(image.width - srcLeft).coerceAtLeast(1)
            val safeH = srcW.toInt().coerceAtMost(image.height - srcTop).coerceAtLeast(1)
            if (safeW > 0 && safeH > 0) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset(srcLeft, srcTop),
                    srcSize = IntSize(safeW, safeH),
                    dstOffset = IntOffset(magLeft.toInt(), magTop.toInt()),
                    dstSize = IntSize(magSizePx.toInt(), magSizePx.toInt()),
                    filterQuality = FilterQuality.None
                )
            }
        }
        drawCircle(
            color = Color.White,
            radius = magR - 1f,
            center = Offset(magCx, magCy),
            style = Stroke(width = 2f)
        )
    }
}
