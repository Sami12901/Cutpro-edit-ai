package com.cutpro.feature.export.validator

import android.content.Context
import android.net.Uri
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.RenderPlan
import com.cutpro.feature.export.model.ExportValidationResult
import java.io.File

/**
 * Pre-export storage estimator and source media accessibility validator.
 * Strictly zero emojis.
 */
open class PreExportValidator(private val context: Context) {

    open fun validate(project: ProjectManifest, plan: RenderPlan, targetDir: File): ExportValidationResult {
        val hasClips = project.tracks.any { it.clips.isNotEmpty() }
        if (!hasClips || plan.durationUs <= 0L) {
            return ExportValidationResult.EmptyTimeline("Empty timeline or zero duration")
        }

        val requiredBytes = plan.calculateEstimatedOutputSizeBytes(safetyBufferMb = 50, overheadFactor = 1.15)
        val availableBytes = targetDir.usableSpace

        if (availableBytes < requiredBytes) {
            return ExportValidationResult.InsufficientStorage(
                requiredBytes = requiredBytes,
                availableBytes = availableBytes,
                message = "Insufficient storage: needed $requiredBytes bytes, available $availableBytes bytes"
            )
        }

        for (track in project.tracks) {
            for (clip in track.clips) {
                if (clip.sourceOutUs <= clip.sourceInUs) {
                    return ExportValidationResult.InvalidClipBoundaries(clip.id)
                }

                val uri = Uri.parse(clip.mediaSource.uriString)
                val isAccessible = when (uri.scheme) {
                    "file" -> {
                        val path = uri.path ?: ""
                        val file = File(path)
                        file.exists() && file.canRead()
                    }
                    "content" -> {
                        try {
                            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
                        } catch (_: Exception) {
                            false
                        }
                    }
                    else -> true
                }

                if (!isAccessible) {
                    return ExportValidationResult.MissingSourceMedia(
                        clipId = clip.id,
                        uriString = clip.mediaSource.uriString
                    )
                }
            }
        }

        return ExportValidationResult.Valid
    }
}
