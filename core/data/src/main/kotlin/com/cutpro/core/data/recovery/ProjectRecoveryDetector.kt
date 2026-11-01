package com.cutpro.core.data.recovery

import com.cutpro.core.data.serialization.ProjectJsonSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed class RecoveryResult {
    data object NoTempFound : RecoveryResult()
    data class CorruptedTempDiscarded(val reason: String) : RecoveryResult()
    data class RecoveredFromTemp(val recoveredFile: File) : RecoveryResult()
    data class PrimaryFileKept(val primaryFile: File) : RecoveryResult()
}

class ProjectRecoveryDetector {

    /**
     * Inspects target project directory for orphaned .tmp autosave files after sudden kill.
     * 1. If .tmp doesn't exist, return NoTempFound.
     * 2. If .tmp exists and is invalid JSON: discard .tmp, return CorruptedTempDiscarded.
     * 3. If .tmp exists, is valid, and target file is missing or .tmp is newer: promote .tmp to targetFile, return RecoveredFromTemp.
     * 4. If primary file is newer than .tmp: delete stale .tmp, return PrimaryFileKept.
     */
    suspend fun checkAndRecover(targetFile: File): RecoveryResult = withContext(Dispatchers.IO) {
        val parentDir = targetFile.parentFile ?: return@withContext RecoveryResult.NoTempFound
        val tempFile = File(parentDir, "${targetFile.name}.tmp")

        if (!tempFile.exists()) {
            return@withContext RecoveryResult.NoTempFound
        }

        // Validate temporary file JSON content
        val isValidJson = try {
            val content = tempFile.readText(Charsets.UTF_8)
            if (content.isBlank()) {
                false
            } else {
                ProjectJsonSerializer.deserialize(content)
                true
            }
        } catch (e: Exception) {
            false
        }

        if (!isValidJson) {
            // Discard corrupted partial write
            tempFile.delete()
            return@withContext RecoveryResult.CorruptedTempDiscarded("Invalid or truncated temporary file")
        }

        // Valid temp file exists. Compare modification timestamps.
        if (!targetFile.exists() || tempFile.lastModified() >= targetFile.lastModified()) {
            // Promote newer temp file to primary file
            try {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: Exception) {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            return@withContext RecoveryResult.RecoveredFromTemp(targetFile)
        } else {
            // Primary file is newer than temp file, delete stale temp
            tempFile.delete()
            return@withContext RecoveryResult.PrimaryFileKept(targetFile)
        }
    }
}
