package com.cutpro.feature.editor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutpro.core.ai.gate.PlanExecutionEngine
import com.cutpro.core.ai.script.CutProScriptCompiler
import com.cutpro.core.ai.script.CutProScriptParser
import com.cutpro.core.data.database.dao.ClipDao
import com.cutpro.core.data.database.dao.ProjectDao
import com.cutpro.core.data.database.dao.TrackDao
import com.cutpro.core.data.database.mapper.toDomain
import com.cutpro.core.data.database.mapper.toEntity
import com.cutpro.core.data.media.MediaImportManager
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ClipAudioProperties
import com.cutpro.core.model.ClipFitMode
import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.ColorAdjustments
import com.cutpro.core.model.LutFilterType
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.SpeedCurve
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.core.timeline.StandardTimelineEngine
import com.cutpro.core.timeline.TimelineEngine
import com.cutpro.core.timeline.TimelineOperations
import com.cutpro.core.timeline.command.AddClipCommand
import com.cutpro.core.timeline.command.DeleteClipCommand
import com.cutpro.core.timeline.command.RepositionClipCommand
import com.cutpro.core.timeline.command.SnapshotTimelineCommand
import com.cutpro.core.timeline.command.SplitClipCommand
import com.cutpro.core.timeline.command.TrimClipCommand
import com.cutpro.core.timeline.command.UpdateClipCommand
import com.cutpro.core.timeline.undo.UndoRedoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.cutpro.core.playback.PlaybackCoordinator
import java.util.UUID

