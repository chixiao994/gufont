package com.gufont.app.ui.step

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gufont.app.data.model.*
import com.gufont.app.data.storage.ProjectStorage
import com.gufont.app.engine.pipeline.FontProjectManager
import com.gufont.app.ui.components.AppBottomBar
import com.gufont.app.ui.dialog.SystemInstallDialog
import com.gufont.app.ui.step.components.FontPreviewView
import com.gufont.app.ui.step.components.InstallDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun StepInstallScreen(rootUri: String, onBack: () -> Unit, onGoStep: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { ProjectStorage(context, Uri.parse(rootUri)) }

    var project by remember { mutableStateOf<Project?>(null) }
    var glyphCount by remember { mutableStateOf(0) }
    var pendingCount by remember { mutableStateOf(0) }
    var fontFile by remember { mutableStateOf<File?>(null) }

    var previewText by remember {
        mutableStateOf(
            "永和九年，岁在癸丑，暮春之初，会于会稽山阴之兰亭。\n" +
                    "天地玄黄，宇宙洪荒。日月盈昃，辰宿列张。\n" +
                    "The quick brown fox jumps over the lazy dog.\n" +
                    "0123456789"
        )
    }
    var fontSizeSp by remember { mutableStateOf(24f) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showInstallDialog by remember { mutableStateOf(false) }
    var showSystemInstall by remember { mutableStateOf(false) }
    var showPendingList by remember { mutableStateOf(false) }
    var building by remember { mutableStateOf(false) }

    // 加载：把 TTF 从 SAF 拷到 cache，供 Typeface 使用
    suspend fun loadFontFile() {
        val uri = storage.openFileUri("font", "output.ttf") ?: run {
            fontFile = null
            return
        }
        val tmp = File(context.cacheDir, "preview_font.ttf")
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { i ->
                    tmp.outputStream().use { o -> i.copyTo(o) }
                }
            }
            fontFile = if (tmp.length() > 12) tmp else null
        } catch (_: Exception) {
            fontFile = null
        }
    }

    suspend fun refresh() {
        try {
            val p = storage.loadProject()
            project = p
            val gi = storage.loadGlyphIndex()
            glyphCount = gi.glyphs.count {
                it.state != GlyphState.SKIPPED && it.codepoint > 0
            }
            pendingCount = gi.glyphs.count {
                it.state != GlyphState.SKIPPED &&
                        (it.issue != null || it.codepoint == 0)
            }
            loadFontFile()
        } catch (_: Exception) { }
    }

    LaunchedEffect(rootUri) {
        refresh()
    }

    // 若字体没生成，自动尝试重建
    LaunchedEffect(fontFile) {
        if (fontFile == null && project != null) {
            building = true
            try {
                val p = storage.loadProject()
                withContext(Dispatchers.IO) {
                    FontProjectManager.rebuildFont(context, storage, p)
                }
                loadFontFile()
            } catch (_: Exception) { }
            building = false
        }
    }

    // 进入时标记步骤完成
    LaunchedEffect(Unit) {
        try {
            val p = storage.loadProject()
            val newSteps = p.steps.toMutableMap()
            newSteps[Step.INSTALL.id] = StepState(completed = true)
            storage.saveProject(p.copy(steps = newSteps))
        } catch (_: Exception) { }
    }

    StepScaffold(
        rootUri = rootUri,
        step = Step.INSTALL,
        onBack = onBack,
        onGoStep = onGoStep,
        resetEnabled = false,
        previewEnabled = true,
        onPreview = {
            if (fontFile != null) showPreviewDialog = true
        },
        bottomBar = {
            // 字体工具底栏三按钮都变成翻页
            AppBottomBar(
                prevEnabled = false,
                skipEnabled = false,
                nextEnabled = false,
                onPrev = { },
                onSkip = { },
                onNext = { },
                nextLabel = "下一张"
            )
        }
    ) { _, _ ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // ========= 信息卡 =========
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    val pr = project
                    Text(
                        pr?.book?.title?.ifBlank { "未命名" } ?: "—",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (!pr?.book?.annotator.isNullOrBlank()) {
                        Text(
                            pr.book.annotator,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!pr?.book?.edition.isNullOrBlank()) {
                        Text(
                            pr.book.edition,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    InfoRow("字形数", "$glyphCount")
                    InfoRow(
                        "字体文件",
                        fontFile?.let { "${it.name}  ${"%.1f".format(it.length() / 1024.0)} KB" }
                            ?: (if (building) "生成中…" else "未生成")
                    )
                }
            }

            // ========= 待处理 =========
            if (pendingCount > 0) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showPendingList = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "待处理 $pendingCount 个",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "点击查看并修复",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // ========= 字号滑块 =========
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("字号", fontSize = 12.sp)
                Slider(
                    value = fontSizeSp,
                    onValueChange = { fontSizeSp = it },
                    valueRange = 12f..60f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("${fontSizeSp.toInt()}", fontSize = 12.sp, modifier = Modifier.width(28.dp))
            }

            // ========= 预览文本输入 =========
            OutlinedTextField(
                value = previewText,
                onValueChange = { previewText = it },
                label = { Text("预览文本") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            // ========= 预览区 =========
            Box(Modifier.weight(1f).fillMaxWidth()) {
                FontPreviewView(
                    fontFile = fontFile,
                    text = previewText,
                    fontSizeSp = fontSizeSp,
                    modifier = Modifier.fillMaxSize()
                )
                if (building) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x80000000)),
                        Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // ========= 安装按钮 =========
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showInstallDialog = true },
                    enabled = fontFile != null,
                    modifier = Modifier.weight(1f)
                ) { Text("安装字体") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            building = true
                            try {
                                val p = storage.loadProject()
                                withContext(Dispatchers.IO) {
                                    FontProjectManager.rebuildFont(context, storage, p)
                                }
                                refresh()
                            } catch (_: Exception) { }
                            building = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("重新生成") }
            }
        }
    }

    if (showPreviewDialog && fontFile != null) {
        Dialog(
            onDismissRequest = { showPreviewDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0xFFFAF6EE))
                    .clickable { showPreviewDialog = false }
                    .padding(16.dp)
            ) {
                FontPreviewView(
                    fontFile = fontFile,
                    text = previewText,
                    fontSizeSp = fontSizeSp * 1.3f,
                    modifier = Modifier.fillMaxSize()
                )
                Text(
                    "点击任意处关闭",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    if (showInstallDialog && fontFile != null) {
        InstallDialog(
            fontFile = fontFile,
            onDismiss = { showInstallDialog = false },
            onSystemInstall = {
                showInstallDialog = false
                showSystemInstall = true
            }
        )
    }

    if (showSystemInstall && fontFile != null) {
        SystemInstallDialog(
            fontFile = fontFile,
            onDismiss = { showSystemInstall = false }
        )
    }

    if (showPendingList) {
        Dialog(
            onDismissRequest = { showPendingList = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                PendingListScreen(
                    rootUri = rootUri,
                    onBack = { showPendingList = false },
                    onEditGlyph = { code ->
                        showPendingList = false
                        onGoStep(Step.EDIT.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            k,
            modifier = Modifier.width(72.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
