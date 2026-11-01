package com.cutpro.core.data.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.cutpro.core.model.MediaSourceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

class CorruptedMediaException(message: String, cause: Throwable? = null) : IOException(message, cause)
class UnsupportedMediaFormatException(message: String) : IllegalArgumentException(message)

class MediaMetadataExtractor(private val context: Context) {

    private val supportedMimeTypes = setOf(
        // Video
        "video/mp4", "video/quicktime", "video/webm", "video/x-matroska", "video/3gpp",
        // Audio
        "audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/aac", "audio/flac", "audio/ogg",
        // Image
        "image/png", "image/jpeg", "image/webp"
    )

    /**
     * Extracts duration, dimensions, rotation, audio channels, and frame rate from a media URI.
     * Automatically handles smartphone rotation tags and rejects zero-byte or corrupted media.
     */
    suspend fun extractMetadata(uri: Uri): Result<MediaSourceInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = context.contentResolver

            // 1. Pre-check file length and existence
            val afd = contentResolver.openAssetFileDescriptor(uri, "r")
                ?: throw FileNotFoundException("Could not open file descriptor for URI: $uri")

            val fileSizeBytes = afd.use { it.length }
            if (fileSizeBytes == 0L) {
                throw CorruptedMediaException("Media file is 0 bytes: $uri")
            }

            // 2. Query Display Name and MIME type
            var displayName = "imported_media"
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        displayName = cursor.getString(nameIdx) ?: displayName
                    }
                }
            }

            var mimeType = contentResolver.getType(uri)
            if (mimeType == null) {
                mimeType = inferMimeTypeFromFileName(displayName)
            }

            // 3. Handle Static Images (Duration is 0, default 3s on timeline)
            if (mimeType.startsWith("image/")) {
                if (!supportedMimeTypes.contains(mimeType)) {
                    throw UnsupportedMediaFormatException("Unsupported image MIME type: $mimeType")
                }
                val (imgW, imgH) = extractImageDimensions(uri)
                return@runCatching MediaSourceInfo(
                    uriString = uri.toString(),
                    fileName = displayName,
                    mimeType = mimeType,
                    durationUs = 3_000_000L, // 3.0s default image duration
                    width = imgW,
                    height = imgH,
                    rotationDegrees = 0,
                    frameRate = 30.0f,
                    audioChannels = 0,
                    audioSampleRate = 0,
                    fileSizeBytes = fileSizeBytes.coerceAtLeast(0L)
                )
            }

            // 4. Use MediaMetadataRetriever for Video and Audio
            val retriever = MediaMetadataRetriever()
            try {
                try {
                    retriever.setDataSource(context, uri)
                } catch (e: RuntimeException) {
                    throw CorruptedMediaException("Failed to parse media container. File may be corrupted or missing header.", e)
                }

                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val durationMs = durationStr?.toLongOrNull() ?: 0L
                if (durationMs <= 0L) {
                    throw CorruptedMediaException("Media container reported invalid duration ($durationMs ms).")
                }
                val durationUs = durationMs * 1000L

                val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

                // Normalize rotation: swap width/height if video is rotated 90 or 270 degrees
                val (normalizedWidth, normalizedHeight) = if (rotation == 90 || rotation == 270) {
                    Pair(rawHeight, rawWidth)
                } else {
                    Pair(rawWidth, rawHeight)
                }

                val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null
                val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
                val channels = if (hasAudio) 2 else 0

                val fpsStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                val frameRate = fpsStr?.toFloatOrNull() ?: 30.0f

                val retrievedMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                val finalMimeType = retrievedMime ?: mimeType

                if (!supportedMimeTypes.contains(finalMimeType)) {
                    throw UnsupportedMediaFormatException("Unsupported media format: $finalMimeType")
                }

                MediaSourceInfo(
                    uriString = uri.toString(),
                    fileName = displayName,
                    mimeType = finalMimeType,
                    durationUs = durationUs,
                    width = normalizedWidth,
                    height = normalizedHeight,
                    rotationDegrees = rotation,
                    frameRate = frameRate,
                    audioChannels = channels,
                    audioSampleRate = sampleRate,
                    fileSizeBytes = fileSizeBytes.coerceAtLeast(0L)
                )
            } finally {
                retriever.release()
            }
        }
    }

    private fun extractImageDimensions(uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
        return Pair(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
    }

    private fun inferMimeTypeFromFileName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}
