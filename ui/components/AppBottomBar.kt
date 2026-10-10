package com.gufont.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全局统一底栏：上一张 · 跳过 · 下一张
 *
 * 语义随场景变：
 *  - 页/列/字级：保存前进
 *  - 字体预览：都是翻页
 */
@Composable
fun AppBottomBar(
    prevEnabled: Boolean = true,
    skipEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    onPrev: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    nextLabel: String = "下一张"
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp)
        ) {
            BottomText("上一张", prevEnabled, onPrev, Modifier.weight(1f))
            BottomText("跳过", skipEnabled, onSkip, Modifier.weight(1f))
            BottomText(nextLabel, nextEnabled, onNext, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomText(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
