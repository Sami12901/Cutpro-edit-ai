package com.cutpro.core.data.bundle

import com.cutpro.core.security.zip.ZipSecurityValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class UnpackedBundleResult(
    val manifestFile: File,
    val mediaDirectory: File,
    val extractedMediaFiles: List<File>,
    val totalBytesExtracted: Long
)

class CorruptedBundleException(message: String, cause: Throwable? = null) : IOException(message, cause)

class CutproBundleUnpackager(
    private val maxUncompressedBytes: Long = MAX_TOTAL_UNCOMPRESSED_BYTES
) {
    companion object {
        const val MAX_TOTAL_UNCOMPRESSED_BYTES = ZipSecurityValidator.MAX_TOTAL_UNCOMPRESSED_BYTES
    }

    /**
     * Unpacks a .cutprobundle ZIP archive into the destination directory with strict Zip-Slip protection.
     *
     * @param inputStream The ZIP input stream to read.
     * @param destinationDir The directory where files will be unpacked. Must be cleaned up on failure.
     * @param onProgress Optional progress callback reporting (entriesExtracted, bytesExtracted).
     * @return UnpackedBundleResult with references to the manifest file and extracted media assets.
     */
    suspend fun unpackBundle(
        inputStream: InputStream,
        destinationDir: File,
        onProgress: ((entriesExtracted: Int, bytesExtracted: Long) -> Unit)? = null
    ): UnpackedBundleResult = withContext(Dispatchers.IO) {
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }

        var manifestFile: File? = null
        val mediaFiles = mutableListOf<File>()
        val mediaDir = File(destinationDir, BundleArchiveLayout.MEDIA_DIR)
        var totalBytesExtracted = 0L
        var entryIndex = 0
        val buffer = ByteArray(32 * 1024)

        try {
            ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    entryIndex++
                    val entryName = entry.name

                    // Strict Zip-Slip validation: throws SecurityException on traversal attempt
                    val targetFile = ZipSecurityValidator.validateEntryDestination(destinationDir, entryName)

                    // Resource limit validation (Zip-Bomb defense)
                    ZipSecurityValidator.validateResourceLimits(entryIndex, totalBytesExtracted)

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { fos ->
                            var count: Int
                            while (zis.read(buffer).also { count = it } != -1) {
                                totalBytesExtracted += count
                                if (totalBytesExtracted > maxUncompressedBytes) {
                                    throw SecurityException("Bundle extracted bytes exceeded ceiling limit")
                                }
                                fos.write(buffer, 0, count)
                            }
                            fos.flush()
                        }

                        // Track recognized bundle files
                        if (entryName == BundleArchiveLayout.MANIFEST_FILENAME) {
                            manifestFile = targetFile
                        } else if (entryName.startsWith("${BundleArchiveLayout.MEDIA_DIR}/")) {
                            mediaFiles.add(targetFile)
                        }
                    }

                    zis.closeEntry()
                    onProgress?.invoke(entryIndex, totalBytesExtracted)
                    entry = zis.nextEntry
                }
            }

            // Verify that the manifest file exists
            val verifiedManifest = manifestFile
            if (verifiedManifest == null || !verifiedManifest.exists() || verifiedManifest.length() == 0L) {
                throw CorruptedBundleException("Invalid .cutprobundle: Missing or empty '${BundleArchiveLayout.MANIFEST_FILENAME}' manifest in archive root.")
            }

            UnpackedBundleResult(
                manifestFile = verifiedManifest,
                mediaDirectory = mediaDir,
                extractedMediaFiles = mediaFiles,
                totalBytesExtracted = totalBytesExtracted
            )
        } catch (t: Throwable) {
            // Delete all partial extractions on security or I/O failure
            destinationDir.deleteRecursively()
            throw t
        }
    }
}
