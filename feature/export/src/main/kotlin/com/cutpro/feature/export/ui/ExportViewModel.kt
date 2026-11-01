package com.cutpro.feature.export.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutpro.core.export.VideoExportEngine
import com.cutpro.core.export.model.ExportPlan
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.RenderPlan
import com.cutpro.feature.export.model.ExportProgressState
import com.cutpro.feature.export.model.ExportStatus
import com.cutpro.feature.export.model.ExportValidationResult
import com.cutpro.feature.export.service.ExportForegroundService
import com.cutpro.feature.export.storage.ExportShareHelper
import com.cutpro.feature.export.validator.PreExportValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * ViewModel managing export dialog configuration, pre-export validation,
 * service lifecycle orchestration, real-time progress observation, and share interaction.
 * Strictly zero emojis.
 */
class ExportViewModel(
    private val exportEngine: VideoExportEngine? = null,
    private val preValidator: PreExportValidator? = null
) : ViewModel() {

    private val _showConfigDialog = MutableStateFlow(false)
    val showConfigDialog: StateFlow<Boolean> = _showConfigDialog.asStateFlow()

    private val _showProgressSheet = MutableStateFlow(false)
    val showProgressSheet: StateFlow<Boolean> = _showProgressSheet.asStateFlow()

    private val _progressState = MutableStateFlow(ExportProgressState())
    val progressState: StateFlow<ExportProgressState> = _progressState.asStateFlow()

    private var activeProjectTitle: String = "CutPro_Project"
    private var activeExportJob: Job? = null
    private var activeExportId: String? = null

    fun openConfigDialog(project: ProjectManifest) {
        activeProjectTitle = project.title
        _showConfigDialog.value = true
    }

    fun dismissConfigDialog() {
        _showConfigDialog.value = false
    }

    fun startExport(context: Context, project: ProjectManifest, renderPlan: RenderPlan) {
        val validator = preValidator ?: PreExportValidator(context)
        val tempDir = context.cacheDir
        val validationResult = validator.validate(project, renderPlan, tempDir)

        if (validationResult !is ExportValidationResult.Valid) {
            val failureMessage = when (validationResult) {
                is ExportValidationResult.InsufficientStorage -> validationResult.message
                is ExportValidationResult.MissingSourceMedia -> "Missing or inaccessible media: ${validationResult.uriString}"
                is ExportValidationResult.EmptyTimeline -> validationResult.message
                is ExportValidationResult.InvalidClipBoundaries -> "Invalid clip boundaries for clip ${validationResult.clipId}"
                ExportValidationResult.Valid -> ""
            }
            _showConfigDialog.value = false
            _showProgressSheet.value = true
            _progressState.value = ExportProgressState(
                status = ExportStatus.FAILED,
                errorMessage = failureMessage
            )
            return
        }

        _showConfigDialog.value = false
        _showProgressSheet.value = true
        _progressState.value = ExportProgressState(status = ExportStatus.PREPARING)

        val exportId = UUID.randomUUID().toString()
        activeExportId = exportId
        activeProjectTitle = project.title

        val tempFile = File(context.cacheDir, "cutpro_export_${exportId}.mp4")

        val plan = ExportPlan(
            exportId = exportId,
            project = project,
            resolution = renderPlan.resolution,
            frameRate = renderPlan.frameRate,
            codec = renderPlan.videoCodec,
            targetAspectRatio = renderPlan.aspectRatio,
            outputFile = tempFile,
            videoBitrateBps = renderPlan.videoBitrateBps,
            audioBitrateBps = renderPlan.audioBitrateBps
        )

        // Inject custom test engine into service factory if provided
        if (exportEngine != null) {
            ExportForegroundService.exportEngineFactory = { exportEngine }
        }

        // Trigger foreground service with export plan
        ExportForegroundService.startExport(context, plan)

        // Observe foreground service state flow
        activeExportJob?.cancel()
        activeExportJob = viewModelScope.launch {
            ExportForegroundService.exportState.collect { serviceState ->
                _progressState.value = serviceState
            }
        }
    }

    fun cancelExport(context: Context) {
        activeExportJob?.cancel()
        activeExportJob = null
        ExportForegroundService.cancel(context)
        _progressState.update { it.copy(status = ExportStatus.CANCELLED) }
    }

    fun shareExportedVideo(context: Context) {
        val uri = _progressState.value.outputUri ?: return
        ExportShareHelper.shareVideo(context, uri, activeProjectTitle)
    }

    fun dismissProgressSheet() {
        _showProgressSheet.value = false
    }
}
