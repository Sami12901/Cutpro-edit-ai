package com.cutpro.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * CutPro Vector Iconography Suite.
 * Strictly replaces all emojis with standard Material Symbols and vector drawables.
 * Zero emojis permitted anywhere.
 */
object CutProIcons {
    val Play: ImageVector = Icons.Filled.PlayArrow
    val Pause: ImageVector = Icons.Filled.Pause
    val Undo: ImageVector = Icons.AutoMirrored.Filled.Undo
    val Redo: ImageVector = Icons.AutoMirrored.Filled.Redo
    val Split: ImageVector = Icons.Filled.ContentCut
    val Trim: ImageVector = Icons.Filled.LinearScale
    val CropTool: ImageVector = Icons.Filled.Crop
    val Volume: ImageVector = Icons.AutoMirrored.Filled.VolumeUp
    val VolumeMute: ImageVector = Icons.AutoMirrored.Filled.VolumeMute

    val Settings: ImageVector = Icons.Filled.Settings
    val Delete: ImageVector = Icons.Filled.Delete
    val Add: ImageVector = Icons.Filled.Add
    val AspectRatio: ImageVector = Icons.Filled.AspectRatio
    val ZoomFit: ImageVector = Icons.Filled.FitScreen
    val Snap: ImageVector = Icons.Filled.Grain
    val StepForward: ImageVector = Icons.Filled.SkipNext
    val StepBackward: ImageVector = Icons.Filled.SkipPrevious
    val Export: ImageVector = Icons.Filled.FileUpload
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val VideoTrack: ImageVector = Icons.Filled.Videocam
    val AudioTrack: ImageVector = Icons.Filled.Audiotrack
    val TextTrack: ImageVector = Icons.Filled.Title
    val OverlayTrack: ImageVector = Icons.Filled.Layers
    val CaptionTrack: ImageVector = Icons.Filled.Subtitles
    val Speed: ImageVector = Icons.Filled.Speed
    val Transform: ImageVector = Icons.Filled.Transform
    val Check: ImageVector = Icons.Filled.Check
    val Close: ImageVector = Icons.Filled.Close
    val More: ImageVector = Icons.Filled.MoreVert
    val Visible: ImageVector = Icons.Filled.Visibility
    val Invisible: ImageVector = Icons.Filled.VisibilityOff
    val Locked: ImageVector = Icons.Filled.Lock
    val Unlocked: ImageVector = Icons.Filled.LockOpen
    val Grid: ImageVector = Icons.Filled.GridOn
}
