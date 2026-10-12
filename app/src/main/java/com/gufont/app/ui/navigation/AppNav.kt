package com.gufont.app.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gufont.app.ui.home.ProjectHomeScreen
import com.gufont.app.ui.project.NewProjectWizard
import com.gufont.app.ui.project.ProjectListScreen
import com.gufont.app.ui.step.*

object Routes {
    const val PROJECT_LIST = "project_list"
    const val NEW_PROJECT = "new_project"
    const val PROJECT_HOME = "project_home/{rootUri}"
    const val STEP = "step/{rootUri}/{stepId}"

    fun projectHome(uri: String) = "project_home/${Uri.encode(uri)}"
    fun step(uri: String, id: Int) = "step/${Uri.encode(uri)}/$id"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()

    NavHost(nav, startDestination = Routes.PROJECT_LIST) {

        composable(Routes.PROJECT_LIST) {
            ProjectListScreen(
                onOpenProject = { nav.navigate(Routes.projectHome(it)) },
                onNewProject = { nav.navigate(Routes.NEW_PROJECT) }
            )
        }

        composable(Routes.NEW_PROJECT) {
            NewProjectWizard(
                onCreated = { uri ->
                    nav.navigate(Routes.projectHome(uri)) {
                        popUpTo(Routes.NEW_PROJECT) { inclusive = true }
                    }
                },
                onCancel = { nav.popBackStack() }
            )
        }

        composable(
            Routes.PROJECT_HOME,
            arguments = listOf(navArgument("rootUri") { type = NavType.StringType })
        ) { e ->
            val uri = e.arguments?.getString("rootUri") ?: return@composable
            ProjectHomeScreen(
                rootUri = uri,
                onBack = { nav.popBackStack() },
                onOpenStep = { id -> nav.navigate(Routes.step(uri, id)) }
            )
        }

        composable(
            Routes.STEP,
            arguments = listOf(
                navArgument("rootUri") { type = NavType.StringType },
                navArgument("stepId") { type = NavType.IntType }
            )
        ) { e ->
            val uri = e.arguments?.getString("rootUri") ?: return@composable
            val stepId = e.arguments?.getInt("stepId") ?: 0

            val goHome: () -> Unit = {
                nav.popBackStack(Routes.PROJECT_HOME, false)
                Unit
            }
            val goStep: (Int) -> Unit = { id ->
                nav.navigate(Routes.step(uri, id)) {
                    popUpTo(Routes.STEP) { inclusive = true }
                }
                Unit
            }

            when (stepId) {
                0 -> StepImportScreen(uri, goHome, goStep)
                1 -> StepCorrectScreen(uri, goHome, goStep)
                2 -> StepSplitScreen(uri, goHome, goStep)
                3 -> StepEditScreen(uri, goHome, goStep)
                4 -> StepInstallScreen(uri, goHome, goStep)
            }
        }
    }
}
