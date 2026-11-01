package com.cutpro.feature.export.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cutpro.core.export.VideoExportEngine
import com.cutpro.core.export.model.ExportPlan
import com.cutpro.core.export.model.ExportProgress
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.RenderPlan
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.feature.export.model.ExportProgressState
import com.cutpro.feature.export.model.ExportStatus
import com.cutpro.feature.export.model.ExportValidationResult
import com.cutpro.feature.export.service.ExportForegroundService
import com.cutpro.feature.export.validator.PreExportValidator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for ExportViewModel verifying dialog state, pre-export validation gating,
 * service lifecycle coordination, and share interaction.
 * Strictly zero emojis.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ExportViewModelTest {

    private lateinit var context: Context
    private lateinit var viewModel: ExportViewModel
    private lateinit var validSourceFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        viewModel = ExportViewModel()
        validSourceFile = File(context.cacheDir, "test_vm_source.mp4").apply {
            writeBytes(ByteArray(1024))
        }
    }

    @After
    fun tearDown() {
        if (validSourceFile.exists()) {
            validSourceFile.delete()
        }
        ExportForegroundService.resetState()
    }

    private fun createValidProject(): ProjectManifest {
        val media = MediaSourceInfo(
            uriString = validSourceFile.toURI().toString(),
            fileName = "test_vm_source.mp4",
            mimeType = "video/mp4",
            durationUs = 10_000_000L,
            width = 1920,
            height = 1080
        )
        val clip = TimelineClip(
            id = "c1",
            trackId = "t1",
            mediaSource = media,
            timelineStartUs = 0L,
            timelineDurationUs = 10_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L
        )
        val track = TimelineTrack(
            id = "t1",
            name = "Video",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip)
        )
        return ProjectManifest(
            id = "p1",
            title = "Valid Project",
            durationUs = 10_000_000L,
            tracks = listOf(track)
        )
    }

    @Test
    fun dialogState_openAndDismiss() {
        assertThat(viewModel.showConfigDialog.value).isFalse()

        val project = ProjectManifest(
            id = "p1",
            title = "My Project",
            durationUs = 10_000_000L,
            tracks = emptyList()
        )

        viewModel.openConfigDialog(project)
        assertThat(viewModel.showConfigDialog.value).isTrue()

        viewModel.dismissConfigDialog()
        assertThat(viewModel.showConfigDialog.value).isFalse()
    }

    @Test
    fun progressSheet_dismissal() {
        viewModel.dismissProgressSheet()
        assertThat(viewModel.showProgressSheet.value).isFalse()
    }

    @Test
    fun startExport_whenValidationFailsOnEmptyTimeline_setsFailedStateImmediately() {
        val emptyProject = ProjectManifest(
            id = "empty_p",
            title = "Empty",
            durationUs = 0L,
            tracks = emptyList()
        )
        val plan = RenderPlan(durationUs = 0L)

        viewModel.startExport(context, emptyProject, plan)

        assertThat(viewModel.showConfigDialog.value).isFalse()
        assertThat(viewModel.showProgressSheet.value).isTrue()
        assertThat(viewModel.progressState.value.status).isEqualTo(ExportStatus.FAILED)
        assertThat(viewModel.progressState.value.errorMessage).contains("Empty timeline")
    }

    @Test
    fun startExport_withCustomPreExportValidator_rejectsOnStorageDeficit() {
        val mockValidator = object : PreExportValidator(context) {
            override fun validate(project: ProjectManifest, plan: RenderPlan, targetDir: File): ExportValidationResult {
                return ExportValidationResult.InsufficientStorage(
                    requiredBytes = 500_000_000L,
                    availableBytes = 100_000_000L,
                    message = "Insufficient storage: needed 500MB, available 100MB"
                )
            }
        }
        val customVm = ExportViewModel(preValidator = mockValidator)
        val project = createValidProject()
        val plan = RenderPlan(durationUs = 10_000_000L)

        customVm.startExport(context, project, plan)

        assertThat(customVm.progressState.value.status).isEqualTo(ExportStatus.FAILED)
        assertThat(customVm.progressState.value.errorMessage).contains("Insufficient storage")
    }

    @Test
    fun startExport_whenValidationPasses_startsForegroundServiceAndObservesProgress() {
        val testEngine = object : VideoExportEngine {
            override fun validateExportPlan(plan: ExportPlan) = Result.success(Unit)
            override fun exportVideo(plan: ExportPlan) = flow {
                emit(ExportProgress.Starting(plan.exportId))
            }
            override fun cancelExport(exportId: String) {}
        }

        val vm = ExportViewModel(exportEngine = testEngine)
        val project = createValidProject()
        val plan = RenderPlan(durationUs = 10_000_000L)

        vm.startExport(context, project, plan)

        assertThat(vm.showConfigDialog.value).isFalse()
        assertThat(vm.showProgressSheet.value).isTrue()
        assertThat(vm.progressState.value.status).isEqualTo(ExportStatus.PREPARING)
    }

    @Test
    fun cancelExport_triggersForegroundServiceCancelAndSetsCancelledState() {
        viewModel.cancelExport(context)
        assertThat(viewModel.progressState.value.status).isEqualTo(ExportStatus.CANCELLED)
    }

    @Test
    fun shareExportedVideo_withNullUri_doesNotCrash() {
        // OutputUri is initially null
        assertThat(viewModel.progressState.value.outputUri).isNull()
        viewModel.shareExportedVideo(context)
    }
}
