package com.gufont.app.ui.project

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gufont.app.data.model.*
import com.gufont.app.data.storage.ProjectStorage
import com.gufont.app.util.SafUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectWizard(
    onCreated: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rootUri by remember { mutableStateOf<Uri?>(null) }
    var bookTitle by remember { mutableStateOf("") }
    var annotator by remember { mutableStateOf("") }
    var edition by remember { mutableStateOf("") }
    var bookAbbr by remember { mutableStateOf("") }
    var annotatorAbbr by remember { mutableStateOf("") }
    var editionAbbr by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            SafUtils.persist(context, uri)
            rootUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建项目") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        }
    ) { p ->
        Column(
            Modifier
                .padding(p)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("保存位置", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            OutlinedButton(
                onClick = { pickDir.launch(null) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(rootUri?.lastPathSegment ?: "选择文件夹") }

            Text("书籍信息", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Field("书名", bookTitle, { bookTitle = it }, "例：老子")
            Field("注者", annotator, { annotator = it }, "可留空")
            Field("版本", edition, { edition = it }, "可留空")

            Text("编码缩写（拼音首字母）", fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("书名", bookAbbr, { bookAbbr = it.uppercase().take(4) }, "LZ",
                    Modifier.weight(1f))
                Field("注者", annotatorAbbr, { annotatorAbbr = it.uppercase().take(2) }, "WB",
                    Modifier.weight(1f))
                Field("版本", editionAbbr, { editionAbbr = it.uppercase().take(2) }, "S",
                    Modifier.weight(1f))
            }

            Text(
                "编码示例：${bookAbbr.ifBlank { "LZ" }}${annotatorAbbr.ifBlank { "WB" }}${editionAbbr.ifBlank { "S" }}_p003_c02_r08",
                fontSize = 12.dp.let { 12.sp() },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val uri = rootUri ?: return@Button
                    if (working) return@Button
                    working = true
                    error = null
                    scope.launch {
                        try {
                            val storage = ProjectStorage(context, uri)
                            val now = SimpleDateFormat(
                                "yyyy-MM-dd'T'HH:mm:ss", Locale.US
                            ).format(Date())
                            val project = Project(
                                created = now,
                                updated = now,
                                book = BookInfo(
                                    title = bookTitle.ifBlank { "未命名" },
                                    annotator = annotator,
                                    edition = edition,
                                    bookAbbr = bookAbbr.ifBlank { "BK" },
                                    annotatorAbbr = annotatorAbbr,
                                    editionAbbr = editionAbbr.ifBlank { "V1" }
                                )
                            )
                            storage.createProject(project)
                            onCreated(uri.toString())
                        } catch (e: Exception) {
                            error = "创建失败：${e.message}"
                            working = false
                        }
                    }
                },
                enabled = rootUri != null && !working,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (working) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (working) "创建中…" else "创建")
            }
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(
    this.toFloat(),
    androidx.compose.ui.unit.TextUnitType.Sp
)

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        modifier = modifier.fillMaxWidth()
    )
}
