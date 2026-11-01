package com.cutpro.core.data.media

import android.content.Context
import android.net.Uri
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.security.storage.UriPermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class MediaImportManager(
    private val context: Context,
    private val permissionManager: UriPermissionManager,
    private val metadataExtractor: MediaMetadataExtractor
) {

    /**
     * Imports a media URI into a project:
     * 1. Validates metadata and rejects corrupt/zero-byte files.
     * 2. Attempts to acquire persistable URI read permissions.
     * 3. If persistable permission succeeds, retains original URI reference (zero storage duplication).
     * 4. If persistable permission fails (e.g. transient Photo Picker URI), copies the file to project internal storage.
     */
    suspend fun importMedia(
        projectId: String,
        uri: Uri
    ): Result<MediaSourceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            // Step 1: Extract and validate metadata
            val rawMetadata = metadataExtractor.extractMetadata(uri).getOrThrow()

            // Step 2: Test persistable URI grant
            val isPersisted = permissionManager.takePersistableReadPermission(uri)

            val finalUriString = if (isPersisted) {
                // Persistent grant successful, store direct reference
                uri.toString()
            } else {
                // Transient Photo Picker grant: copy to internal project directory to survive reboot
                copyToProjectStorage(projectId, uri, rawMetadata.fileName)
            }

            rawMetadata.copy(uriString = finalUriString)
        }
    }

    private fun copyToProjectStorage(projectId: String, sourceUri: Uri, originalName: String): String {
        val projectMediaDir = File(context.filesDir, "projects/$projectId/media")
        if (!projectMediaDir.exists()) projectMediaDir.mkdirs()

        val uniquePrefix = UUID.randomUUID().toString().substring(0, 8)
        val sanitized = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val destinationFile = File(projectMediaDir, "${uniquePrefix}_$sanitized")

        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw java.io.FileNotFoundException("Cannot open source URI: $sourceUri")
            outputStream = FileOutputStream(destinationFile)
            val buffer = ByteArray(32 * 1024)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
        } finally {
            inputStream?.close()
            outputStream?.close()
        }

        return Uri.fromFile(destinationFile).toString()
    }
}
