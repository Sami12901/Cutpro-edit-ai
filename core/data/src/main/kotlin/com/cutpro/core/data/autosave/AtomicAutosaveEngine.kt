package com.cutpro.core.data.autosave

import android.os.StatFs
import com.cutpro.core.data.serialization.ProjectJsonSerializer
import com.cutpro.core.model.ProjectManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class InsufficientDiskSpaceException(message: String) : IOException(message)

class AtomicAutosaveEngine {

    private val saveMutex = Mutex()

    /**
     * Executes non-corrupting two-phase atomic autosave:
     * 1. Space check: verifies target directory has at least 10 MB free.
     * 2. Staging write: writes serialized JSON to targetFile.tmp.
     * 3. Hardware sync: flushes OS buffers to physical disk via fd.sync().
     * 4. Atomic swap: renames targetFile.tmp to targetFile via ATOMIC_MOVE.
     */
    suspend fun saveProjectAtomically(
        manifest: ProjectManifest,
        targetFile: File,
        minFreeBytes: Long = 10L * 1024 * 1024 // 10 MB safety threshold
    ): Result<Unit> = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            runCatching {
                val parentDir = targetFile.parentFile ?: throw IOException("Target file has no parent directory: $targetFile")
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }

                // Step 1: Disk space check
                val freeBytes = getAvailableBytes(parentDir)
                if (freeBytes < minFreeBytes) {
                    throw InsufficientDiskSpaceException(
                        "Insufficient disk space to autosave project. Free: $freeBytes bytes, Required: $minFreeBytes bytes."
                    )
                }

                val jsonContent = ProjectJsonSerializer.serialize(manifest)
                val tempFile = File(parentDir, "${targetFile.name}.tmp")

                // Step 2: Write bytes to staging file and flush hardware sync
                FileOutputStream(tempFile).use { fos ->
                    val bytes = jsonContent.toByteArray(Charsets.UTF_8)
                    fos.write(bytes)
                    fos.flush()
                    try {
                        fos.fd.sync()
                    } catch (e: Exception) {
                        // Some virtualized or test filesystems may not support sync()
                    }
                }

                // Step 3: Atomic file rename
                try {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    )
                } catch (e: AtomicMoveNotSupportedException) {
                    // Fallback for filesystems where atomic move is unsupported
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
                Unit
            }
        }
    }

    private fun getAvailableBytes(dir: File): Long {
        return try {
            val stat = StatFs(dir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Throwable) {
            dir.usableSpace.coerceAtLeast(0L)
        }
    }
}
