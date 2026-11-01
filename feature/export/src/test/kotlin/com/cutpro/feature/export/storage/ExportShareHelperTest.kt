package com.cutpro.feature.export.storage

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ExportShareHelperTest {

    @Test
    fun createShareIntent_containsRequiredActionAndExtras() {
        val testUri = Uri.parse("content://media/external/video/media/1234")
        val title = "Sample Project Render"

        val chooserIntent = ExportShareHelper.createShareIntent(testUri, title)
        assertThat(chooserIntent.action).isEqualTo(Intent.ACTION_CHOOSER)

        val targetIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertThat(targetIntent).isNotNull()
        assertThat(targetIntent!!.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(targetIntent.type).isEqualTo("video/mp4")
        assertThat(targetIntent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)).isEqualTo(testUri)
        assertThat(targetIntent.getStringExtra(Intent.EXTRA_SUBJECT)).isEqualTo(title)
        assertThat(targetIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)

        // Strict requirement: Chooser title is "Share Rendered Video via" with zero emojis
        val chooserTitle = chooserIntent.getStringExtra(Intent.EXTRA_TITLE)
        assertThat(chooserTitle).isEqualTo("Share Rendered Video via")
    }
}
