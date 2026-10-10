package com.gufont.app.ui.step

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gufont.app.data.model.Glyph
import com.gufont.app.data.model.GlyphIndex
import com.gufont.app.data.model.GlyphState
import com.gufont.app.data.storage.ProjectStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingListScreen(
    rootUri: String,
    onBack: () -> Unit,
    onEditGlyph: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storage = remember { ProjectStorage(context, Uri.parse(rootUri)) }

    var pending by remember { mutableStateOf<List<Glyph>>(emptyList()) }

    LaunchedEffect(Unit) {
        val gi = storage.loadGlyphIndex()
        pending = gi.glyphs.filter {
            it.state != GlyphState.SKIPPED &&
                    (it.issue != null || it.codepoint == 0)
        }
    }

    fun reload() {
        scope.launch {
            val gi = storage.loadGlyphIndex()
            pending = gi.glyphs.filter {
                it.state != GlyphState.SKIPPED &&
                        (it.issue != null || it.codepoint == 0)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("待处理 (${pending.size})", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    if (pending.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                val gi = storage.loadGlyphIndex()
                                val updated = gi.glyphs.map {
                                    if (pending.any { p -> p.code == it.code })
                                        it.copy(state = GlyphState.SKIPPED)
                                    else it
                                }
                                storage.saveGlyphIndex(GlyphIndex(updated))
                                pending = emptyList()
                            }
                        }) { Text("全部抛弃") }
                    }
                }
            )
        }
    ) { pad ->
        if (pending.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Text("没有待处理项", color = MaterialTheme.colorScheme.outline)
            }
            return@Scaffold
        }

        LazyColumn(
            Modifier.padding(pad).fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pending) { g ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEditGlyph(g.code) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(g.code, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "原因：${g.issue ?: "未命名"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "U+${g.codepoint.toString(16).uppercase()}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
