package com.cutpro.feature.editor.inspector

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

import com.cutpro.core.designsystem.component.CutProButton
import com.cutpro.core.designsystem.component.CutProIconButton
import com.cutpro.core.designsystem.component.DurationBadge
import com.cutpro.core.designsystem.component.accessibleTouchTarget
import com.cutpro.core.designsystem.icon.CutProIcons
import com.cutpro.core.model.ClipAudioProperties
import com.cutpro.core.model.ClipFitMode
import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TrackType
import java.util.Locale

/**
 * Contextual properties inspector:
 * - Reactive bottom sheet / panel updating controls based on selected clip type
 * - Video: Transform, Audio, Speed, Cut (4 tabs)
 * - Audio: Volume, Fades, Speed (3 tabs)
 * - Text: Typography, Animation, Style (3 tabs)
 * - Collapses completely when no clip is selected
 * - All controls have >= 48dp accessible touch targets
 * - Zero emojis permitted anywhere.
 */
@Composable
fun ContextualPropertiesInspector(
    selectedClip: TimelineClip?,
    trackType: TrackType?,
    activeTab: String,
    onTabSelected: (String) -> Unit,
    onTransformChanged: (ClipTransform) -> Unit,
    onAudioChanged: (ClipAudioProperties) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible = selectedClip != null

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier
    ) {
        if (selectedClip == null) return@AnimatedVisibility

        val tabs = when (trackType) {
            TrackType.VIDEO, TrackType.OVERLAY -> listOf("Transform", "Audio", "Speed", "Cut")
            TrackType.AUDIO -> listOf("Volume", "Fades", "Speed")
            TrackType.TEXT, TrackType.CAPTION -> listOf("Typography", "Animation", "Style")
            null -> emptyList()
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header: Clip Title, Duration badge, Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = selectedClip.customTitle ?: selectedClip.mediaSource.fileName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        DurationBadge(
                            formattedDuration = formatDuration(selectedClip.timelineDurationUs)
                        )
                    }

                    CutProIconButton(
                        icon = CutProIcons.Close,
                        contentDescription = "Collapse inspector",
                        onClick = onCloseClick
                    )
                }

                // Dynamic Inspector Tabs
                if (tabs.isNotEmpty()) {
                    val selectedTabIndex = tabs.indexOf(activeTab).coerceAtLeast(0)
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        edgePadding = 0.dp,
                        indicator = { tabPositions ->
                            if (selectedTabIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    ) {
                        tabs.forEach { tabName ->
                            Tab(
                                selected = (activeTab == tabName),
                                onClick = { onTabSelected(tabName) },
                                text = {
                                    Text(
                                        text = tabName,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                },
                                modifier = Modifier.accessibleTouchTarget()
                            )
                        }
                    }
                }

                // Tab Content Panel (Scrollable)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    when (activeTab) {
                        "Transform" -> TransformTabContent(
                            transform = selectedClip.transform,
                            fitMode = selectedClip.fitMode,
                            onTransformChanged = onTransformChanged
                        )
                        "Audio", "Volume" -> AudioTabContent(
                            audio = selectedClip.audioProperties,
                            onAudioChanged = onAudioChanged
                        )
                        "Speed" -> SpeedTabContent(
                            speed = selectedClip.speedMultiplier,
                            onSpeedChanged = onSpeedChanged
                        )
                        "Cut" -> CutTabContent(clip = selectedClip)
                        "Fades" -> FadesTabContent(
                            audio = selectedClip.audioProperties,
                            onAudioChanged = onAudioChanged
                        )
                        "Typography", "Animation", "Style" -> TextTabContent(activeTab = activeTab)
                    }
                }
            }
        }
    }
}

