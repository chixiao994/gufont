package com.gufont.app.ui.home

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gufont.app.data.model.Project
import com.gufont.app.data.model.Step
import com.gufont.app.data.storage.ProjectStorage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectHomeScreen(
    rootUri: String,
    onBack: () -> Unit,
    onOpenStep: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun reload() {
        try {
            project = ProjectStorage(context, Uri.parse(rootUri)).loadProject()
        } catch (_: Exception) { }
    }

    LaunchedEffect(rootUri) {
        reload()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        project?.book?.title ?: "项目",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { p ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(p), Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    reload()
                    refreshing = false
                }
            },
            modifier = Modifier.padding(p).fillMaxSize()
        ) {
            val pr = project
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (pr != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            InfoRow("书名", pr.book.title)
                            if (pr.book.annotator.isNotBlank())
                                InfoRow("注者", pr.book.annotator)
                            if (pr.book.edition.isNotBlank())
                                InfoRow("版本", pr.book.edition)
                            InfoRow("前缀", pr.book.codePrefix())
                        }
                    }
                }

                Text(
                    "步骤",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                Step.values().forEach { s ->
                    val st = pr?.steps?.get(s.id)
                    // ★ 解锁逻辑：前一步完成才解锁；IMPORT 永远解锁
                    val prevCompleted = if (s.id == Step.IMPORT.id) true
                    else pr?.steps?.get(s.id - 1)?.completed == true
                    val unlocked = prevCompleted

                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = unlocked) { onOpenStep(s.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (unlocked)
                                MaterialTheme.colorScheme.surfaceVariant
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                when {
                                    st?.completed == true -> "✓"
                                    unlocked -> "○"
                                    else -> "·"
                                },
                                modifier = Modifier.width(28.dp),
                                fontSize = 16.sp
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    s.label,
                                    fontWeight = FontWeight.Bold,
                                    color = if (unlocked)
                                        MaterialTheme.colorScheme.onSurface
                                    else
                                        MaterialTheme.colorScheme.outline
                                )
                                if ((st?.total ?: 0) > 0) {
                                    Text(
                                        "${st!!.done} / ${st.total}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (unlocked) {
                                Text(
                                    "进入",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun InfoRow(k: String, v: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(
            k,
            modifier = Modifier.width(64.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Text(v, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
