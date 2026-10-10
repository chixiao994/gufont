package com.gufont.app.ui.step

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gufont.app.data.model.*
import com.gufont.app.data.storage.ProjectStorage
import com.gufont.app.engine.image.BitmapIO
import com.gufont.app.engine.pipeline.FontProjectManager
import com.gufont.app.engine.potrace.PotraceParams
import com.gufont.app.engine.potrace.Potrace
import com.gufont.app.ui.components.AppBottomBar
import com.gufont.app.ui.step.components.BrushCanvas
import com.gufont.app.ui.step.components.VectorOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StepEditScreen(rootUri: String, onBack: () -> Unit, onGoStep: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { ProjectStorage(context, Uri.parse(rootUri)) }

    var project by remember { mutableStateOf<Project?>(null) }
    var glyphs by remember { mutableStateOf<List<Glyph>>(emptyList()) }
    var index by remember { mutableStateOf(0) }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bitmapVersion by remember { mutableIntStateOf(0) }
    var modified by remember { mutableStateOf(false) }
    var eraser by remember { mutableStateOf(true) }  // 默认擦除
    var brushSizeDp by remember { mutableStateOf(16f) }

    // 命名
    var nameText by remember { mutableStateOf("") }

    // 二值化参数
    var binMode by remember { mutableStateOf("otsu") }
    var binThreshold by remember { mutableFloatStateOf(128f) }
    var binInvert by remember { mutableStateOf(false) }

    // 参数栏 Tab
    var paramTab by remember { mutableStateOf(0) }  // 0=笔刷 1=二值化

    // 预览
    var showPreview by remember { mutableStateOf(false) }
    var showBinDialog by remember { mutableStateOf(false) }

    // 加载
    LaunchedEffect(rootUri) {
        try {
            val p = storage.loadProject()
            project = p
            val gi = storage.loadGlyphIndex()
            glyphs = gi.glyphs.filter { it.state != GlyphState.SKIPPED }
                .sortedBy { it.code }
            index = 0
            // 首次进入，弹二值化确认
            if (!p.steps[Step.EDIT.id]?.completed!! || gi.glyphs.none { it.state == GlyphState.EDITED || it.state == GlyphState.IN_FONT }) {
                showBinDialog = true
            }
        } catch (_: Exception) { }
    }

    // 加载当前字
    LaunchedEffect(glyphs, index) {
        val g = glyphs.getOrNull(index) ?: return@LaunchedEffect
        bitmap?.recycle(); bitmap = null
        withContext(Dispatchers.IO) {
            val fileName = g.edited ?: g.raw
            val uri = storage.openFileUri("glyphs", fileName) ?: return@withContext
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) { null } ?: return@withContext
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext
            bitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
        }
        modified = false
        nameText = if (g.codepoint > 0) {
            try { String(Character.toChars(g.codepoint)) } catch (_: Exception) { "" }
        } else ""
        // 二值化参数
        val bin = g.binaryOverride ?: project?.settings?.toBinarySettings()
        binMode = bin?.mode ?: "otsu"
        binThreshold = (bin?.threshold ?: 128).toFloat()
        binInvert = bin?.invert ?: false
    }

    // 解析命名
    fun parseCodepoint(s: String): Int {
        if (s.isEmpty()) return 0
        // 单字符
        if (s.length == 1 || (s.length == 2 && Character.isHighSurrogate(s[0]))) {
            val cp = s.codePointAt(0)
            if (cp > 0x20 && cp != 0x7F) return cp
        }
        // U+XXXX / 0xXXXX / XXXX
        val hex = s.removePrefix("U+").removePrefix("u+")
            .removePrefix("0x").removePrefix("0X")
        hex.toIntOrNull(16)?.let { if (it in 1..0x10FFFF) return it }
        return 0
    }

    suspend fun saveCurrent(): Boolean {
        val g = glyphs.getOrNull(index) ?: return false
        val bmp = bitmap ?: return false
        val proj = project ?: return false
        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())

        val editedName = "${g.code}_edited.png"
        val bin = BinarySettings(binMode, binThreshold.toInt(), binInvert)

        // 判定是否真的改了
        val newCp = parseCodepoint(nameText)
        val changedBitmap = modified
        val changedBin = bin != (g.binaryOverride ?: proj.settings.toBinarySettings())
        val changedName = newCp != g.codepoint && newCp > 0

        if (!changedBitmap && !changedBin && !changedName) return true

        withContext(Dispatchers.IO) {
            if (changedBitmap) {
                val bytes = BitmapIO.compressPng(bmp)
                storage.writeImage("glyphs", editedName, bytes)
            }
        }

        val newGlyph = g.copy(
            codepoint = if (newCp > 0) newCp else g.codepoint,
            edited = if (changedBitmap) editedName else g.edited,
            manualEdited = g.manualEdited || changedBitmap,
            binaryOverride = if (changedBin) bin else g.binaryOverride,
            state = if (changedBitmap) GlyphState.EDITED else g.state,
            updated = now
        )

        // 更新 glyphs.json
        val gi = storage.loadGlyphIndex()
        val updated = gi.glyphs.map { if (it.code == g.code) newGlyph else it }
        storage.saveGlyphIndex(GlyphIndex(updated))

        // 更新内存
        glyphs = glyphs.toMutableList().also { it[index] = newGlyph }

        // 后台重建字体
        val projNow = storage.loadProject()
        val (ok, issues) = FontProjectManager.rebuildFont(context, storage, projNow)
        // 更新进度
        val proj2 = storage.loadProject()
        val newSteps = proj2.steps.toMutableMap()
        newSteps[Step.EDIT.id] = StepState(
            completed = false,
            done = updated.count { it.state == GlyphState.EDITED || it.state == GlyphState.IN_FONT },
            total = updated.size
        )
        storage.saveProject(proj2.copy(steps = newSteps))
        project = proj2

        return true
    }

    fun goTo(target: Int) {
        if (target < 0) return
        if (target >= glyphs.size) {
            // 完成
            scope.launch {
                try { saveCurrent() } catch (_: Exception) { }
                val proj = storage.loadProject()
                val newSteps = proj.steps.toMutableMap()
                newSteps[Step.EDIT.id] = StepState(
                    completed = true, done = glyphs.size, total = glyphs.size
                )
                storage.saveProject(proj.copy(steps = newSteps))
                onGoStep(Step.INSTALL.id)
            }
            return
        }
        scope.launch {
            try { saveCurrent() } catch (_: Exception) { }
            index = target
        }
    }

    StepScaffold(
        rootUri = rootUri,
        step = Step.EDIT,
        onBack = onBack,
        onGoStep = onGoStep,
        resetEnabled = bitmap != null,
        previewEnabled = bitmap != null,
        onReset = {
            scope.launch {
                val g = glyphs.getOrNull(index) ?: return@launch
                withContext(Dispatchers.IO) {
                    val uri = storage.openFileUri("glyphs", g.raw) ?: return@withContext
                    val bytes = try {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    } catch (_: Exception) { null } ?: return@withContext
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: return@withContext
                    bitmap?.recycle()
                    bitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
                }
                modified = false
                bitmapVersion++
                binMode = project?.settings?.binaryMode ?: "otsu"
                binThreshold = (project?.settings?.binaryThreshold ?: 128).toFloat()
                binInvert = project?.settings?.binaryInvert ?: false
                // 清除覆盖参数 + 删除 edited 文件（可选）
                val gi = storage.loadGlyphIndex()
                val updated = gi.glyphs.map {
                    if (it.code == g.code) it.copy(
                        binaryOverride = null, manualEdited = false
                    ) else it
                }
                storage.saveGlyphIndex(GlyphIndex(updated))
                glyphs = glyphs.toMutableList().also {
                    it[index] = updated.first { x -> x.code == g.code }
                }
            }
        },
        onPreview = { showPreview = true },
        bottomBar = {
            AppBottomBar(
                prevEnabled = index > 0,
                skipEnabled = true,
                nextEnabled = index < glyphs.size,
                onPrev = { goTo(index - 1) },
                onSkip = { goTo(index + 1) },
                onNext = { goTo(index + 1) }
            )
        }
    ) { _, _ ->
        Column(Modifier.fillMaxSize()) {

            // ========== 命名行 ==========
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it.take(20) },
                        label = { Text("命名", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    val cp = parseCodepoint(nameText)
                    Text(
                        if (cp > 0) "U+${cp.toString(16).uppercase()}" else "—",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ========== 参数栏 ==========
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModeTab("笔刷", paramTab == 0) { paramTab = 0 }
                        ModeTab("二值化", paramTab == 1) { paramTab = 1 }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${index + 1}/${glyphs.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        val g = glyphs.getOrNull(index)
                        Text(
                            g?.code ?: "—",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(6.dp))

                    if (paramTab == 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { eraser = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!eraser)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) { Text("补画", fontSize = 13.sp) }
                            Spacer(Modifier.width(6.dp))
                            Button(
                                onClick = { eraser = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (eraser)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) { Text("擦除", fontSize = 13.sp) }
                            Spacer(Modifier.weight(1f))
                            Text("笔刷 ${brushSizeDp.toInt()}", fontSize = 12.sp)
                        }
                        Slider(
                            value = brushSizeDp,
                            onValueChange = { brushSizeDp = it },
                            valueRange = 4f..64f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val modes = listOf("otsu" to "Otsu", "fixed" to "固定", "adaptive" to "自适应")
                            modes.forEach { (v, label) ->
                                Button(
                                    onClick = {
                                        binMode = v
                                        modified = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (binMode == v)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text(label, fontSize = 12.sp) }
                                Spacer(Modifier.width(4.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("反色", fontSize = 12.sp)
                                Switch(
                                    checked = binInvert,
                                    onCheckedChange = {
                                        binInvert = it; modified = true
                                    }
                                )
                            }
                        }
                        if (binMode == "fixed") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("阈值 ${binThreshold.toInt()}", fontSize = 12.sp)
                                Slider(
                                    value = binThreshold,
                                    onValueChange = {
                                        binThreshold = it; modified = true
                                    },
                                    valueRange = 0f..255f,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ========== 画布 ==========
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF202020))) {
                BrushCanvas(
                    bitmap = bitmap,
                    version = bitmapVersion,
                    brushColor = if (eraser) Color.White else Color.Black,
                    brushSizeDp = brushSizeDp,
                    onModified = {
                        modified = true
                        bitmapVersion++
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // 预览
    if (showPreview) {
        val bmp = bitmap
        if (bmp != null) {
            Dialog(
                onDismissRequest = { showPreview = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black)
                        .clickable { showPreview = false },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        } else showPreview = false
    }

    // 首次进入弹二值化参数
    if (showBinDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("二值化默认参数") },
            text = {
                Column {
                    Text("所有字块的默认二值化参数，之后可在编辑中单独调整。")
                    Spacer(Modifier.height(8.dp))
                    Row {
                        listOf("otsu" to "Otsu", "fixed" to "固定", "adaptive" to "自适应").forEach {
                            (v, label) ->
                            RadioButton(
                                selected = binMode == v,
                                onClick = { binMode = v }
                            )
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val proj = storage.loadProject()
                        val newSettings = proj.settings.copy(
                            binaryMode = binMode,
                            binaryThreshold = binThreshold.toInt(),
                            binaryInvert = binInvert
                        )
                        storage.saveProject(proj.copy(settings = newSettings))
                        project = proj.copy(settings = newSettings)
                    }
                    showBinDialog = false
                }) { Text("确定") }
            }
        )
    }
}

@Composable
private fun ModeTab(label: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    ) { Text(label, fontSize = 13.sp) }
}
