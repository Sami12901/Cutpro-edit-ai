package com.cutpro.core.data.autosave

import com.cutpro.core.model.ProjectManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class AutosaveCoordinator(
    private val scope: CoroutineScope,
    private val autosaveEngine: AtomicAutosaveEngine,
    private val debounceDelayMs: Long = 10_000L
) {
    private var pendingSaveJob: Job? = null
    private var latestManifest: ProjectManifest? = null
    private var currentTargetFile: File? = null

    fun onProjectMutated(manifest: ProjectManifest, targetFile: File) {
        latestManifest = manifest
        currentTargetFile = targetFile
        pendingSaveJob?.cancel()
        pendingSaveJob = scope.launch {
            delay(debounceDelayMs)
            executeSave()
        }
    }

    suspend fun triggerAutosaveNow(): Result<Unit> {
        pendingSaveJob?.cancel()
        return executeSave()
    }

    private suspend fun executeSave(): Result<Unit> {
        val manifest = latestManifest ?: return Result.success(Unit)
        val target = currentTargetFile ?: return Result.success(Unit)
        return autosaveEngine.saveProjectAtomically(manifest, target)
    }
}
