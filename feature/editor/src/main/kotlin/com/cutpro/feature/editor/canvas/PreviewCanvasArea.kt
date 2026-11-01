package com.cutpro.feature.editor.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.cutpro.core.designsystem.component.CutProIconButton
import com.cutpro.core.designsystem.component.TimecodeBadge
import com.cutpro.core.designsystem.icon.CutProIcons
import com.cutpro.core.designsystem.theme.DarkBackground
import com.cutpro.core.designsystem.theme.DarkSurface
import com.cutpro.core.model.CanvasAspectRatio

/**
 * Preview canvas area rendering the aspect-ratio framed presentation container,
 * safe-zone guidelines overlay (93% action safe, 90% title safe, center crosshair),
 * and transport controls. Zero emojis. All touch targets >= 48dp.
 */
@Composable
fun PreviewCanvasArea(
    aspectRatio: CanvasAspectRatio,
    timecode: String,
    totalDuration: String,
    isPlaying: Boolean,
    areGuideLinesVisible: Boolean,
    onPlayPauseClick: () -> Unit,
    onStepForwardClick: () -> Unit,
    onStepBackwardClick: () -> Unit,
    onToggleGuideLines: () -> Unit,
    modifier: Modifier = Modifier,
    player: Player? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        // Framed Canvas Container maintaining chosen aspect ratio
        Box(
            modifier = Modifier
                .padding(12.dp)
                .aspectRatio(aspectRatio.widthRatio.toFloat() / aspectRatio.heightRatio.toFloat())
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface),
            contentAlignment = Alignment.Center
        ) {
            if (player != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    update = { playerView ->
                        playerView.player = player
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Placeholder Video Presentation Content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = CutProIcons.VideoTrack,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "CutPro Preview Canvas",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Safe Zone Guidelines Overlay (Action Safe 93%, Title Safe 90%, Center Crosshair)
            if (areGuideLinesVisible) {
                CanvasGuidelinesOverlay(modifier = Modifier.fillMaxSize())
            }
        }

        // Bottom Floating Transport Controls Bar
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Current Timecode Readout
                TimecodeBadge(timecode = timecode)

                Spacer(modifier = Modifier.width(8.dp))

                // Step 1 Frame Backward
                CutProIconButton(
                    icon = CutProIcons.StepBackward,
                    contentDescription = "Step backward",
                    onClick = onStepBackwardClick
                )

                // Play / Pause Button
                FloatingActionButton(
                    onClick = onPlayPauseClick,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) CutProIcons.Pause else CutProIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Step 1 Frame Forward
                CutProIconButton(
                    icon = CutProIcons.StepForward,
                    contentDescription = "Step forward",
                    onClick = onStepForwardClick
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Total Duration
                TimecodeBadge(
                    timecode = totalDuration,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Toggle Guide Lines Overlay
                CutProIconButton(
                    icon = CutProIcons.Grid,
                    contentDescription = "Toggle guides",
                    onClick = onToggleGuideLines,
                    tint = if (areGuideLinesVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Overlay drawing Safe Action (93%), Safe Title (90%), and Center Snap Crosshair.
 */
@Composable
fun CanvasGuidelinesOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val dashedStroke = Stroke(
            width = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
        )
        val solidStroke = Stroke(width = 1f)

        val guideColor = Color(0x6600E676)
        val centerColor = Color(0x44FFFFFF)

        // 93% Action Safe Area (3.5% margin on each side)
        val actionInsetX = w * 0.035f
        val actionInsetY = h * 0.035f
        drawRect(
            color = guideColor,
            topLeft = Offset(actionInsetX, actionInsetY),
            size = Size(w - 2 * actionInsetX, h - 2 * actionInsetY),
            style = solidStroke
        )

        // 90% Title Safe Area (5% margin on each side)
        val titleInsetX = w * 0.05f
        val titleInsetY = h * 0.05f
        drawRect(
            color = guideColor.copy(alpha = 0.5f),
            topLeft = Offset(titleInsetX, titleInsetY),
            size = Size(w - 2 * titleInsetX, h - 2 * titleInsetY),
            style = dashedStroke
        )

        // Center Crosshair
        drawLine(
            color = centerColor,
            start = Offset(w / 2f, 0f),
            end = Offset(w / 2f, h),
            strokeWidth = 1f
        )
        drawLine(
            color = centerColor,
            start = Offset(0f, h / 2f),
            end = Offset(w, h / 2f),
            strokeWidth = 1f
        )
    }
}
