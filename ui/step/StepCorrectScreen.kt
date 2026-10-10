package com.gufont.app.ui.step

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gufont.app.data.model.*
import com.gufont.app.data.storage.ProjectStorage
import com.gufont.app.engine.image.BitmapIO
import com.gufont.app.engine.image.PerspectiveEngine
import com.gufont.app.engine.pdf.PdfToImageEngine
import com.gufont.app.ui.components.AppBottomBar
import com.gufont.app.ui.step.components.ColumnEditView
import com.gufont.app.ui.step.components.CornerEditView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 视图模式：拖角点 / 拖竖线 */
private enum class SubMode { CORNER, COLUMN }

@Composable
fun StepCorrectScreen(rootUri: String, onBack: () -> Unit, onGoStep: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { ProjectStorage(context, Uri.parse(rootUri)) }

    var project by remember { mutableStateOf<Project?>(null) }
    var pages by remember { mutableStateOf<List<Page>>(emptyList()) }
    var index by remember { mutableStateOf(0) }
    var subMode by remember { mutableStateOf(SubMode.CORNER) }

    // 当前页状态
    var srcBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var correctedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var corners by remember { mutableStateOf(defaultCorners()) }
    var dividers by remember { mutableStateOf<List<Float>>(emptyList()) }
    var columnCount by remember { mutableStateOf(12) }
    var columnOverlap by remember { mutableStateOf(0) }

    // 参数（全局 / 当前页）
    var globalColumnCount by remember { mutableStateOf(12) }
    var globalOverlap by remember { mutableStateOf(0) }
    var currentColumnCountText by remember { mutableStateOf("12") }
    var currentOverlapText by remember { mutableStateOf("0") }

    // 丢弃标记模式
    var markMode by remember { mutableStateOf(false) }
    var droppedColumns by remember { mutableStateOf<Set<Int>>(emptySet()) }

    // 预览
    var showPreview by remember { mutableStateOf(false) }

    LaunchedEffect(rootUri) {
        try {
            val p = storage.loadProject()
            project = p
            globalColumnCount = p.layout.globalColumnCount
            globalOverlap = p.layout.globalColumnOverlap
            pages = storage.listPages()
            index = 0
        } catch (_: Exception) { }
    }

    // 加载当前页
    LaunchedEffect(pages, index) {
        correctedBitmap = null
        srcBitmap = null
        val page = pages.getOrNull(index) ?: return@LaunchedEffect
        val proj = project ?: return@LaunchedEffect

        withContext(Dispatchers.IO) {
            val uri = storage.openFileUri("pages", page.srcName)
            if (uri != null) {
                srcBitmap = BitmapIO.decodeSampled(context, uri, 2400)
            }
        }

        val geo = page.geometry
        if (geo != null && geo.corners.size == 4) {
            corners = geo.corners.map { Offset(it[0], it[1]) }
            columnCount = geo.columnCount
            dividers = geo.columnDividers
            columnOverlap = geo.columnOverlap
            currentColumnCountText = geo.columnCount.toString()
            currentOverlapText = geo.columnOverlap.toString()
            subMode = SubMode.COLUMN
        } else {
            corners = defaultCorners()
            columnCount = proj.layout.globalColumnCount
            columnOverlap = proj.layout.globalColumnOverlap
            dividers = evenDividers(columnCount)
            currentColumnCountText = columnCount.toString()
            currentOverlapText = columnOverlap.toString()
            subMode = SubMode.CORNER
        }
        markMode = false
        droppedColumns = page.columns.filter { it.dropped }.map { it.colIndex - 1 }.toSet()
    }

    // 进入分列模式时生成校正图
    LaunchedEffect(subMode, corners) {
        if (subMode == SubMode.COLUMN) {
            val src = srcBitmap ?: return@LaunchedEffect
            val c = corners
            if (c.size != 4) return@LaunchedEffect
            withContext(Dispatchers.Default) {
                correctedBitmap = PerspectiveEngine.correct(src, c)
            }
        }
    }

    // 保存当前页
    suspend fun saveCurrentPage() {
        val page = pages.getOrNull(index) ?: return
        val proj = project ?: return
        val geom = PageGeometry(
            corners = corners.map { listOf(it.x, it.y) },
            columnCount = columnCount,
            columnDividers = dividers,
            columnOverlap = columnOverlap
        )
        val cols = (0 until columnCount).map { i ->
            ColumnInfo(
                colIndex = i + 1,
                rows = proj.layout.globalRowCount,
                dividers = emptyList(),
                rowOverlap = proj.layout.globalRowOverlap,
                dropped = droppedColumns.contains(i),
                confirmed = false
            )
        }
        val updated = page.copy(
            geometry = geom,
            columns = cols,
            state = PageState.COLUMNS_SET,
            correctedName = PdfToImageEngine.correctedNameOf(page.pageIndex)
        )
        storage.savePage(updated)

        // 保存校正图
        correctedBitmap?.let { bmp ->
            val bytes = BitmapIO.compressPng(bmp)
            storage.writeImage("pages", updated.correctedName!!, bytes)
        }

        // 更新 pages 列表
        pages = pages.toMutableList().also { it[index] = updated }
    }

    fun goTo(target: Int) {
        if (target < 0) return
        if (target >= pages.size) {
            // 到最后，标记步骤完成
            scope.launch {
                val proj = storage.loadProject()
                val newSteps = proj.steps.toMutableMap()
                val done = pages.count { it.state != PageState.PENDING }
                newSteps[Step.CORRECT.id] = StepState(
                    completed = done == pages.size && pages.isNotEmpty(),
                    done = done,
                    total = pages.size
                )
                storage.saveProject(proj.copy(steps = newSteps))
                onGoStep(Step.SPLIT.id)
            }
            return
        }
        scope.launch {
            if (subMode == SubMode.COLUMN && corners.size == 4) {
                saveCurrentPage()
            }
            index = target
        }
    }

    StepScaffold(
        rootUri = rootUri,
        step = Step.CORRECT,
        onBack = onBack,
        onGoStep = onGoStep,
        resetEnabled = true,
        previewEnabled = true,
        onReset = {
            // 只重置当前页
            corners = defaultCorners()
            dividers = evenDividers(globalColumnCount)
            columnCount = globalColumnCount
            columnOverlap = globalOverlap
            currentColumnCountText = columnCount.toString()
            currentOverlapText = columnOverlap.toString()
            droppedColumns = emptySet()
            subMode = SubMode.CORNER
        },
        onPreview = { showPreview = true },
        bottomBar = {
            AppBottomBar(
                prevEnabled = index > 0,
                skipEnabled = true,
                nextEnabled = index < pages.size,
                onPrev = { goTo(index - 1) },
                onSkip = { goTo(index + 1) },
                onNext = { goTo(index + 1) }
            )
        }
    ) { _, _ ->
        Column(Modifier.fillMaxSize()) {

            // ============ 参数栏 ============
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {

                    // 子模式切换
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModeTab("四点校正", subMode == SubMode.CORNER) {
                            subMode = SubMode.CORNER
                        }
                        ModeTab("分列", subMode == SubMode.COLUMN) {
                            subMode = SubMode.COLUMN
                            if (dividers.isEmpty()) dividers = evenDividers(columnCount)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${index + 1}/${pages.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // 分列参数（仅分列模式显示）
                    if (subMode == SubMode.COLUMN) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            NumberField(
                                "全局列数", globalColumnCount.toString(),
                                { s ->
                                    val v = s.filter { it.isDigit() }.take(3).toIntOrNull() ?: 12
                                    globalColumnCount = v.coerceIn(1, 50)
                                },
                                Modifier.weight(1f)
                            )
                            NumberField(
                                "当前页", currentColumnCountText,
                                { s ->
                                    currentColumnCountText = s.filter { it.isDigit() }.take(3)
                                    val v = currentColumnCountText.toIntOrNull() ?: return@NumberField
                                    val cc = v.coerceIn(1, 50)
                                    columnCount = cc
                                    dividers = evenDividers(cc)
                                },
                                Modifier.weight(1f)
                            )
                            NumberField(
                                "左右重叠", currentOverlapText,
                                { s ->
                                    currentOverlapText = s.filter { it.isDigit() }.take(4)
                                    columnOverlap = currentOverlapText.toIntOrNull() ?: 0
                                },
                                Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { markMode = !markMode },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (markMode)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.weight(1f)
                            ) { Text(if (markMode) "退出标记" else "丢弃") }
                        }
                    }
                }
            }

            // ============ 显示区 ============
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (subMode == SubMode.CORNER) {
                    CornerEditView(
                        bitmap = srcBitmap,
                        corners = corners,
                        onCornersChange = { corners = it },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ColumnEditView(
                        bitmap = correctedBitmap ?: srcBitmap,
                        columnDividers = dividers,
                        onDividersChange = {
                            dividers = it
                            columnCount = it.size + 1
                            currentColumnCountText = columnCount.toString()
                        },
                        markMode = markMode,
                        droppedColumns = droppedColumns,
                        onToggleColumn = { i ->
                            droppedColumns = if (droppedColumns.contains(i))
                                droppedColumns - i else droppedColumns + i
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // 全屏预览
    if (showPreview) {
        val bmp = if (subMode == SubMode.COLUMN) correctedBitmap else srcBitmap
        if (bmp != null) {
            FullScreenPreview(bmp) { showPreview = false }
        } else {
            showPreview = false
        }
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

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun FullScreenPreview(bmp: android.graphics.Bitmap, onClose: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
                .clickable { onClose() },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
    }
}

// 辅助函数
private fun defaultCorners(): List<Offset> = listOf(
    Offset(0.05f, 0.05f),
    Offset(0.95f, 0.05f),
    Offset(0.95f, 0.95f),
    Offset(0.05f, 0.95f)
)

private fun evenDividers(count: Int): List<Float> {
    if (count <= 1) return emptyList()
    return (1 until count).map { it.toFloat() / count }
}

// 需要的 import
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.asImageBitmap
