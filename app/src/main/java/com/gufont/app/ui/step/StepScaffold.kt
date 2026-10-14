package com.gufont.app.ui.step

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.gufont.app.data.model.Project
import com.gufont.app.data.model.Step
import com.gufont.app.data.storage.ProjectStorage
import com.gufont.app.ui.components.AppBottomBar
import com.gufont.app.ui.components.AppTopBar
import com.gufont.app.ui.components.HamburgerMenu
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    /**
     * 下拉刷新回调。
     * 传非 null 时，中间区支持下拉刷新。
     * 通常在回调里重新扫描 SAF 目录 + 重读 JSON。
     */
    onRefresh: (suspend () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (Project?, (Project) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var project by remember { mutableStateOf<Project?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(rootUri, step) {
        try {
            project = ProjectStorage(context, Uri.parse(rootUri)).loadProject()
        } catch (_: Exception) { }
    }

    val saveProject: (Project) -> Unit = { p ->
        project = p
        scope.launch(Dispatchers.IO) {
            try {
                ProjectStorage(context, Uri.parse(rootUri)).saveProject(p)
            } catch (_: Exception) { }
        }
    }

    val doRefresh: () -> Unit = {
        if (onRefresh != null && !refreshing) {
            scope.launch {
                refreshing = true
                try {
                    onRefresh()
                    project = ProjectStorage(context, Uri.parse(rootUri)).loadProject()
                } catch (_: Exception) { }
                refreshing = false
            }
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

        if (onRefresh != null) {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = doRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                Box(Modifier.fillMaxSize()) {
                    content(project, saveProject)
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                content(project, saveProject)
            }
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
