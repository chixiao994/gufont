package com.gufont.app.ui.step.components

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 用给定的 TTF 文件渲染一段文本。
 * 支持滚动查看长文本。
 */
@Composable
fun FontPreviewView(
    fontFile: File?,
    text: String,
    fontSizeSp: Float = 24f,
    lineHeightMultiplier: Float = 1.5f,
    letterSpacingEm: Float = 0f,
    modifier: Modifier = Modifier
) {
    val fontFamily: FontFamily? = remember(fontFile) {
        try {
            if (fontFile != null && fontFile.exists()) {
                FontFamily(Font(fontFile, FontWeight.Normal))
            } else null
        } catch (_: Exception) { null }
    }

    val style = TextStyle(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeightMultiplier).sp,
        letterSpacing = letterSpacingEm.sp
    )

    Surface(
        modifier = modifier,
        color = Color(0xFFFAF6EE)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (fontFamily != null) {
                Text(
                    text = text,
                    style = style,
                    fontFamily = fontFamily,
                    color = Color(0xFF1F1F1F)
                )
            } else {
                Text(
                    "（字体尚未生成）",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
