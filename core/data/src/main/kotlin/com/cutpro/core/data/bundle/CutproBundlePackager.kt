package com.cutpro.core.data.bundle

import android.content.ContentResolver
import android.net.Uri
import com.cutpro.core.model.ProjectManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CutproBundlePackager(
    private val contentResolver: ContentResolver
) {

    /**
     * Compresses a project manifest and all referenced media assets into a .cutprobundle ZIP archive.
     *
     * Archive layout:
     * project.cutproproj         <- JSON manifest root entry
     * media/                     <- Asset directory
     *   ├── asset_001.mp4
     *   └── asset_002.mp3
     */
    suspend fun packBundle(
        manifest: ProjectManifest,
        manifestJsonString: String,
        outputStream: OutputStream,
        onProgress: ((bytesWritten: Long, totalEstimate: Long) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(32 * 1024)
        var totalBytesWritten = 0L

        // Collect all distinct media sources across all timeline tracks
        val distinctClips = manifest.tracks.flatMap { it.clips }.distinctBy { it.mediaSource.uriString }

        var totalEstimatedBytes = manifestJsonString.toByteArray(Charsets.UTF_8).size.toLong()
        for (clip in distinctClips) {
            val uri = Uri.parse(clip.mediaSource.uriString)
            try {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { pfd ->
                    totalEstimatedBytes += pfd.length
                }
            } catch (e: Exception) {
                // Size query fallback
            }
        }

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zos ->
            zos.setLevel(Deflater.BEST_SPEED)

            // 1. Write project.cutproproj manifest entry at archive root
            val manifestEntry = ZipEntry(BundleArchiveLayout.MANIFEST_FILENAME)
            val manifestBytes = manifestJsonString.toByteArray(Charsets.UTF_8)
            manifestEntry.size = manifestBytes.size.toLong()
            zos.putNextEntry(manifestEntry)
            zos.write(manifestBytes)
            zos.closeEntry()
            totalBytesWritten += manifestBytes.size
            onProgress?.invoke(totalBytesWritten, totalEstimatedBytes)

            // 2. Write media assets into media/ subfolder
            val writtenEntryNames = mutableSetOf<String>()
            for (clip in distinctClips) {
                val uri = Uri.parse(clip.mediaSource.uriString)
                val sanitizedFileName = sanitizeFileName(clip.mediaSource.fileName)
                val entryName = "${BundleArchiveLayout.MEDIA_DIR}/$sanitizedFileName"

                var uniqueEntryName = entryName
                var duplicateCounter = 1
                while (writtenEntryNames.contains(uniqueEntryName)) {
                    val dotIdx = sanitizedFileName.lastIndexOf('.')
                    val nameNoExt = if (dotIdx != -1) sanitizedFileName.substring(0, dotIdx) else sanitizedFileName
                    val ext = if (dotIdx != -1) sanitizedFileName.substring(dotIdx) else ""
                    uniqueEntryName = "${BundleArchiveLayout.MEDIA_DIR}/${nameNoExt}_$duplicateCounter$ext"
                    duplicateCounter++
                }
                writtenEntryNames.add(uniqueEntryName)

                val mediaEntry = ZipEntry(uniqueEntryName)
                zos.putNextEntry(mediaEntry)

                var inputStream: InputStream? = null
                try {
                    inputStream = contentResolver.openInputStream(uri)
                        ?: throw FileNotFoundException("Could not open input stream for media URI: $uri")

                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        zos.write(buffer, 0, read)
                        totalBytesWritten += read
                        onProgress?.invoke(totalBytesWritten, totalEstimatedBytes)
                    }
                } finally {
                    inputStream?.close()
                    zos.closeEntry()
                }
            }
            zos.finish()
            zos.flush()
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }
}
