package com.cutpro.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.feature.editor.ai.AiCodeRunnerSheet
import com.cutpro.feature.editor.ai.TranscriptPromptSheet
import com.cutpro.feature.editor.canvas.PreviewCanvasArea
import com.cutpro.feature.editor.inspector.ContextualPropertiesInspector
import com.cutpro.feature.editor.timeline.MultitrackTimelineArea
import com.cutpro.feature.editor.toolbar.TopToolbar

/**
 * Main CutPro Editor Screen assembling the Top Action Toolbar, Preview Canvas Area,
 * Multitrack Timeline UI, and Contextual Properties Inspector.
 * Adheres strictly to Zero Emojis and >= 48dp accessible touch targets.
 */
@Composable
fun EditorScreen(
    projectId: String,
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExport: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        if (uiState.project == null || uiState.project?.id != projectId) {
            viewModel.loadProject(projectId)
        }
    }

    val activeProject = uiState.project ?: return

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopToolbar(
                title = activeProject.title,
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                aspectRatio = activeProject.aspectRatio,
                onBackClick = onNavigateBack,
                onUndoClick = { viewModel.undo() },
                onRedoClick = { viewModel.redo() },
                onTitleChanged = { viewModel.updateProjectTitle(it) },
                onAspectRatioChanged = { viewModel.setAspectRatio(it) },
                onExportClick = { onNavigateToExport(projectId) },
                onOpenPromptSheet = { viewModel.showPromptSheet() },
                onOpenCodeRunnerSheet = { viewModel.showCodeRunnerSheet() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Preview Canvas Area (Framed video container, safe guidelines, transport controls)
            PreviewCanvasArea(
                aspectRatio = activeProject.aspectRatio,
                timecode = uiState.formattedTimecode,
                totalDuration = uiState.formattedDuration,
                isPlaying = uiState.isPlaying,
                areGuideLinesVisible = uiState.areGuideLinesVisible,
                onPlayPauseClick = { viewModel.togglePlayPause() },
                onStepForwardClick = { viewModel.stepFrameForward() },
                onStepBackwardClick = { viewModel.stepFrameBackward() },
                onToggleGuideLines = { viewModel.toggleGuideLines() },
                player = viewModel.playbackCoordinator?.player,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.48f)
            )

            // Multitrack Timeline Area (Toolbar, ruler, tracks, scrubber, zoom)
            MultitrackTimelineArea(
                project = activeProject,
                playheadUs = uiState.playheadUs,
                zoomLevel = uiState.zoomLevel,
                isSnappingEnabled = uiState.isSnappingEnabled,
                selectedClipId = uiState.selectedClip?.id,
                onPlayheadChanged = { targetUs, isScrubbing ->
                    viewModel.setPlayheadUs(targetUs, isScrubbing)
                },
                onSplitClick = { viewModel.splitSelectedClipAtPlayhead() },
                onTrimLeftClick = { viewModel.trimSelectedClipInToPlayhead() },
                onTrimRightClick = { viewModel.trimSelectedClipOutToPlayhead() },
                onDeleteClick = { viewModel.deleteSelectedClip() },
                onSnapToggle = { viewModel.toggleSnapping() },
                onZoomFitClick = { viewModel.zoomFit(1080f) },
                onZoomLevelChanged = { viewModel.setZoomLevel(it) },
                onSelectClip = { clip, trackId, trackType ->
                    viewModel.selectClip(clip, trackId, trackType)
                },
                onDeselect = { viewModel.deselectClip() },
                onRepositionClip = { clip, newStartUs ->
                    viewModel.repositionSelectedClip(newStartUs)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.52f)
            )

            // Contextual Properties Inspector (Collapses when no clip selected)
            ContextualPropertiesInspector(
                selectedClip = uiState.selectedClip,
                trackType = uiState.selectedTrackType,
                activeTab = uiState.activeInspectorTab,
                onTabSelected = { viewModel.setActiveInspectorTab(it) },
                onTransformChanged = { viewModel.updateClipTransform(it) },
                onAudioChanged = { viewModel.updateClipAudio(it) },
                onSpeedChanged = { viewModel.updateClipSpeed(it) },
                onColorChanged = { viewModel.updateClipColor(it) },
                onFilterChanged = { viewModel.updateClipFilter(it) },
                onSpeedCurveChanged = { viewModel.updateClipSpeedCurve(it) },
                onCloseClick = { viewModel.deselectClip() }
            )
        }

        // AI Sheets
        if (uiState.isPromptSheetVisible) {
            TranscriptPromptSheet(
                project = activeProject,
                segments = emptyList(),
                onDismiss = { viewModel.hidePromptSheet() },
                onNavigateToCodeRunner = {
                    viewModel.hidePromptSheet()
                    viewModel.showCodeRunnerSheet()
                }
            )
        }

        if (uiState.isCodeRunnerSheetVisible) {
            AiCodeRunnerSheet(
                onDismiss = { viewModel.hideCodeRunnerSheet() },
                onExecuteScript = { viewModel.executeAiScript(it) }
            )
        }
    }
}
