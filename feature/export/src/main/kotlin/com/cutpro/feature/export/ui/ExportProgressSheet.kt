package com.cutpro.feature.export.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cutpro.core.designsystem.component.CutProButton
import com.cutpro.core.designsystem.component.CutProOutlinedButton
import com.cutpro.core.designsystem.icon.CutProIcons
import com.cutpro.feature.export.model.ExportProgressState
import com.cutpro.feature.export.model.ExportStatus
import java.util.Locale

/**
 * Modal bottom sheet displaying real-time export progress, elapsed time, ETA, cancellation,
 * and share sheet triggers upon completion.
 * Strictly zero emojis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportProgressSheet(
    state: ExportProgressState,
    onCancel: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    ModalBottomSheet(
        onDismissRequest = {
            if (state.status == ExportStatus.COMPLETED ||
                state.status == ExportStatus.FAILED ||
                state.status == ExportStatus.CANCELLED
            ) {
                onDismiss()
            }
        },
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state.status) {
                ExportStatus.IDLE,
                ExportStatus.PREPARING -> {
                    Text(
                        text = "Preparing Export",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Initializing encoders and building render plan...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    CutProOutlinedButton(
                        text = "Cancel",
                        onClick = onCancel
                    )
                }

                ExportStatus.RENDERING,
                ExportStatus.FINALIZING -> {
                    val isFinalizing = state.status == ExportStatus.FINALIZING
                    Text(
                        text = if (isFinalizing) "Finalizing MP4 Video" else "Rendering Video",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (isFinalizing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { state.progressPercentage / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${state.progressPercentage}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val elapsedStr = formatTime(state.elapsedTimeMs)
                        val etaStr = state.estimatedRemainingMs?.let { formatTime(it) } ?: "calculating..."
                        Text(
                            text = "Elapsed: $elapsedStr | ETA: $etaStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    CutProOutlinedButton(
                        text = "Cancel",
                        onClick = onCancel
                    )
                }

                ExportStatus.COMPLETED -> {
                    Icon(
                        imageVector = CutProIcons.Check,
                        contentDescription = "Export Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Export Completed",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Video successfully rendered and saved to Movies/CutPro",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CutProButton(
                            text = "Share Video",
                            icon = CutProIcons.Export,
                            onClick = onShare
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        CutProOutlinedButton(
                            text = "Done",
                            onClick = onDismiss
                        )
                    }
                }

                ExportStatus.FAILED -> {
                    Icon(
                        imageVector = CutProIcons.Close,
                        contentDescription = "Export Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Export Failed",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.errorMessage ?: "An unexpected error occurred during rendering.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    CutProButton(
                        text = "Dismiss",
                        onClick = onDismiss
                    )
                }

                ExportStatus.CANCELLED -> {
                    Text(
                        text = "Export Cancelled",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "The export was aborted and temporary files were removed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    CutProButton(
                        text = "Close",
                        onClick = onDismiss
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}