class EditorViewModel(
    private val projectDao: ProjectDao? = null,
    private val trackDao: TrackDao? = null,
    private val clipDao: ClipDao? = null,
    private val timelineEngine: TimelineEngine = StandardTimelineEngine(),
    private val undoRedoManager: UndoRedoManager = UndoRedoManager(maxHistoryLimit = 50),
    val playbackCoordinator: PlaybackCoordinator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        if (playbackCoordinator != null) {
            viewModelScope.launch {
                playbackCoordinator.playheadUs.collect { currentUs ->
                    _uiState.update { it.copy(playheadUs = currentUs) }
                }
            }
            viewModelScope.launch {
                playbackCoordinator.isPlaying.collect { playing ->
                    _uiState.update { it.copy(isPlaying = playing) }
                }
            }
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            if (projectDao != null) {
                val projectWithTracks = projectDao.getProjectWithTracks(projectId)
                if (projectWithTracks != null) {
                    val domainProject = projectWithTracks.toDomain()
                    setProject(domainProject)
                    return@launch
                }
            }
            // Fallback default project
            createDefaultProject(projectId)
        }
    }

    fun setProject(project: ProjectManifest) {
        undoRedoManager.clear()
        playbackCoordinator?.setTimeline(project)
        _uiState.update {
            it.copy(
                project = project,
                playheadUs = 0L,
                canUndo = undoRedoManager.canUndo,
                canRedo = undoRedoManager.canRedo
            )
        }
    }

    private fun createDefaultProject(projectId: String) {
        val sampleMedia = MediaSourceInfo(
            uriString = "content://media/sample_video.mp4",
            fileName = "sample_video.mp4",
            mimeType = "video/mp4",
            durationUs = 10_000_000L,
            width = 1920,
            height = 1080
        )
        val initialClip = TimelineClip(
            id = UUID.randomUUID().toString(),
            trackId = "track_v1",
            mediaSource = sampleMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 6_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 6_000_000L
        )
        val videoTrack = TimelineTrack(
            id = "track_v1",
            name = "Video 1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(initialClip)
        )
        val audioTrack = TimelineTrack(
            id = "track_a1",
            name = "Audio 1",
            type = TrackType.AUDIO,
            zIndex = 1,
            clips = emptyList()
        )
        val manifest = ProjectManifest(
            id = projectId,
            title = "New Project",
            durationUs = initialClip.timelineDurationUs,
            tracks = listOf(videoTrack, audioTrack)
        )
        setProject(manifest)
    }

    fun setPlayheadUs(requestedUs: Long, isScrubbing: Boolean) {
        val maxDurationUs = _uiState.value.durationUs
        val clampedUs = requestedUs.coerceIn(0L, maxOf(0L, maxDurationUs))
        _uiState.update {
            it.copy(
                playheadUs = clampedUs,
                isScrubbing = isScrubbing
            )
        }
        playbackCoordinator?.seekTo(clampedUs, isScrubbing)
    }

    fun stepFrameForward(fps: Int = 30) {
        val frameDurationUs = (1_000_000L / fps.coerceAtLeast(1))
        setPlayheadUs(_uiState.value.playheadUs + frameDurationUs, isScrubbing = false)
    }

    fun stepFrameBackward(fps: Int = 30) {
        val frameDurationUs = (1_000_000L / fps.coerceAtLeast(1))
        setPlayheadUs(_uiState.value.playheadUs - frameDurationUs, isScrubbing = false)
    }

    fun togglePlayPause() {
        if (playbackCoordinator != null) {
            if (playbackCoordinator.isPlaying.value) {
                playbackCoordinator.pause()
            } else {
                playbackCoordinator.play()
            }
        } else {
            _uiState.update { it.copy(isPlaying = !it.isPlaying) }
        }
    }

    fun selectClip(clip: TimelineClip?, trackId: String?, trackType: TrackType?) {
        _uiState.update {
            val tabs = when (trackType) {
                TrackType.VIDEO, TrackType.OVERLAY -> listOf("Transform", "Audio", "Speed", "Cut")
                TrackType.AUDIO -> listOf("Volume", "Fades", "Speed")
                TrackType.TEXT, TrackType.CAPTION -> listOf("Typography", "Animation", "Style")
                null -> emptyList()
            }
            it.copy(
                selectedClip = clip,
                selectedTrackId = trackId,
                selectedTrackType = trackType,
                activeInspectorTab = tabs.firstOrNull() ?: "Transform"
            )
        }
    }

    fun deselectClip() {
        _uiState.update {
            it.copy(
                selectedClip = null,
                selectedTrackId = null,
                selectedTrackType = null
            )
        }
    }

    fun setActiveInspectorTab(tabName: String) {
        _uiState.update { it.copy(activeInspectorTab = tabName) }
    }

    fun setZoomLevel(zoom: Float) {
        // Clamping to [0.1, 5.0] (10% to 500%)
        val clampedZoom = zoom.coerceIn(0.1f, 5.0f)
        _uiState.update { it.copy(zoomLevel = clampedZoom) }
    }

    fun zoomFit(viewportWidthPx: Float) {
        val durationUs = _uiState.value.durationUs
        if (durationUs <= 0L) {
            setZoomLevel(1.0f)
            return
        }
        val targetPixelsPerSec = (viewportWidthPx / (durationUs / 1_000_000.0f))
        val fitZoom = (targetPixelsPerSec / 100f).coerceIn(0.1f, 5.0f)
        _uiState.update { it.copy(zoomLevel = fitZoom) }
    }

    fun toggleSnapping() {
        _uiState.update { it.copy(isSnappingEnabled = !it.isSnappingEnabled) }
    }

    fun toggleGuideLines() {
        _uiState.update { it.copy(areGuideLinesVisible = !it.areGuideLinesVisible) }
    }

    fun setAspectRatio(ratio: CanvasAspectRatio) {
        val currentProject = _uiState.value.project ?: return
        val updated = currentProject.copy(
            aspectRatio = ratio,
            modifiedAt = System.currentTimeMillis()
        )
        val cmd = SnapshotTimelineCommand("Change Aspect Ratio", currentProject, updated)
        applyCommand(cmd)
    }

    fun updateProjectTitle(newTitle: String) {
        val clampedTitle = if (newTitle.length > 100) newTitle.take(100) else newTitle
        val currentProject = _uiState.value.project ?: return
        val updated = currentProject.copy(
            title = clampedTitle,
            modifiedAt = System.currentTimeMillis()
        )
        val cmd = SnapshotTimelineCommand("Update Project Title", currentProject, updated)
        applyCommand(cmd)
    }

    fun splitSelectedClipAtPlayhead(minClipDurationUs: Long = 100_000L) {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val playheadUs = _uiState.value.playheadUs

        if (playheadUs < clip.timelineStartUs + minClipDurationUs ||
            playheadUs > clip.timelineEndUs - minClipDurationUs
        ) {
            return
        }

        val (left, right) = timelineEngine.splitAt(clip, playheadUs, minClipDurationUs)
        val cmd = SplitClipCommand(trackId, clip, left, right)
        applyCommand(cmd)
        selectClip(right, trackId, _uiState.value.selectedTrackType)
    }

    fun trimSelectedClipInToPlayhead(minClipDurationUs: Long = 100_000L) {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val playheadUs = _uiState.value.playheadUs

        val offsetTimelineUs = playheadUs - clip.timelineStartUs
        val requestedInUs = clip.sourceInUs + (offsetTimelineUs * clip.speedMultiplier).toLong()

        val trimmed = timelineEngine.trimIn(clip, requestedInUs, minClipDurationUs)
        val cmd = TrimClipCommand(trackId, clip, trimmed)
        applyCommand(cmd)
        selectClip(trimmed, trackId, _uiState.value.selectedTrackType)
    }

    fun trimSelectedClipOutToPlayhead(minClipDurationUs: Long = 100_000L) {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val playheadUs = _uiState.value.playheadUs

        val offsetTimelineUs = playheadUs - clip.timelineStartUs
        val requestedOutUs = clip.sourceInUs + (offsetTimelineUs * clip.speedMultiplier).toLong()

        val trimmed = timelineEngine.trimOut(clip, requestedOutUs, minClipDurationUs)
        val cmd = TrimClipCommand(trackId, clip, trimmed)
        applyCommand(cmd)
        selectClip(trimmed, trackId, _uiState.value.selectedTrackType)
    }

    fun deleteSelectedClip() {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val cmd = DeleteClipCommand(trackId, clip)
        applyCommand(cmd)
        deselectClip()
    }

    fun repositionSelectedClip(newTimelineStartUs: Long, densityDpi: Float = 160f) {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val project = _uiState.value.project ?: return

        var targetStartUs = maxOf(0L, newTimelineStartUs)

        // Snapping if enabled
        if (_uiState.value.isSnappingEnabled) {
            val candidates = TimelineOperations.collectCandidateSnapPoints(
                project = project,
                playheadUs = _uiState.value.playheadUs,
                excludeClipId = clip.id
            )
            val pixelsPerSec = 100f * _uiState.value.zoomLevel
            val snapped = timelineEngine.findSnapPoint(
                movingEdgeUs = targetStartUs,
                candidatePointsUs = candidates,
                pixelsPerSec = pixelsPerSec,
                densityDpi = densityDpi
            )
            if (snapped != null) {
                targetStartUs = snapped
            }
        }

        val repositioned = timelineEngine.reposition(clip, targetStartUs)
        val cmd = RepositionClipCommand(trackId, trackId, clip, repositioned)
        applyCommand(cmd)
        selectClip(repositioned, trackId, _uiState.value.selectedTrackType)
    }

    fun updateClipTransform(transform: ClipTransform) {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val updated = clip.copy(transform = transform)
        val cmd = UpdateClipCommand(trackId, clip, updated, "Update Transform")
        applyCommand(cmd)
        selectClip(updated, trackId, _uiState.value.selectedTrackType)
    }

    fun updateClipAudio(audioProperties: ClipAudioProperties) {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val updated = clip.copy(audioProperties = audioProperties)
        val cmd = UpdateClipCommand(trackId, clip, updated, "Update Audio")
        applyCommand(cmd)
        selectClip(updated, trackId, _uiState.value.selectedTrackType)
    }

    fun updateClipSpeed(speedMultiplier: Float) {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val clampedSpeed = speedMultiplier.coerceIn(0.1f, 10.0f)
        val updated = timelineEngine.adjustSpeed(clip, clampedSpeed)
        val cmd = UpdateClipCommand(trackId, clip, updated, "Update Speed")
        applyCommand(cmd)
        selectClip(updated, trackId, _uiState.value.selectedTrackType)
    }

    fun updateClipColor(adjustments: ColorAdjustments) {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val updated = clip.copy(colorAdjustments = adjustments)
        val cmd = UpdateClipCommand(trackId, clip, updated, "Update Color")
        applyCommand(cmd)
        selectClip(updated, trackId, _uiState.value.selectedTrackType)
    }

    fun updateClipFilter(filterType: LutFilterType) {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val updated = clip.copy(filterType = filterType)
        val cmd = UpdateClipCommand(trackId, clip, updated, "Update Filter: ${filterType.displayName}")
        applyCommand(cmd)
        selectClip(updated, trackId, _uiState.value.selectedTrackType)
    }

    fun updateClipSpeedCurve(curve: SpeedCurve) {
        val clip = _uiState.value.selectedClip ?: return
        val trackId = _uiState.value.selectedTrackId ?: return
        val updated = clip.copy(speedCurve = curve)
        val cmd = UpdateClipCommand(trackId, clip, updated, "Update Speed Curve: ${curve.preset.name}")
        applyCommand(cmd)
        selectClip(updated, trackId, _uiState.value.selectedTrackType)
    }

    fun showPromptSheet() {
        _uiState.update { it.copy(isPromptSheetVisible = true) }
    }

    fun hidePromptSheet() {
        _uiState.update { it.copy(isPromptSheetVisible = false) }
    }

    fun showCodeRunnerSheet() {
        _uiState.update { it.copy(isCodeRunnerSheetVisible = true) }
    }

    fun hideCodeRunnerSheet() {
        _uiState.update { it.copy(isCodeRunnerSheetVisible = false) }
    }

    fun executeAiScript(scriptText: String): Result<Int> {
        val current = _uiState.value.project ?: return Result.failure(IllegalStateException("No active project"))
        return try {
            val ast = CutProScriptParser.parse(scriptText)
            val plan = CutProScriptCompiler.compile(ast, current)
            val updated = PlanExecutionEngine.executeActions(current, plan.actions)
            val cmd = SnapshotTimelineCommand("AI Script: ${plan.actions.size} actions", current, updated)
            applyCommand(cmd)
            Result.success(plan.actions.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun importAndAddMedia(
        context: Context,
        uris: List<Uri>,
        targetTrackType: TrackType = TrackType.VIDEO,
        insertAtPlayhead: Boolean = true
    ) {
        if (uris.isEmpty()) return
        val currentProject = _uiState.value.project ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isImportingMedia = true, importStatusMessage = "Importing media...") }
            try {
                val importManager = MediaImportManager.create(context)
                var workingProject = currentProject

                // Ensure target track exists
                var targetTrack = workingProject.tracks.firstOrNull { it.type == targetTrackType }
                if (targetTrack == null) {
                    val newTrack = TimelineTrack(
                        id = UUID.randomUUID().toString(),
                        name = when (targetTrackType) {
                            TrackType.OVERLAY -> "Overlay 1"
                            TrackType.AUDIO -> "Audio 2"
                            TrackType.VIDEO -> "Video 2"
                            else -> "Track ${workingProject.tracks.size + 1}"
                        },
                        type = targetTrackType,
                        zIndex = workingProject.tracks.size
                    )
                    workingProject = workingProject.copy(tracks = workingProject.tracks + newTrack)
                    targetTrack = newTrack
                }

                val currentTrackClips = workingProject.tracks.firstOrNull { it.id == targetTrack.id }?.clips ?: emptyList()
                var currentInsertionUs = if (insertAtPlayhead) {
                    _uiState.value.playheadUs
                } else {
                    currentTrackClips.maxOfOrNull { it.timelineEndUs } ?: 0L
                }

                var lastAddedClip: TimelineClip? = null

                for (uri in uris) {
                    val result = importManager.importMedia(workingProject.id, uri)
                    if (result.isSuccess) {
                        val mediaInfo = result.getOrThrow()
                        val clipDurationUs = if (mediaInfo.mimeType.startsWith("image/")) {
                            3_000_000L // 3.0s default image duration
                        } else {
                            mediaInfo.durationUs
                        }

                        val newClip = TimelineClip(
                            id = UUID.randomUUID().toString(),
                            trackId = targetTrack.id,
                            mediaSource = mediaInfo,
                            timelineStartUs = currentInsertionUs,
                            timelineDurationUs = clipDurationUs,
                            sourceInUs = 0L,
                            sourceOutUs = clipDurationUs
                        )

                        val cmd = AddClipCommand(targetTrack.id, newClip, "Add Media: ${mediaInfo.fileName}")
                        workingProject = undoRedoManager.execute(cmd, workingProject)
                        currentInsertionUs += clipDurationUs
                        lastAddedClip = newClip
                    }
                }

                playbackCoordinator?.setTimeline(workingProject)
                _uiState.update {
                    it.copy(
                        project = workingProject,
                        canUndo = undoRedoManager.canUndo,
                        canRedo = undoRedoManager.canRedo,
                        isImportingMedia = false,
                        importStatusMessage = ""
                    )
                }

                if (lastAddedClip != null) {
                    selectClip(lastAddedClip, targetTrack.id, targetTrack.type)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isImportingMedia = false, importStatusMessage = "") }
            }
        }
    }

    fun undo() {
        val current = _uiState.value.project ?: return
        val reverted = undoRedoManager.undo(current) ?: return
        playbackCoordinator?.setTimeline(reverted)
        _uiState.update {
            it.copy(
                project = reverted,
                canUndo = undoRedoManager.canUndo,
                canRedo = undoRedoManager.canRedo
            )
        }
        refreshSelectedClipAfterMutation(reverted)
    }

    fun redo() {
        val current = _uiState.value.project ?: return
        val redone = undoRedoManager.redo(current) ?: return
        playbackCoordinator?.setTimeline(redone)
        _uiState.update {
            it.copy(
                project = redone,
                canUndo = undoRedoManager.canUndo,
                canRedo = undoRedoManager.canRedo
            )
        }
        refreshSelectedClipAfterMutation(redone)
    }

    private fun applyCommand(command: com.cutpro.core.timeline.command.TimelineCommand) {
        val current = _uiState.value.project ?: return
        val updated = undoRedoManager.execute(command, current)
        playbackCoordinator?.setTimeline(updated)
        _uiState.update {
            it.copy(
                project = updated,
                canUndo = undoRedoManager.canUndo,
                canRedo = undoRedoManager.canRedo
            )
        }
    }

    private fun refreshSelectedClipAfterMutation(updatedProject: ProjectManifest) {
        val selectedId = _uiState.value.selectedClip?.id ?: return
        val track = updatedProject.tracks.find { it.id == _uiState.value.selectedTrackId }
        val refreshedClip = track?.clips?.find { it.id == selectedId }
        _uiState.update {
            it.copy(selectedClip = refreshedClip)
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackCoordinator?.release()
    }
}
