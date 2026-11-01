package com.cutpro.feature.export.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ExportWakeLockManagerTest {

    private lateinit var context: Context
    private lateinit var wakeLockManager: ExportWakeLockManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        wakeLockManager = ExportWakeLockManager(context)
    }

    @Test
    fun acquireAndRelease_lifecycleCleanliness() {
        assertThat(wakeLockManager.isHeld).isFalse()

        wakeLockManager.acquire("CutPro:TestLock", timeoutMs = 60_000L)
        assertThat(wakeLockManager.isHeld).isTrue()

        wakeLockManager.release()
        assertThat(wakeLockManager.isHeld).isFalse()

        // Double release is idempotent and does not throw
        wakeLockManager.release()
        assertThat(wakeLockManager.isHeld).isFalse()
    }
}
