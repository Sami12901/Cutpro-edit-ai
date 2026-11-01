package com.cutpro.core.export

import androidx.media3.transformer.Composition
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.RenderPlan
import java.io.File

sealed interface ValidationResult {
    object Valid : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
    data class InsufficientStorage(val neededBytes: Long, val availableBytes: Long) : ValidationResult
}

interface ExportProgressListener {
    fun onProgress(percentage: Int, elapsedMs: Long, estimatedRemainingMs: Long?)
    fun onCompleted(outputFile: File)
    fun onError(throwable: Throwable)
    fun onCancelled()
}

interface ExportPipeline {
    fun validatePlan(project: ProjectManifest, plan: RenderPlan, outputDir: File): ValidationResult
    fun buildComposition(project: ProjectManifest, plan: RenderPlan): Composition
    fun startExport(composition: Composition, outputFile: File, listener: ExportProgressListener)
    fun cancelExport()
}
