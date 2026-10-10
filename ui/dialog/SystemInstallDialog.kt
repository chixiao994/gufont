package com.gufont.app.ui.dialog

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gufont.app.util.SystemFontInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SystemInstallDialog(
    fontFile: File,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SystemFontInstaller.Result?>(null) }

    AlertDialog(
        onDismissRequest = { if (!installing) onDismiss() },
        title = {
            Text(when {
                installing -> "正在安装…"
                result?.ok == true -> "安装成功"
                result != null -> "安装失败"
                else -> "安装为系统字体"
            })
        },
        text = {
            when {
                installing -> {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("请在弹出的 root 授权请求中点「允许」。")
                    }
                }
                result != null -> Text(result!!.message)
                else -> Text(
                    "此操作会：\n" +
                            "• 请求 root 权限\n" +
                            "• 把字体写入 /system/fonts/\n" +
                            "• 备份并替换系统默认字体\n\n" +
                            "完成后需重启手机生效。\n\n" +
                            "⚠️ 修改系统分区有风险。"
                )
            }
        },
        confirmButton = {
            when {
                installing -> {}
                result != null -> {
                    TextButton(onClick = onDismiss) { Text("知道了") }
                }
                else -> {
                    TextButton(onClick = {
                        installing = true
                        scope.launch {
                            val r = withContext(Dispatchers.IO) {
                                SystemFontInstaller.install(
                                    context, fontFile, fontFile.name
                                )
                            }
                            installing = false
                            result = r
                        }
                    }) { Text("继续") }
                }
            }
        },
        dismissButton = {
            if (!installing && result == null) {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
