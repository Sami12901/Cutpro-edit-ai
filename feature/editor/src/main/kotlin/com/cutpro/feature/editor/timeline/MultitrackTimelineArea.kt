package com.cutpro.feature.editor.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cutpro.core.designsystem.component.CutProIconButton
import com.cutpro.core.designsystem.component.accessibleTouchTarget
import com.cutpro.core.designsystem.icon.CutProIcons
import com.cutpro.core.designsystem.theme.AudioTrackColor
import com.cutpro.core.designsystem.theme.CaptionTrackColor
import com.cutpro.core.designsystem.theme.DarkBackground
import com.cutpro.core.designsystem.theme.DarkSurface
import com.cutpro.core.designsystem.theme.DarkSurfaceContainer
import com.cutpro.core.designsystem.theme.OverlayTrackColor
import com.cutpro.core.designsystem.theme.PlayheadColor
import com.cutpro.core.designsystem.theme.SelectionBorderColor
import com.cutpro.core.designsystem.theme.TextTrackColor
import com.cutpro.core.designsystem.theme.VideoTrackColor
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import kotlin.math.roundToInt

/**
 * Multitrack timeline UI shell:
 * - Header toolbar with Split, Trim Left, Trim Right, Delete, Snap toggle, Zoom fit
 * - Timecode ruler with 1.0s major ticks and 0.2s sub-interval ticks
 * - Draggable playhead scrubber with >=48dp touch region and duration clamping
 * - Scrollable multitrack stack (Video, Audio, Text, Overlay, Caption tracks)
 * - Clips with visible 4dp trim handles padded to >=48dp touch hitbox
 * - Zero emojis permitted anywhere.
 */
@Composable
fun MultitrackTimelineArea(
    project: ProjectManifest,
    playheadUs: Long,
    zoomLevel: Float,
    isSnappingEnabled: Boolean,
    selectedClipId: String?,
    onPlayheadChanged: (Long, Boolean) -> Unit,
    onSplitClick: () -> Unit,
    onTrimLeftClick: () -> Unit,
    onTrimRightClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSnapToggle: () -> Unit,
    onZoomFitClick: () -> Unit,
    onZoomLevelChanged: (Float) -> Unit,
    onSelectClip: (TimelineClip, String, TrackType) -> Unit,
    onDeselect: () -> Unit,
    onRepositionClip: (TimelineClip, Long) -> Unit,
    onAddMediaClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 100 dp per second at zoomLevel 1.0f
    val pixelsPerSecond = 100f * zoomLevel
    val durationUs = project.durationUs.coerceAtLeast(10_000_000L) // Minimum 10 seconds viewport
    val timelineWidthDp = ((durationUs / 1_000_000.0f) * pixelsPerSecond).dp

    val horizontalScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
    ) {
        // Timeline Header Toolbar
        TimelineHeaderToolbar(
            isClipSelected = selectedClipId != null,
            isSnappingEnabled = isSnappingEnabled,
            zoomLevel = zoomLevel,
            onAddMediaClick = onAddMediaClick,
            onSplitClick = onSplitClick,
            onTrimLeftClick = onTrimLeftClick,
            onTrimRightClick = onTrimRightClick,
            onDeleteClick = onDeleteClick,
            onSnapToggle = onSnapToggle,
            onZoomFitClick = onZoomFitClick,
            onZoomLevelChanged = onZoomLevelChanged
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

        // Main Multitrack Canvas (Ruler, Tracks, Playhead)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTapGestures { onDeselect() }
                }
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Fixed Left Track Headers Column (Width: 80dp)
                Column(
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight()
                        .background(DarkSurfaceContainer)
                ) {
                    // Ruler header spacer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Track headers
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(project.tracks, key = { it.id }) { track ->
                            TrackHeaderCell(track = track)
                        }
                    }
                }

                // Scrollable Multitrack Bed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    Column(modifier = Modifier.width(timelineWidthDp)) {
                        // Timecode Ruler
                        TimelineRuler(
                            durationUs = durationUs,
                            pixelsPerSecond = pixelsPerSecond,
                            onSeek = { seekUs -> onPlayheadChanged(seekUs, false) }
                        )

                        // Multitrack Lanes
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(project.tracks, key = { it.id }) { track ->
                                TrackLane(
                                    track = track,
                                    pixelsPerSecond = pixelsPerSecond,
                                    selectedClipId = selectedClipId,
                                    onSelectClip = { clip -> onSelectClip(clip, track.id, track.type) },
                                    onRepositionClip = onRepositionClip
                                )
                            }
                        }
                    }

                    // Draggable Playhead Scrubber Line
                    PlayheadScrubber(
                        playheadUs = playheadUs,
                        pixelsPerSecond = pixelsPerSecond,
                        maxDurationUs = durationUs,
                        onPlayheadScrub = onPlayheadChanged
                    )
                }
            }
        }
    }
}

