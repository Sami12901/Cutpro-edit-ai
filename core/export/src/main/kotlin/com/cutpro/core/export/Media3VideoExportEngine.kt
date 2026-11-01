package com.cutpro.core.export

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.cutpro.core.export.composition.Media3CompositionBuilder
import com.cutpro.core.export.model.ExportPlan
import com.cutpro.core.export.model.ExportProgress
import com.cutpro.core.export.model.ExportResult
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.RenderPlan
import com.cutpro.core.model.VideoCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Production VideoExportEngine implementation utilizing AndroidX Media3 Transformer.
 * Performs genuine off-main-thread MP4 rendering, 250ms progress polling, monotonic progress guarantee,
 * storage pre-checks, and clean cancellation with partial file cleanup.
 * Strictly zero emojis.
 */
@OptIn(UnstableApi::class)
class Media3VideoExportEngine(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : VideoExportEngine, ExportPipeline {

    private val compositionBuilder = Media3CompositionBuilder(context)

    private data class ActiveExport(
        val transformer: Transformer,
        val outputFile: File,
        val pollingJob: Job? = null
    )

    private val activeExports = ConcurrentHashMap<String, ActiveExport>()

    override fun validateExportPlan(plan: ExportPlan): Result<Unit> {
        val project = plan.project
        val hasClips = project.tracks.any { it.clips.isNotEmpty() }
        if (!hasClips || plan.project.durationUs <= 0L) {
            return Result.failure(IllegalArgumentException("Timeline has no clips or duration is zero"))
        }

        val requiredBytes = plan.calculateEstimatedOutputSizeBytes(safetyBufferMb = 50, overheadFactor = 1.15)
        val targetDir = plan.outputFile.parentFile ?: context.cacheDir
        val availableBytes = targetDir.usableSpace

        if (availableBytes < requiredBytes) {
            return Result.failure(
                IllegalStateException(
                    "Insufficient storage: needed $requiredBytes bytes, available $availableBytes bytes"
                )
            )
        }

        // Validate clip boundaries and URI accessibility
        for (track in project.tracks) {
            for (clip in track.clips) {
                if (clip.sourceOutUs <= clip.sourceInUs) {
                    return Result.failure(
                        IllegalArgumentException("Invalid clip boundaries for clip ${clip.id}")
                    )
                }

                val uri = Uri.parse(clip.mediaSource.uriString)
                val accessible = isUriAccessible(uri)
                if (!accessible) {
                    return Result.failure(
                        IllegalStateException("Missing or inaccessible media source: ${clip.mediaSource.uriString}")
                    )
                }
            }
        }

        return Result.success(Unit)
    }

    override fun exportVideo(plan: ExportPlan): Flow<ExportProgress> = channelFlow {
        val validation = validateExportPlan(plan)
        if (validation.isFailure) {
            val err = validation.exceptionOrNull() ?: IllegalArgumentException("Invalid export plan")
            send(ExportProgress.Failed(plan.exportId, err, err.message ?: "Validation failure"))
            close()
            return@channelFlow
        }

        send(ExportProgress.Starting(plan.exportId))

        val composition = compositionBuilder.buildComposition(plan)
        val (targetWidth, targetHeight) = plan.calculateOutputDimensions()
        val exportStartTimeMs = System.currentTimeMillis()

        var lastRecordedPercent = 0
        var isFinished = false

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(plan.codec.mimeType)
            .setAudioMimeType("audio/mp4a-latm")
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    comp: Composition,
                    exportResult: androidx.media3.transformer.ExportResult
                ) {
                    isFinished = true
                    activeExports.remove(plan.exportId)
                    val durationMs = System.currentTimeMillis() - exportStartTimeMs
                    val fileSize = if (plan.outputFile.exists()) plan.outputFile.length() else 0L

                    val result = ExportResult(
                        exportId = plan.exportId,
                        outputFile = plan.outputFile,
                        destinationUri = plan.destinationUri,
                        durationUs = plan.project.durationUs,
                        fileSizeBytes = fileSize,
                        width = targetWidth,
                        height = targetHeight,
                        frameRate = plan.frameRate.fps,
                        codec = plan.codec,
                        exportDurationMs = durationMs
                    )

                    launch {
                        send(ExportProgress.Completed(plan.exportId, result))
                        close()
                    }
                }

                override fun onError(
                    comp: Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: ExportException
                ) {
                    isFinished = true
                    activeExports.remove(plan.exportId)
                    cleanupOutputFile(plan.outputFile)

                    launch {
                        send(
                            ExportProgress.Failed(
                                exportId = plan.exportId,
                                cause = exportException,
                                message = exportException.message ?: "Export failed"
                            )
                        )
                        close()
                    }
                }
            })
            .build()

        val progressHolder = ProgressHolder()
        val pollingJob = launch(defaultDispatcher) {
            while (isActive && !isFinished) {
                val state = transformer.getProgress(progressHolder)
                val elapsedMs = System.currentTimeMillis() - exportStartTimeMs

                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val rawProgress = progressHolder.progress.coerceIn(0, 100)
                    lastRecordedPercent = maxOf(lastRecordedPercent, rawProgress)
                    val etaMs = calculateEtaMs(lastRecordedPercent, elapsedMs)

                    send(
                        ExportProgress.Processing(
                            exportId = plan.exportId,
                            percentage = lastRecordedPercent,
                            elapsedMs = elapsedMs,
                            estimatedRemainingMs = etaMs
                        )
                    )
                } else if (state == Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
                    send(
                        ExportProgress.Processing(
                            exportId = plan.exportId,
                            percentage = lastRecordedPercent,
                            elapsedMs = elapsedMs,
                            estimatedRemainingMs = null
                        )
                    )
                }

                delay(250) // FEAT-EXP-006 250ms periodic progress polling
            }
        }

        activeExports[plan.exportId] = ActiveExport(
            transformer = transformer,
            outputFile = plan.outputFile,
            pollingJob = pollingJob
        )

        // Ensure parent directory exists
        plan.outputFile.parentFile?.mkdirs()

        // Start Media3 Transformer
        transformer.start(composition, plan.outputFile.absolutePath)

        awaitClose {
            if (!isFinished) {
                isFinished = true
                cancelExport(plan.exportId)
            }
        }
    }

    override fun cancelExport(exportId: String) {
        val active = activeExports.remove(exportId) ?: return
        active.pollingJob?.cancel()
        try {
            active.transformer.cancel()
        } catch (_: Exception) {}
        cleanupOutputFile(active.outputFile)
    }

    private fun cleanupOutputFile(file: File) {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    private fun isUriAccessible(uri: Uri): Boolean {
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return false
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
    }

    // ExportPipeline implementation for Interface Contract 6 compatibility

    override fun validatePlan(
        project: ProjectManifest,
        plan: RenderPlan,
        outputDir: File
    ): ValidationResult {
        val hasClips = project.tracks.any { it.clips.isNotEmpty() }
        if (!hasClips || plan.durationUs <= 0L) {
            return ValidationResult.Invalid("Timeline is empty or has zero duration")
        }

        val requiredBytes = plan.calculateEstimatedOutputSizeBytes(safetyBufferMb = 50, overheadFactor = 1.15)
        val availableBytes = outputDir.usableSpace

        if (availableBytes < requiredBytes) {
            return ValidationResult.InsufficientStorage(
                neededBytes = requiredBytes,
                availableBytes = availableBytes
            )
        }

        return ValidationResult.Valid
    }

    override fun buildComposition(project: ProjectManifest, plan: RenderPlan): Composition {
        return compositionBuilder.buildComposition(project, plan)
    }

    override fun startExport(
        composition: Composition,
        outputFile: File,
        listener: ExportProgressListener
    ) {
        val exportId = "legacy_${System.currentTimeMillis()}"
        val exportStartTimeMs = System.currentTimeMillis()
        var isFinished = false

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(
                    comp: Composition,
                    exportResult: androidx.media3.transformer.ExportResult
                ) {
                    isFinished = true
                    activeExports.remove(exportId)
                    listener.onCompleted(outputFile)
                }

                override fun onError(
                    comp: Composition,
                    exportResult: androidx.media3.transformer.ExportResult,
                    exportException: ExportException
                ) {
                    isFinished = true
                    activeExports.remove(exportId)
                    cleanupOutputFile(outputFile)
                    listener.onError(exportException)
                }
            })
            .build()

        val scope = CoroutineScope(defaultDispatcher)
        val progressHolder = ProgressHolder()
        val pollingJob = scope.launch {
            var lastPercent = 0
            while (isActive && !isFinished) {
                val state = transformer.getProgress(progressHolder)
                val elapsedMs = System.currentTimeMillis() - exportStartTimeMs

                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val raw = progressHolder.progress.coerceIn(0, 100)
                    lastPercent = maxOf(lastPercent, raw)
                    val etaMs = calculateEtaMs(lastPercent, elapsedMs)
                    listener.onProgress(lastPercent, elapsedMs, etaMs)
                }

                delay(250)
            }
        }

        activeExports[exportId] = ActiveExport(transformer, outputFile, pollingJob)
        outputFile.parentFile?.mkdirs()
        transformer.start(composition, outputFile.absolutePath)
    }

    override fun cancelExport() {
        val keys = activeExports.keys().toList()
        for (key in keys) {
            cancelExport(key)
        }
    }

    companion object {
        fun calculateEtaMs(progressPercent: Int, elapsedMs: Long): Long? {
            if (progressPercent <= 1 || elapsedMs < 1000L) return null
            if (progressPercent >= 100) return 0L
            val fraction = progressPercent / 100.0
            val totalEstimateMs = (elapsedMs / fraction).toLong()
            return maxOf(0L, totalEstimateMs - elapsedMs)
        }
    }
}
