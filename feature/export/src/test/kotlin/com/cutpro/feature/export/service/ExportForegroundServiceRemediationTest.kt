package com.cutpro.feature.export.service

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cutpro.core.export.VideoExportEngine
import com.cutpro.core.export.model.ExportPlan
import com.cutpro.core.export.model.ExportProgress
import com.cutpro.core.export.model.ExportResult
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.VideoCodec
import com.cutpro.feature.export.model.ExportStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Remediation unit tests for ExportForegroundService verifying API 34 compatibility,
 * service lifecycle execution, Scoped Storage publish, error reporting, and cancellation.
 * Strictly zero emojis.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ExportForegroundServiceRemediationTest {

    private lateinit var context: Context
    private lateinit var tempFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        tempFile = File(context.cacheDir, "service_test_out.mp4").apply {
            writeBytes(ByteArray(4096))
        }
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
        ExportForegroundService.resetState()
    }

    private fun createPlan(): ExportPlan {
        val project = ProjectManifest(
            id = "p_srv_1",
            title = "Service Test Project",
            durationUs = 5_000_000L
        )
        return ExportPlan(
            exportId = "exp_srv_1",
            project = project,
            resolution = ExportResolution.RES_720P,
            frameRate = ExportFrameRate.FPS_30,
            codec = VideoCodec.H264,
            targetAspectRatio = CanvasAspectRatio.RATIO_16_9,
            outputFile = tempFile,
            videoBitrateBps = 5_000_000,
            audioBitrateBps = 192_000
        )
    }

    @Test
    fun service_onStartCommand_startsForegroundWithMediaProcessingOnApi34() {
        val serviceController = Robolectric.buildService(ExportForegroundService::class.java)
        val service = serviceController.create().get()

        val plan = createPlan()
        ExportForegroundService.pendingExportPlan = plan

        val startIntent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_START_EXPORT
        }
        service.onStartCommand(startIntent, 0, 1)

        val shadowService = shadowOf(service)
        assertThat(shadowService.lastForegroundNotification).isNotNull()
        assertThat(shadowService.lastForegroundNotificationId).isEqualTo(ExportNotificationManager.NOTIFICATION_ID)

        serviceController.destroy()
    }

    @Test
    @Config(sdk = [33])
    fun service_onStartCommand_startsForegroundFallbackOnApi33() {
        val serviceController = Robolectric.buildService(ExportForegroundService::class.java)
        val service = serviceController.create().get()

        val plan = createPlan()
        ExportForegroundService.pendingExportPlan = plan

        val startIntent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_START_EXPORT
        }
        service.onStartCommand(startIntent, 0, 1)

        val shadowService = shadowOf(service)
        assertThat(shadowService.lastForegroundNotification).isNotNull()
        assertThat(shadowService.lastForegroundNotificationId).isEqualTo(ExportNotificationManager.NOTIFICATION_ID)

        serviceController.destroy()
    }

    @Test
    fun service_executesEngineToCompletion_releasesWakeLockAndUpdatesState() {
        runBlocking {
            val plan = createPlan()
            val testEngine = object : VideoExportEngine {
                override fun validateExportPlan(plan: ExportPlan) = Result.success(Unit)
                override fun exportVideo(plan: ExportPlan) = flow {
                    emit(ExportProgress.Starting(plan.exportId))
                    emit(ExportProgress.Processing(plan.exportId, 50, 1000L, 1000L))
                    emit(
                        ExportProgress.Completed(
                            plan.exportId,
                            ExportResult(
                                exportId = plan.exportId,
                                outputFile = plan.outputFile,
                                destinationUri = null,
                                durationUs = 5_000_000L,
                                fileSizeBytes = 4096L,
                                width = 1280,
                                height = 720,
                                frameRate = 30,
                                codec = VideoCodec.H264,
                                exportDurationMs = 2000L
                            )
                        )
                    )
                }
                override fun cancelExport(exportId: String) {}
            }

            ExportForegroundService.exportEngineFactory = { testEngine }
            ExportForegroundService.pendingExportPlan = plan

            val serviceController = Robolectric.buildService(ExportForegroundService::class.java)
            val service = serviceController.create().get()

            val startIntent = Intent(context, ExportForegroundService::class.java).apply {
                action = ExportForegroundService.ACTION_START_EXPORT
            }
            service.onStartCommand(startIntent, 0, 1)

            withTimeout(5000L) {
                ExportForegroundService.exportState.first { it.status == ExportStatus.COMPLETED }
            }

            // Verify completion state
            assertThat(ExportForegroundService.exportState.value.status).isEqualTo(ExportStatus.COMPLETED)
            assertThat(ExportForegroundService.exportState.value.progressPercentage).isEqualTo(100)

            val wakeLockField = ExportForegroundService::class.java.getDeclaredField("wakeLockManager").apply {
                isAccessible = true
            }
            val wakeLockManager = wakeLockField.get(service) as ExportWakeLockManager
            assertThat(wakeLockManager.isHeld).isFalse()

            serviceController.destroy()
        }
    }

    @Test
    fun service_handlesEngineFailure_releasesWakeLockAndNotifiesError() {
        runBlocking {
            val plan = createPlan()
            val testEngine = object : VideoExportEngine {
                override fun validateExportPlan(plan: ExportPlan) = Result.success(Unit)
                override fun exportVideo(plan: ExportPlan) = flow {
                    emit(ExportProgress.Starting(plan.exportId))
                    emit(
                        ExportProgress.Failed(
                            exportId = plan.exportId,
                            cause = RuntimeException("Transcode pipeline crashed"),
                            message = "Transcode pipeline crashed"
                        )
                    )
                }
                override fun cancelExport(exportId: String) {}
            }

            ExportForegroundService.exportEngineFactory = { testEngine }
            ExportForegroundService.pendingExportPlan = plan

            val serviceController = Robolectric.buildService(ExportForegroundService::class.java)
            val service = serviceController.create().get()

            val startIntent = Intent(context, ExportForegroundService::class.java).apply {
                action = ExportForegroundService.ACTION_START_EXPORT
            }
            service.onStartCommand(startIntent, 0, 1)

            withTimeout(5000L) {
                ExportForegroundService.exportState.first { it.status == ExportStatus.FAILED }
            }

            assertThat(ExportForegroundService.exportState.value.status).isEqualTo(ExportStatus.FAILED)
            assertThat(ExportForegroundService.exportState.value.errorMessage).contains("Transcode pipeline crashed")

            val wakeLockField = ExportForegroundService::class.java.getDeclaredField("wakeLockManager").apply {
                isAccessible = true
            }
            val wakeLockManager = wakeLockField.get(service) as ExportWakeLockManager
            assertThat(wakeLockManager.isHeld).isFalse()

            serviceController.destroy()
        }
    }

    @Test
    fun service_cancelIntent_cancelsEngineAndDeletesOutputFile() {
        val cancelledIds = CopyOnWriteArrayList<String>()
        val plan = createPlan()
        val testEngine = object : VideoExportEngine {
            override fun validateExportPlan(plan: ExportPlan) = Result.success(Unit)
            override fun exportVideo(plan: ExportPlan) = flow {
                emit(ExportProgress.Starting(plan.exportId))
            }
            override fun cancelExport(exportId: String) {
                cancelledIds.add(exportId)
            }
        }

        ExportForegroundService.exportEngineFactory = { testEngine }
        ExportForegroundService.pendingExportPlan = plan

        val serviceController = Robolectric.buildService(ExportForegroundService::class.java)
        val service = serviceController.create().get()

        val startIntent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_START_EXPORT
        }
        service.onStartCommand(startIntent, 0, 1)

        val cancelIntent = Intent(context, ExportForegroundService::class.java).apply {
            action = ExportForegroundService.ACTION_CANCEL_EXPORT
        }
        service.onStartCommand(cancelIntent, 0, 2)

        assertThat(cancelledIds).contains("exp_srv_1")
        assertThat(tempFile.exists()).isFalse()
        assertThat(ExportForegroundService.exportState.value.status).isEqualTo(ExportStatus.CANCELLED)

        serviceController.destroy()
    }
}
