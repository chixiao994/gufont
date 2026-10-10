package com.gufont.app.ui.step

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.gufont.app.data.model.Project
import com.gufont.app.data.model.Step
import com.gufont.app.data.storage.ProjectStorage
import com.gufont.app.ui.components.AppBottomBar
import com.gufont.app.ui.components.AppTopBar
import com.gufont.app.ui.components.HamburgerMenu

@Composable
fun StepScaffold(
    rootUri: String,
    step: Step,
    onBack: () -> Unit,
    onGoStep: (Int) -> Unit,
    onReset: () -> Unit = {},
    onPreview: () -> Unit = {},
    resetEnabled: Boolean = true,
    previewEnabled: Boolean = true,
    /** 自定义底栏；null 表示用默认的禁用三按钮 */
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (Project?, (Project) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var project by remember { mutableStateOf<Project?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(rootUri, step) {
        try {
            project = ProjectStorage(context, Uri.parse(rootUri)).loadProject()
        } catch (_: Exception) { }
    }

    val saveProject: (Project) -> Unit = { p ->
        project = p
        // 异步保存；不阻塞 UI
        val ctx = context
        kotlinx.coroutines.GlobalScope.launch(
            kotlinx.coroutines.Dispatchers.IO
        ) {
            try { ProjectStorage(ctx, Uri.parse(rootUri)).saveProject(p) }
            catch (_: Exception) { }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBar(
            title = step.label,
            onMenuClick = { showMenu = true },
            onFileClick = onBack,
            onResetClick = onReset,
            onPreviewClick = onPreview,
            resetEnabled = resetEnabled,
            previewEnabled = previewEnabled
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            content(project, saveProject)
        }

        if (bottomBar != null) {
            bottomBar()
        } else {
            AppBottomBar(
                prevEnabled = false,
                skipEnabled = false,
                nextEnabled = false,
                onPrev = {}, onSkip = {}, onNext = {}
            )
        }
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
}

// 小工具
private fun kotlinx.coroutines.CoroutineScope.launch(
    dispatcher: kotlin.coroutines.CoroutineContext,
    block: suspend () -> Unit
) = kotlinx.coroutines.CoroutineScope(dispatcher).let {
    kotlinx.coroutines.launch(context = dispatcher, block = block)
}
