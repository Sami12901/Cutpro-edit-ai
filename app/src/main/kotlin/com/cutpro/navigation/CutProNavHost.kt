package com.cutpro.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.cutpro.core.data.database.CutproDatabase
import com.cutpro.core.export.Media3VideoExportEngine
import com.cutpro.core.playback.PreviewPlaybackCoordinator
import com.cutpro.feature.dashboard.DashboardScreen
import com.cutpro.feature.dashboard.DashboardViewModel
import com.cutpro.feature.editor.EditorScreen
import com.cutpro.feature.editor.EditorViewModel
import com.cutpro.feature.export.ui.ExportConfigDialog
import com.cutpro.feature.export.ui.ExportProgressSheet
import com.cutpro.feature.export.ui.ExportViewModel

object CutProDestinations {
    const val DASHBOARD = "dashboard"
    const val EDITOR_ROUTE = "editor/{projectId}"
    fun editorRoute(projectId: String) = "editor/$projectId"
}

@kotlin.OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CutProNavHost(
    navController: NavHostController,
    database: CutproDatabase,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = CutProDestinations.DASHBOARD,
        modifier = modifier
    ) {
        composable(CutProDestinations.DASHBOARD) {
            val dashboardViewModel: DashboardViewModel = viewModel {
                DashboardViewModel(
                    projectDao = database.projectDao(),
                    trackDao = database.trackDao(),
                    clipDao = database.clipDao()
                )
            }
            DashboardScreen(
                viewModel = dashboardViewModel,
                onNavigateToEditor = { projectId ->
                    navController.navigate(CutProDestinations.editorRoute(projectId))
                }
            )
        }

        composable(
            route = CutProDestinations.EDITOR_ROUTE,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val context = LocalContext.current
            val editorViewModel: EditorViewModel = viewModel {
                val coordinator = PreviewPlaybackCoordinator(context.applicationContext)
                EditorViewModel(
                    projectDao = database.projectDao(),
                    playbackCoordinator = coordinator
                )
            }
            val exportEngine = remember { Media3VideoExportEngine(context.applicationContext) }
            val exportViewModel: ExportViewModel = viewModel {
                ExportViewModel(exportEngine = exportEngine)
            }

            val editorUiState by editorViewModel.uiState.collectAsState()
            val showConfigDialog by exportViewModel.showConfigDialog.collectAsState()
            val showProgressSheet by exportViewModel.showProgressSheet.collectAsState()
            val progressState by exportViewModel.progressState.collectAsState()

            EditorScreen(
                projectId = projectId,
                viewModel = editorViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToExport = {
                    val currentProject = editorUiState.project
                    if (currentProject != null) {
                        exportViewModel.openConfigDialog(currentProject)
                    }
                }
            )

            val currentProject = editorUiState.project
            if (showConfigDialog && currentProject != null) {
                ExportConfigDialog(
                    project = currentProject,
                    onDismiss = { exportViewModel.dismissConfigDialog() },
                    onConfirmExport = { plan ->
                        exportViewModel.startExport(context.applicationContext, currentProject, plan)
                    }
                )
            }

            if (showProgressSheet) {
                ExportProgressSheet(
                    state = progressState,
                    onCancel = { exportViewModel.cancelExport(context.applicationContext) },
                    onShare = { exportViewModel.shareExportedVideo(context.applicationContext) },
                    onDismiss = { exportViewModel.dismissProgressSheet() }
                )
            }
        }
    }
}
