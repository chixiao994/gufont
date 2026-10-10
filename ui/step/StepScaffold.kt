package com.gufont.app.ui.step

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gufont.app.data.model.Project
import com.gufont.app.data.model.Step
import com.gufont.app.data.storage.ProjectStorage
import com.gufont.app.ui.components.AppBottomBar
import com.gufont.app.ui.components.AppTopBar
import com.gufont.app.ui.components.HamburgerMenu
import kotlinx.coroutines.launch

/**
 * 所有步骤页共用骨架：
 *   AppTopBar(☰ 文件 重置 预览)
 *   内容区（占位或实现）
 *   AppBottomBar(上一张 跳过 下一张)
 *   汉堡菜单（可选）
 */
@Composable
fun StepScaffold(
    rootUri: String,
    step: Step,
    onBack: () -> Unit,
    onGoStep: (Int) -> Unit,
    content: @Composable (Project?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }

    LaunchedEffect(rootUri) {
        try {
            project = ProjectStorage(context, Uri.parse(rootUri)).loadProject()
        } catch (_: Exception) { }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        AppTopBar(
            title = step.label,
            onMenuClick = { showMenu = true },
            onFileClick = onBack,
            onResetClick = { /* 各步骤实现里接管 */ },
            onPreviewClick = { showPreview = true }
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            content(project)
        }

        AppBottomBar(
            onPrev = { },
            onSkip = { },
            onNext = { }
        )
    }

    if (showMenu) {
        HamburgerMenu(
            currentStep = step.id,
            stepStates = project?.steps ?: emptyMap(),
            onDismiss = { showMenu = false },
            onStepSelected = { id ->
                showMenu = false
                onGoStep(id)
            }
        )
    }

    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("预览") },
            text = { Text("（各步骤实现时接入全屏预览）") },
            confirmButton = {
                TextButton(onClick = { showPreview = false }) { Text("关闭") }
            }
        )
    }
}
