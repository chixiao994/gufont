package com.gufont.app.ui.step.components

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream

/**
 * 应用内安装：把 TTF 复制到 filesDir/fonts/ 供应用内使用。
 * 系统安装：交给 SystemInstallDialog。
 */
@Composable
fun InstallDialog(
    fontFile: File?,
    onDismiss: () -> Unit,
    onSystemInstall: (File) -> Unit
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("安装字体") },
        text = {
            Column {
                if (fontFile == null || !fontFile.exists()) {
                    Text("字体文件不存在，请先在编辑步骤处理一些字。")
                    return@Column
                }
                Text("字体：${fontFile.name}")
                Text(
                    "大小：${"%.1f".format(fontFile.length() / 1024.0)} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "「应用内安装」仅本 App 预览用，无需 root。\n" +
                            "「系统安装」写入系统，需要 root，重启生效。",
                    style = MaterialTheme.typography.bodySmall
                )
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fontFile != null) {
                        message = installInApp(context, fontFile)
                    }
                },
                enabled = fontFile != null
            ) { Text("应用内安装") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        if (fontFile != null) onSystemInstall(fontFile)
                    },
                    enabled = fontFile != null
                ) { Text("系统安装") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

private fun installInApp(context: Context, src: File): String = try {
    val dir = File(context.filesDir, "fonts")
    if (!dir.exists()) dir.mkdirs()
    dir.listFiles()?.forEach { it.delete() }
    val dst = File(dir, src.name)
    src.inputStream().use { i ->
        FileOutputStream(dst).use { o -> i.copyTo(o) }
    }
    "已安装到应用内：${dst.name}"
} catch (e: Exception) {
    "安装失败：${e.message}"
}
