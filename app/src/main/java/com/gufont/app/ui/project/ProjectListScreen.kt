package com.gufont.app.ui.project

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gufont.app.data.json.JsonIO
import com.gufont.app.util.SafUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
data class RecentProject(
    val uri: String,
    val name: String,
    val bookTitle: String = "",
    val edition: String = "",
    val glyphTotal: Int = 0,
    val glyphInFont: Int = 0,
    val updated: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onOpenProject: (String) -> Unit,
    onNewProject: () -> Unit
) {
    val context = LocalContext.current
    var recent by remember { mutableStateOf<List<RecentProject>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val prefs = context.getSharedPreferences("gufont_recent", 0)
            val raw = prefs.getString("list", "[]") ?: "[]"
            recent = JsonIO.json.decodeFromString<List<RecentProject>>(raw)
        } catch (_: Exception) {
            recent = emptyList()
        }
    }

    val pickExisting = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            SafUtils.persist(context, uri)
            onOpenProject(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("古籍字库", fontWeight = FontWeight.Bold)
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewProject,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("新建") }
            )
        }
    ) { p ->
        Column(
            Modifier.padding(p).fillMaxSize().padding(16.dp)
        ) {
            Text(
                "最近项目",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (recent.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "还没有项目\n点右下角「新建」开始",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recent) { r ->
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SafUtils.persist(context, Uri.parse(r.uri))
                                    onOpenProject(r.uri)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(r.name, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${r.bookTitle} · ${r.edition}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "字位 ${r.glyphInFont}/${r.glyphTotal} · ${r.updated}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { pickExisting.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("打开已有项目") }
        }
    }
}
