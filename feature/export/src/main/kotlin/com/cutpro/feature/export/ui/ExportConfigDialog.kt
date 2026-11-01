package com.cutpro.feature.export.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.cutpro.core.designsystem.component.CutProButton
import com.cutpro.core.designsystem.component.CutProOutlinedButton
import com.cutpro.core.designsystem.icon.CutProIcons
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.RenderPlan
import com.cutpro.core.model.VideoCodec
import com.cutpro.feature.export.model.ExportValidationResult
import com.cutpro.feature.export.validator.PreExportValidator
import java.util.Locale

/**
 * Material 3 export configuration dialog allowing selection of resolution, frame rate,
 * aspect ratio, codec, and displaying real-time storage pre-checks.
 * Strictly zero emojis.
 */
@Composable
fun ExportConfigDialog(
    project: ProjectManifest,
    onDismiss: () -> Unit,
    onConfirmExport: (RenderPlan) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedResolution by remember { mutableStateOf(project.defaultResolution) }
    var selectedFrameRate by remember { mutableStateOf(project.defaultFrameRate) }
    var selectedAspectRatio by remember { mutableStateOf(project.aspectRatio) }
    val selectedCodec = VideoCodec.H264

    val videoBitrate = when (selectedResolution) {
        ExportResolution.RES_720P -> 5_000_000
        ExportResolution.RES_1080P -> 12_000_000
    }
    val audioBitrate = 192_000

    val currentPlan = remember(selectedResolution, selectedFrameRate, selectedAspectRatio) {
        RenderPlan(
            resolution = selectedResolution,
            frameRate = selectedFrameRate,
            aspectRatio = selectedAspectRatio,
            videoCodec = selectedCodec,
            videoBitrateBps = videoBitrate,
            audioBitrateBps = audioBitrate,
            durationUs = project.durationUs
        )
    }

    val estimatedBytes = remember(currentPlan) {
        currentPlan.calculateEstimatedOutputSizeBytes(safetyBufferMb = 50, overheadFactor = 1.15)
    }

    val availableBytes = remember(currentPlan) {
        context.cacheDir.usableSpace
    }

    val headroomBytes = availableBytes - estimatedBytes
    val hasSufficientStorage = headroomBytes >= 0

    val validationResult = remember(currentPlan, project) {
        PreExportValidator(context).validate(project, currentPlan, context.cacheDir)
    }

    val isExportValid = validationResult is ExportValidationResult.Valid && hasSufficientStorage

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Export Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Resolution Selection
                Text(
                    text = "Resolution",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableChip(
                        text = "720p HD",
                        selected = selectedResolution == ExportResolution.RES_720P,
                        onClick = { selectedResolution = ExportResolution.RES_720P },
                        modifier = Modifier.weight(1f)
                    )
                    SelectableChip(
                        text = "1080p Full HD",
                        selected = selectedResolution == ExportResolution.RES_1080P,
                        onClick = { selectedResolution = ExportResolution.RES_1080P },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Frame Rate Selection
                Text(
                    text = "Frame Rate",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableChip(
                        text = "24 fps",
                        selected = selectedFrameRate == ExportFrameRate.FPS_24,
                        onClick = { selectedFrameRate = ExportFrameRate.FPS_24 },
                        modifier = Modifier.weight(1f)
                    )
                    SelectableChip(
                        text = "30 fps",
                        selected = selectedFrameRate == ExportFrameRate.FPS_30,
                        onClick = { selectedFrameRate = ExportFrameRate.FPS_30 },
                        modifier = Modifier.weight(1f)
                    )
                    SelectableChip(
                        text = "60 fps",
                        selected = selectedFrameRate == ExportFrameRate.FPS_60,
                        onClick = { selectedFrameRate = ExportFrameRate.FPS_60 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Aspect Ratio Selection
                Text(
                    text = "Aspect Ratio",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SelectableChip(
                        text = "16:9",
                        selected = selectedAspectRatio == CanvasAspectRatio.RATIO_16_9,
                        onClick = { selectedAspectRatio = CanvasAspectRatio.RATIO_16_9 },
                        modifier = Modifier.weight(1f)
                    )
                    SelectableChip(
                        text = "9:16",
                        selected = selectedAspectRatio == CanvasAspectRatio.RATIO_9_16,
                        onClick = { selectedAspectRatio = CanvasAspectRatio.RATIO_9_16 },
                        modifier = Modifier.weight(1f)
                    )
                    SelectableChip(
                        text = "1:1",
                        selected = selectedAspectRatio == CanvasAspectRatio.RATIO_1_1,
                        onClick = { selectedAspectRatio = CanvasAspectRatio.RATIO_1_1 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Codec Info Card
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Codec",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "H.264 / AVC",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Estimated Size",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatSizeMb(estimatedBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Free Storage",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatSizeMb(availableBytes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (hasSufficientStorage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Storage Headroom",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (headroomBytes >= 0) "+${formatSizeMb(headroomBytes)}" else "-${formatSizeMb(-headroomBytes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (headroomBytes >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                if (!isExportValid) {
                    val warningText = when (validationResult) {
                        is ExportValidationResult.InsufficientStorage ->
                            "Insufficient storage: needed ${formatSizeMb(validationResult.requiredBytes)}, available ${formatSizeMb(validationResult.availableBytes)} (includes 50 MB safety margin)."
                        is ExportValidationResult.MissingSourceMedia ->
                            "Missing media: Clip '${validationResult.clipId}' source file is not accessible."
                        is ExportValidationResult.InvalidClipBoundaries ->
                            "Invalid clip trim points for clip '${validationResult.clipId}'."
                        is ExportValidationResult.EmptyTimeline ->
                            "Timeline is empty: ${validationResult.message}."
                        ExportValidationResult.Valid ->
                            if (!hasSufficientStorage) "Insufficient storage available for export." else ""
                    }
                    if (warningText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = warningText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CutProOutlinedButton(
                        text = "Cancel",
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    CutProButton(
                        text = "Start Export",
                        icon = CutProIcons.Export,
                        enabled = isExportValid,
                        onClick = { onConfirmExport(currentPlan) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

private fun formatSizeMb(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}
