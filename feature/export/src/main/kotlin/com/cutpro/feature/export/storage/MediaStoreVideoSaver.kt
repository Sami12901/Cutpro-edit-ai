package com.cutpro.feature.export.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves exported MP4 video files to shared storage (Movies/CutPro) using MediaStore IS_PENDING scoped storage.
 * Strictly zero emojis.
 */
class MediaStoreVideoSaver(private val context: Context) {

    fun saveVideoToMovies(
        tempFile: File,
        title: String,
        subDir: String = "CutPro"
    ): Result<Uri> = runCatching {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitizedTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val baseName = "CutPro_${sanitizedTitle}_$timestamp"
        val fileName = "$baseName.mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$subDir")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, contentValues)
            ?: throw IllegalStateException("Failed to create MediaStore entry")

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
            } ?: throw IllegalStateException("Failed to open output stream for $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } catch (e: Exception) {
            // Delete corrupt pending entry on failure
            try {
                resolver.delete(uri, null, null)
            } catch (_: Exception) {}
            throw e
        }

        uri
    }
}
