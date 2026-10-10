package com.gufont.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全局统一顶栏：[☰]  [文件]  [重置]  [预览]
 *
 * 所有页面使用相同结构。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String? = null,
    onMenuClick: () -> Unit,
    onFileClick: () -> Unit,
    onResetClick: () -> Unit,
    onPreviewClick: () -> Unit,
    resetEnabled: Boolean = true,
    previewEnabled: Boolean = true
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "步骤")
                }
                TopBarText("文件", true, onFileClick, Modifier.weight(1f))
                TopBarText("重置", resetEnabled, onResetClick, Modifier.weight(1f))
                TopBarText("预览", previewEnabled, onPreviewClick, Modifier.weight(1f))
            }
            if (title != null) {
                Text(
                    title,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun TopBarText(
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
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