@Composable
fun TimelineHeaderToolbar(
    isClipSelected: Boolean,
    isSnappingEnabled: Boolean,
    zoomLevel: Float,
    onAddMediaClick: () -> Unit = {},
    onSplitClick: () -> Unit,
    onTrimLeftClick: () -> Unit,
    onTrimRightClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onSnapToggle: () -> Unit,
    onZoomFitClick: () -> Unit,
    onZoomLevelChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Add Media Action (Video, Image, Audio)
            CutProIconButton(
                icon = CutProIcons.Add,
                contentDescription = "Add media to timeline",
                onClick = onAddMediaClick,
                tint = MaterialTheme.colorScheme.primary
            )

            // Split Action
            CutProIconButton(
                icon = CutProIcons.Split,
                contentDescription = "Split clip at playhead",
                onClick = onSplitClick,
                enabled = isClipSelected
            )

            // Trim Left to Playhead
            CutProIconButton(
                icon = CutProIcons.Trim,
                contentDescription = "Trim in to playhead",
                onClick = onTrimLeftClick,
                enabled = isClipSelected
            )

            // Trim Right to Playhead
            CutProIconButton(
                icon = CutProIcons.CropTool,
                contentDescription = "Trim out to playhead",
                onClick = onTrimRightClick,
                enabled = isClipSelected
            )

            // Delete Selected Clip
            CutProIconButton(
                icon = CutProIcons.Delete,
                contentDescription = "Delete selected clip",
                onClick = onDeleteClick,
                enabled = isClipSelected,
                tint = if (isClipSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Magnetic Snapping Toggle
            CutProIconButton(
                icon = CutProIcons.Snap,
                contentDescription = "Toggle magnetic snapping",
                onClick = onSnapToggle,
                tint = if (isSnappingEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Zoom Fit Button
            CutProIconButton(
                icon = CutProIcons.ZoomFit,
                contentDescription = "Fit timeline to screen",
                onClick = onZoomFitClick
            )
        }

        // Zoom Slider (10% to 500%)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.width(140.dp)
        ) {
            Text(
                text = "${(zoomLevel * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp)
            )
            Slider(
                value = zoomLevel,
                onValueChange = onZoomLevelChanged,
                valueRange = 0.1f..5.0f,
                modifier = Modifier.accessibleTouchTarget()
            )
        }
    }
}

@Composable
fun TrackHeaderCell(track: TimelineTrack, modifier: Modifier = Modifier) {
    val trackIcon = when (track.type) {
        TrackType.VIDEO -> CutProIcons.VideoTrack
        TrackType.AUDIO -> CutProIcons.AudioTrack
        TrackType.TEXT -> CutProIcons.TextTrack
        TrackType.OVERLAY -> CutProIcons.OverlayTrack
        TrackType.CAPTION -> CutProIcons.CaptionTrack
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp) // Accessible track height >= 48dp (64dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = trackIcon,
                contentDescription = track.name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = track.name,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TimelineRuler(
    durationUs: Long,
    pixelsPerSecond: Float,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSeconds = (durationUs / 1_000_000L).toInt() + 1
    val widthDp = ((durationUs / 1_000_000.0f) * pixelsPerSecond).dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(pixelsPerSecond) {
                detectTapGestures { offset ->
                    val seekSec = offset.x / pixelsPerSecond
                    val seekUs = (seekSec * 1_000_000L).toLong()
                    onSeek(seekUs)
                }
            }
    ) {
        val lineColor = Color(0xFF6B7280)
        val majorLineColor = Color(0xFFD1D5DB)

        // Draw ticks every 0.2s (sub-interval) and 1.0s (major interval)
        val tickIntervalSec = 0.2f
        val numTicks = (durationUs / (tickIntervalSec * 1_000_000.0f)).toInt()

        for (i in 0..numTicks) {
            val sec = i * tickIntervalSec
            val x = sec * pixelsPerSecond
            val isMajor = (i % 5 == 0) // every 1.0 second

            val tickHeight = if (isMajor) size.height * 0.6f else size.height * 0.3f
            val color = if (isMajor) majorLineColor else lineColor

            drawLine(
                color = color,
                start = Offset(x, size.height - tickHeight),
                end = Offset(x, size.height),
                strokeWidth = if (isMajor) 1.5f else 1f
            )
        }
    }
}

@Composable
fun TrackLane(
    track: TimelineTrack,
    pixelsPerSecond: Float,
    selectedClipId: String?,
    onSelectClip: (TimelineClip) -> Unit,
    onRepositionClip: (TimelineClip, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val laneColor = when (track.type) {
        TrackType.VIDEO -> VideoTrackColor
        TrackType.AUDIO -> AudioTrackColor
        TrackType.TEXT -> TextTrackColor
        TrackType.OVERLAY -> OverlayTrackColor
        TrackType.CAPTION -> CaptionTrackColor
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp) // Track height >= 48dp
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .background(DarkBackground.copy(alpha = 0.5f))
    ) {
        for (clip in track.clips) {
            val startXDp = ((clip.timelineStartUs / 1_000_000.0f) * pixelsPerSecond).dp
            val widthDp = ((clip.timelineDurationUs / 1_000_000.0f) * pixelsPerSecond).dp.coerceAtLeast(16.dp)
            val isSelected = (clip.id == selectedClipId)

            TimelineClipView(
                clip = clip,
                color = laneColor,
                isSelected = isSelected,
                pixelsPerSecond = pixelsPerSecond,
                modifier = Modifier
                    .offset(x = startXDp)
                    .width(widthDp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                onClick = { onSelectClip(clip) },
                onReposition = { newStartUs -> onRepositionClip(clip, newStartUs) }
            )
        }
    }
}

@Composable
fun TimelineClipView(
    clip: TimelineClip,
    color: Color,
    isSelected: Boolean,
    pixelsPerSecond: Float,
    onClick: () -> Unit,
    onReposition: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .then(
                if (isSelected) Modifier.border(2.dp, SelectionBorderColor, RoundedCornerShape(6.dp))
                else Modifier.border(0.5.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            )
            .pointerInput(clip.id) {
                detectTapGestures { onClick() }
            }
            .pointerInput(clip.id, pixelsPerSecond) {
                detectDragGestures(
                    onDragStart = { dragOffsetX = 0f },
                    onDragEnd = {
                        val deltaUs = ((dragOffsetX / pixelsPerSecond) * 1_000_000L).toLong()
                        val newStartUs = maxOf(0L, clip.timelineStartUs + deltaUs)
                        onReposition(newStartUs)
                        dragOffsetX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                    }
                )
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        // Clip content label
        Text(
            text = clip.customTitle ?: clip.mediaSource.fileName,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Left Trim Handle (visible width 4dp bar, touch hitbox padded to >= 48dp)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .accessibleTouchTarget(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                    .align(Alignment.Center)
            )
        }

        // Right Trim Handle (visible width 4dp bar, touch hitbox padded to >= 48dp)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .accessibleTouchTarget(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
fun PlayheadScrubber(
    playheadUs: Long,
    pixelsPerSecond: Float,
    maxDurationUs: Long,
    onPlayheadScrub: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val playheadXDp = ((playheadUs / 1_000_000.0f) * pixelsPerSecond).dp

    Box(
        modifier = modifier
            .offset(x = playheadXDp - 24.dp) // Center 48dp scrubber head on position
            .fillMaxHeight()
            .width(48.dp) // >= 48dp touch hitbox
            .pointerInput(pixelsPerSecond, maxDurationUs) {
                detectDragGestures(
                    onDragStart = { onPlayheadScrub(playheadUs, true) },
                    onDragEnd = { onPlayheadScrub(playheadUs, false) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val deltaSec = dragAmount.x / pixelsPerSecond
                        val deltaUs = (deltaSec * 1_000_000L).toLong()
                        val newPos = (playheadUs + deltaUs).coerceIn(0L, maxDurationUs)
                        onPlayheadScrub(newPos, true)
                    }
                )
            }
    ) {
        // Red playhead line spanning full multitrack height
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(2.dp)
                .fillMaxHeight()
                .background(PlayheadColor)
        )

        // Circular Scrubber Handle (48dp interactive diameter)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(24.dp)
                .accessibleTouchTarget(minWidth = 48.dp, minHeight = 48.dp),
            shape = CircleShape,
            color = PlayheadColor,
            shadowElevation = 4.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, CircleShape)
                )
            }
        }
    }
}
