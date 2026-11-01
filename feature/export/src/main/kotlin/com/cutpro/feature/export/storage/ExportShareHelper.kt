package com.cutpro.feature.export.storage

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Helper for dispatching Android Share Sheet intents for rendered MP4 video files.
 * Strictly zero emojis.
 */
object ExportShareHelper {

    fun createShareIntent(videoUri: Uri, title: String): Intent {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, videoUri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "Share Rendered Video via")
    }

    fun shareVideo(context: Context, videoUri: Uri, title: String) {
        val chooserIntent = createShareIntent(videoUri, title)
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
    }
}