@Composable
fun TransformTabContent(
    transform: ClipTransform,
    fitMode: ClipFitMode,
    onTransformChanged: (ClipTransform) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Scale X Slider
        PropertySliderRow(
            label = "Scale",
            valueText = String.format(Locale.US, "%.1fx", transform.scaleX),
            value = transform.scaleX,
            valueRange = 0.1f..3.0f,
            onValueChange = { newScale ->
                onTransformChanged(transform.copy(scaleX = newScale, scaleY = newScale))
            }
        )

        // Rotation Slider
        PropertySliderRow(
            label = "Rotation",
            valueText = "${transform.rotationDegrees.roundToInt()} deg",
            value = transform.rotationDegrees,
            valueRange = -180f..180f,
            onValueChange = { newRot ->
                onTransformChanged(transform.copy(rotationDegrees = newRot))
            }
        )

        // Opacity Slider
        PropertySliderRow(
            label = "Opacity",
            valueText = "${(transform.opacity * 100).roundToInt()}%",
            value = transform.opacity,
            valueRange = 0f..1f,
            onValueChange = { newOp ->
                onTransformChanged(transform.copy(opacity = newOp))
            }
        )
    }
}

@Composable
fun AudioTabContent(
    audio: ClipAudioProperties,
    onAudioChanged: (ClipAudioProperties) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Volume Slider (0% to 200%)
        PropertySliderRow(
            label = "Volume",
            valueText = "${(audio.volume * 100).roundToInt()}%",
            value = audio.volume,
            valueRange = 0f..2f,
            onValueChange = { onAudioChanged(audio.copy(volume = it)) }
        )

        // Mute Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Mute Audio",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Switch(
                checked = audio.isMuted,
                onCheckedChange = { onAudioChanged(audio.copy(isMuted = it)) },
                modifier = Modifier.accessibleTouchTarget()
            )
        }
    }
}

@Composable
fun SpeedTabContent(
    speed: Float,
    onSpeedChanged: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Speed Multiplier (0.1x to 10.0x) with numerical readout
        PropertySliderRow(
            label = "Playback Speed",
            valueText = String.format(Locale.US, "%.1fx", speed),
            value = speed,
            valueRange = 0.1f..10.0f,
            onValueChange = onSpeedChanged
        )

        // Speed Preset Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(0.5f, 1.0f, 1.5f, 2.0f, 4.0f).forEach { preset ->
                OutlinedButton(
                    onClick = { onSpeedChanged(preset) },
                    modifier = Modifier.accessibleTouchTarget()
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1fx", preset),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
fun CutTabContent(clip: TimelineClip) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Source In Point", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatDuration(clip.sourceInUs),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Source Out Point", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatDuration(clip.sourceOutUs),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Trimmed Duration", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = formatDuration(clip.timelineDurationUs),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun FadesTabContent(
    audio: ClipAudioProperties,
    onAudioChanged: (ClipAudioProperties) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PropertySliderRow(
            label = "Fade In",
            valueText = String.format(Locale.US, "%.1fs", audio.fadeInDurationUs / 1_000_000.0),
            value = (audio.fadeInDurationUs / 1_000_000.0f),
            valueRange = 0f..5.0f,
            onValueChange = { onAudioChanged(audio.copy(fadeInDurationUs = (it * 1_000_000L).toLong())) }
        )

        PropertySliderRow(
            label = "Fade Out",
            valueText = String.format(Locale.US, "%.1fs", audio.fadeOutDurationUs / 1_000_000.0),
            value = (audio.fadeOutDurationUs / 1_000_000.0f),
            valueRange = 0f..5.0f,
            onValueChange = { onAudioChanged(audio.copy(fadeOutDurationUs = (it * 1_000_000L).toLong())) }
        )
    }
}

@Composable
fun TextTabContent(activeTab: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "$activeTab Settings",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Font size, tracking, and styling parameters",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PropertySliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp), // >= 48dp touch bounds
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(100.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .weight(1f)
                .accessibleTouchTarget()
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = valueText,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(56.dp)
        )
    }
}

private fun formatDuration(durationUs: Long): String {
    val totalSec = durationUs / 1_000_000L
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
