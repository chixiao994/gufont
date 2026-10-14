package com.gufont.app.ui.step

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gufont.app.data.model.Step
import com.gufont.app.data.model.StepState
import com.gufont.app.data.storage.ProjectStorage
import com.gufont.app.engine.pdf.PdfToImageEngine
import kotlinx.coroutines.launch

@Composable
fun StepImportScreen(rootUri: String, onBack: () -> Unit, onGoStep: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { ProjectStorage(context, Uri.parse(rootUri)) }

    var busy by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var progressValue by remember { mutableStateOf(0f) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // ★ 进入时读项目状态，若已完成则直接显示"已完成"
    LaunchedEffect(rootUri) {
        try {
            val p = storage.loadProject()
            val st = p.steps[Step.IMPORT.id]
            if (st?.completed == true && st.total > 0) {
                done = true
                progressText = "已完成：${st.done} 页"
                progressValue = 1f
            }
        } catch (_: Exception) { }
    }

    val pickPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = null
        progressText = "准备中…"
        scope.launch {
            try {
                val total = PdfToImageEngine.convert(
                    context = context,
                    pdfUri = uri,
                    storage = storage,
                    dpi = PdfToImageEngine.DEFAULT_DPI
                ) { d, t, name ->
                    progressText = "转换 $d/$t · $name"
                    progressValue = d.toFloat() / t
                }
                val proj = storage.loadProject()
                val newSteps = proj.steps.toMutableMap()
                newSteps[Step.IMPORT.id] = StepState(
                    completed = true, done = total, total = total
                )
                storage.saveProject(proj.copy(steps = newSteps, lastStep = Step.IMPORT.id))
                done = true
                progressText = "完成：$total 页"
                progressValue = 1f
            } catch (e: Exception) {
                error = "转换失败：${e.message}"
            } finally {
                busy = false
            }
        }
    }

    StepScaffold(
        rootUri = rootUri,
        step = Step.IMPORT,
        onBack = onBack,
        onGoStep = onGoStep,
        resetEnabled = false,
        previewEnabled = false,
        onRefresh = {
            // 重新读取导入状态
            try {
                val p = storage.loadProject()
                val st = p.steps[Step.IMPORT.id]
                if (st?.completed == true && st.total > 0) {
                    done = true
                    progressText = "已完成：${st.done} 页"
                    progressValue = 1f
                } else {
                    done = false
                }
            } catch (_: Exception) { }
        },
        bottomBar = {
            if (done) {
                ImportBottomBar(onNext = { onGoStep(Step.CORRECT.id) })
            } else {
                DisabledBottomBar()
            }
        }
    ) { _, _ ->
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("导入 PDF", style = MaterialTheme.typography.titleLarge)

                if (!busy && !done) {
                    Button(
                        onClick = { pickPdf.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) { Text("选择 PDF 文件") }

                    Text(
                        "提示：一页 PDF 转为一张图像，300 DPI。\n" +
                                "若已有图像，可稍后在项目目录里手动放入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (busy) {
                    CircularProgressIndicator()
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier.fillMaxWidth(0.7f)
                    )
                    Text(progressText, style = MaterialTheme.typography.bodySmall)
                }

                if (done) {
                    Text(
                        progressText,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "点击「下一张」进入校正分列",
                        color = MaterialTheme.colorScheme.outline,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { pickPdf.launch(arrayOf("application/pdf")) }
                    ) { Text("重新导入") }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ImportBottomBar(onNext: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp)
        ) {
            TextButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                Text("上一张")
            }
            TextButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                Text("跳过")
            }
            TextButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                Text("下一张", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DisabledBottomBar() {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(56.dp)
        ) {
            listOf("上一张", "跳过", "下一张").forEach {
                TextButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                    Text(it)
                }
            }
        }
    }
}
