package com.gufont.app.ui.step

import android.graphics.Bitmap
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
import com.gufont.app.engine.image.GlyphCutter
import com.gufont.app.ui.components.AppBottomBar
import com.gufont.app.ui.step.components.SplitCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class FlatColumn(
    val pageIndex: Int,
    val colIndexFromRight: Int,
    val colIndexFromLeft: Int,
    val page: Page,
    val columnInfo: ColumnInfo
)

@Composable
fun StepSplitScreen(rootUri: String, onBack: () -> Unit, onGoStep: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { ProjectStorage(context, Uri.parse(rootUri)) }

    var project by remember { mutableStateOf<Project?>(null) }
    var flatColumns by remember { mutableStateOf<List<FlatColumn>>(emptyList()) }
    var index by remember { mutableStateOf(0) }

    var correctedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var columnBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var dividers by remember { mutableStateOf<List<Float>>(emptyList()) }
    var rowCount by remember { mutableStateOf(10) }
    var rowOverlap by remember { mutableStateOf(0) }

    var globalRowCount by remember { mutableStateOf(10) }
    var globalOverlap by remember { mutableStateOf(0) }

    var currentRowCountText by remember { mutableStateOf("10") }
    var currentOverlapText by remember { mutableStateOf("0") }

    var markMode by remember { mutableStateOf(false) }
    var droppedRows by remember { mutableStateOf<Set<Int>>(emptySet()) }

    var showPreview by remember { mutableStateOf(false) }
    var lastLoadedPage by remember { mutableStateOf(-1) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(rootUri) {
        try {
            val proj = storage.loadProject()
            project = proj
            globalRowCount = proj.layout.globalRowCount
            globalOverlap = proj.layout.globalRowOverlap

            // ★ 以文件系统为准
            val pages = storage.syncPagesFromFileSystem()
            val flat = mutableListOf<FlatColumn>()
            for (pg in pages) {
                val geo = pg.geometry ?: continue
                val n = geo.columnCount
                for (k in (n - 1) downTo 0) {
                    val colRight = n - k
                    val info = pg.columns.getOrNull(k)
                        ?: ColumnInfo(colIndex = colRight)
                    flat.add(
                        FlatColumn(
                            pageIndex = pg.pageIndex,
                            colIndexFromRight = colRight,
                            colIndexFromLeft = k,
                            page = pg,
                            columnInfo = info
                        )
                    )
                }
            }
            flatColumns = flat
            index = 0
            if (flat.isEmpty()) {
                loadError = "没有可处理的列\n请先在「校正 + 分列」步骤完成至少一页"
            }
        } catch (e: Exception) {
            loadError = "加载项目失败：${e.message}"
        }
    }

    LaunchedEffect(flatColumns, index) {
        correctedBitmap = null
        columnBitmap = null
        loadError = null

        val fc = flatColumns.getOrNull(index)
        if (fc == null) {
            loadError = "无列：flatColumns.size=${flatColumns.size}, index=$index"
            return@LaunchedEffect
        }
        val proj = project
        if (proj == null) {
            loadError = "项目未加载"
            return@LaunchedEffect
        }

        val correctedName = fc.page.correctedName
        if (correctedName.isNullOrBlank()) {
            loadError = "该页尚未生成校正图\npageIndex=${fc.pageIndex}"
            return@LaunchedEffect
        }

        loading = true
        try {
            val cUri = storage.openFileUri("pages", correctedName)
            if (cUri == null) {
                loadError = "找不到校正图：pages/$correctedName"
                return@LaunchedEffect
            }

            withContext(Dispatchers.IO) {
                val needReload = correctedBitmap == null ||
                        lastLoadedPage != fc.pageIndex
                if (needReload) {
                    correctedBitmap?.recycle()
                    correctedBitmap = BitmapIO.decodeSampled(context, cUri, 3000)
                    lastLoadedPage = fc.pageIndex
                }
                val cb = correctedBitmap
                if (cb != null) {
                    columnBitmap?.recycle()
                    columnBitmap = GlyphCutter.cropColumn(
                        cb, fc.page, fc.colIndexFromLeft,
                        fc.page.geometry?.columnOverlap ?: 0
                    )
                }
            }
        } catch (e: Exception) {
            loadError = "加载异常：${e.message}"
        } finally {
            loading = false
        }

        val info = fc.columnInfo
        rowCount = if (info.confirmed) info.rows else proj.layout.globalRowCount
        rowOverlap = info.rowOverlap
        dividers = if (info.dividers.isNotEmpty() &&
            info.dividers.size == rowCount - 1
        ) info.dividers else evenDividers(rowCount)
        currentRowCountText = rowCount.toString()
        currentOverlapText = rowOverlap.toString()
        droppedRows = emptySet()
        markMode = false
    }

    suspend fun saveCurrentColumn() {
        val fc = flatColumns.getOrNull(index) ?: return
        val proj = project ?: return

        val newInfo = fc.columnInfo.copy(
            rows = rowCount,
            dividers = dividers,
            rowOverlap = rowOverlap,
            confirmed = true
        )
        val cols = fc.page.columns.toMutableList()
        while (cols.size <= fc.colIndexFromLeft) {
            cols.add(ColumnInfo(colIndex = cols.size + 1))
        }
        cols[fc.colIndexFromLeft] = newInfo
        val updatedPage = fc.page.copy(
            columns = cols,
            state = PageState.SPLIT_DONE
        )
        storage.savePage(updatedPage)

        val columnBmp = columnBitmap ?: return
        val glyphIndex = storage.loadGlyphIndex()
        val byCode = glyphIndex.glyphs.associateBy { it.code }.toMutableMap()
        val nowStr = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss", Locale.US
        ).format(Date())

        withContext(Dispatchers.IO) {
            for (rowIdx in 0 until rowCount) {
                if (droppedRows.contains(rowIdx)) continue

                val code = buildGlyphCode(
                    proj.book.bookAbbr,
                    proj.book.annotatorAbbr,
                    proj.book.editionAbbr,
                    fc.pageIndex,
                    fc.colIndexFromRight,
                    rowIdx + 1
                )
                if (byCode.containsKey(code)) continue

                val rowBmp = GlyphCutter.cropRow(
                    columnBmp, dividers, rowIdx, rowCount, rowOverlap
                )
                val bytes = BitmapIO.compressPng(rowBmp)
                rowBmp.recycle()

                val rawName = "${code}_raw.png"
                storage.writeImage("glyphs", rawName, bytes)

                byCode[code] = Glyph(
                    code = code,
                    page = fc.pageIndex,
                    col = fc.colIndexFromRight,
                    row = rowIdx + 1,
                    state = GlyphState.EXTRACTED,
                    raw = rawName,
                    updated = nowStr
                )
            }
        }
        storage.saveGlyphIndex(GlyphIndex(byCode.values.toList()))
    }

    fun goTo(target: Int) {
        if (target < 0) return
        if (target >= flatColumns.size) {
            scope.launch {
                val proj = storage.loadProject()
                val newSteps = proj.steps.toMutableMap()
                newSteps[Step.SPLIT.id] = StepState(
                    completed = true,
                    done = flatColumns.size,
                    total = flatColumns.size
                )
                val gi = storage.loadGlyphIndex()
                newSteps[Step.EDIT.id] = StepState(
                    completed = false,
                    done = 0,
                    total = gi.glyphs.size
                )
                storage.saveProject(proj.copy(steps = newSteps))
                onGoStep(Step.EDIT.id)
            }
            return
        }
        scope.launch {
            try { saveCurrentColumn() } catch (_: Exception) { }
            index = target
        }
    }

    StepScaffold(
        rootUri = rootUri,
        step = Step.SPLIT,
        onBack = onBack,
        onGoStep = onGoStep,
        resetEnabled = true,
        previewEnabled = true,
        onReset = {
            dividers = evenDividers(globalRowCount)
            rowCount = globalRowCount
            rowOverlap = globalOverlap
            currentRowCountText = rowCount.toString()
            currentOverlapText = rowOverlap.toString()
            droppedRows = emptySet()
        },
        onPreview = { showPreview = true },
        bottomBar = {
            AppBottomBar(
                prevEnabled = index > 0,
                skipEnabled = true,
                nextEnabled = index < flatColumns.size,
                onPrev = { goTo(index - 1) },
                onSkip = { goTo(index + 1) },
                onNext = { goTo(index + 1) }
            )
        }
    ) { _, _ ->
        Column(Modifier.fillMaxSize()) {

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val fc = flatColumns.getOrNull(index)
                        val proj = project
                        val title = if (fc != null && proj != null) {
                            "${proj.book.codePrefix()}_p${
                                fc.pageIndex.toString().padStart(3, '0')
                            }_c${
                                fc.colIndexFromRight.toString().padStart(2, '0')
                            }"
                        } else "—"
                        Text(
                            title, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${index + 1}/${flatColumns.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NumberField(
                            "全局行数",
                            globalRowCount.toString(),
                            { s ->
                                val v = s.filter { it.isDigit() }.take(3)
                                    .toIntOrNull() ?: 10
                                globalRowCount = v.coerceIn(1, 100)
                            },
                            Modifier.weight(1f)
                        )
                        NumberField(
                            "当前列",
                            currentRowCountText,
                            { s ->
                                currentRowCountText =
                                    s.filter { it.isDigit() }.take(3)
                                val v = currentRowCountText.toIntOrNull()
                                    ?: return@NumberField
                                val rc = v.coerceIn(1, 100)
                                rowCount = rc
                                dividers = evenDividers(rc)
                            },
                            Modifier.weight(1f)
                        )
                        NumberField(
                            "上下重叠",
                            currentOverlapText,
                            { s ->
                                currentOverlapText =
                                    s.filter { it.isDigit() }.take(4)
                                rowOverlap =
                                    currentOverlapText.toIntOrNull() ?: 0
                            },
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Row {
                        Button(
                            onClick = { markMode = !markMode },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (markMode)
                                    MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (markMode) "退出标记" else "丢弃") }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loadError != null -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text(
                                loadError!!,
                                color = Color(0xFFE57373),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                    loading -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        SplitCanvas(
                            columnBitmap = columnBitmap,
                            dividers = dividers,
                            onDividersChange = {
                                dividers = it
                                rowCount = it.size + 1
                                currentRowCountText = rowCount.toString()
                            },
                            markMode = markMode,
                            droppedRows = droppedRows,
                            onToggleRow = { i ->
                                droppedRows = if (droppedRows.contains(i))
                                    droppedRows - i else droppedRows + i
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (showPreview) {
        val bmp = correctedBitmap
        if (bmp != null) {
            Dialog(
                onDismissRequest = { showPreview = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
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

private fun evenDividers(count: Int): List<Float> {
    if (count <= 1) return emptyList()
    return (1 until count).map { it.toFloat() / count }
}
