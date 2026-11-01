package com.cutpro.feature.editor

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TrackType
import java.util.Locale

data class EditorUiState(
    val project: ProjectManifest? = null,
    val selectedClip: TimelineClip? = null,
    val selectedTrackId: String? = null,
    val selectedTrackType: TrackType? = null,
    val playheadUs: Long = 0L,
    val isPlaying: Boolean = false,
    val isScrubbing: Boolean = false,
    val zoomLevel: Float = 1.0f, // 0.1f (10%) to 5.0f (500%)
    val isSnappingEnabled: Boolean = true,
    val areGuideLinesVisible: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val activeInspectorTab: String = "Transform",
    val isEditingTitle: Boolean = false,
    val titleInput: String = ""
) {
    val isInspectorCollapsed: Boolean get() = selectedClip == null

    val durationUs: Long get() = project?.durationUs ?: 0L

    val formattedTimecode: String
        get() {
            val totalSec = playheadUs / 1_000_000L
            val frames = ((playheadUs % 1_000_000L) / 1_000_000.0 * 30.0).toInt()
            val min = totalSec / 60L
            val sec = totalSec % 60L
            return String.format(Locale.US, "%02d:%02d:%02d", min, sec, frames)
        }

    val formattedDuration: String
        get() {
            val totalSec = durationUs / 1_000_000L
            val frames = ((durationUs % 1_000_000L) / 1_000_000.0 * 30.0).toInt()
            val min = totalSec / 60L
            val sec = totalSec % 60L
            return String.format(Locale.US, "%02d:%02d:%02d", min, sec, frames)
        }

    fun getInspectorTabsForSelectedClip(): List<String> {
        return when (selectedTrackType) {
            TrackType.VIDEO, TrackType.OVERLAY -> listOf("Transform", "Audio", "Speed", "Cut")
            TrackType.AUDIO -> listOf("Volume", "Fades", "Speed")
            TrackType.TEXT, TrackType.CAPTION -> listOf("Typography", "Animation", "Style")
            null -> emptyList()
        }
    }
}
