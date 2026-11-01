package com.cutpro.core.data.autosave

import com.cutpro.core.data.recovery.ProjectRecoveryDetector
import com.cutpro.core.data.recovery.RecoveryResult
import com.cutpro.core.data.serialization.ProjectJsonSerializer
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.ProjectManifest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicAutosaveAndRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var targetFile: File
    private lateinit var engine: AtomicAutosaveEngine
    private lateinit var recoveryDetector: ProjectRecoveryDetector

    private val testManifest = ProjectManifest(
        id = "proj-autosave",
        title = "Autosaved Project",
        aspectRatio = CanvasAspectRatio.RATIO_16_9,
        defaultResolution = ExportResolution.RES_1080P,
        defaultFrameRate = ExportFrameRate.FPS_30
    )

    @Before
    fun setup() {
        projectDir = tempFolder.newFolder("projects", "proj-autosave")
        targetFile = File(projectDir, "project.cutproproj")
        engine = AtomicAutosaveEngine()
        recoveryDetector = ProjectRecoveryDetector()
    }

    @Test
    fun testAtomicSaveCreatesPrimaryFileAndLeavesNoTemp() = runBlocking {
        val result = engine.saveProjectAtomically(testManifest, targetFile)
        assertThat(result.isSuccess).isTrue()

        assertThat(targetFile.exists()).isTrue()
        val tempFile = File(projectDir, "project.cutproproj.tmp")
        assertThat(tempFile.exists()).isFalse()

        val loaded = ProjectJsonSerializer.deserialize(targetFile.readText())
        assertThat(loaded.id).isEqualTo(testManifest.id)
        assertThat(loaded.title).isEqualTo(testManifest.title)
    }

    @Test
    fun testCrashRecoveryPromotesValidNewerTempFile() = runBlocking {
        // Old target file
        val oldManifest = testManifest.copy(title = "Old Version")
        targetFile.writeText(ProjectJsonSerializer.serialize(oldManifest))
        targetFile.setLastModified(1000L)

        // Newer abandoned .tmp file
        val tempFile = File(projectDir, "project.cutproproj.tmp")
        val newerManifest = testManifest.copy(title = "Newer Recovered Version")
        tempFile.writeText(ProjectJsonSerializer.serialize(newerManifest))
        tempFile.setLastModified(5000L)

        val recoveryResult = recoveryDetector.checkAndRecover(targetFile)

        assertThat(recoveryResult).isInstanceOf(RecoveryResult.RecoveredFromTemp::class.java)
        assertThat(tempFile.exists()).isFalse()
        assertThat(targetFile.exists()).isTrue()

        val restored = ProjectJsonSerializer.deserialize(targetFile.readText())
        assertThat(restored.title).isEqualTo("Newer Recovered Version")
    }

    @Test
    fun testCrashRecoveryPurgesCorruptTempFile() = runBlocking {
        // Intact primary file
        targetFile.writeText(ProjectJsonSerializer.serialize(testManifest))

        // Corrupted, truncated .tmp file
        val tempFile = File(projectDir, "project.cutproproj.tmp")
        tempFile.writeText("{\"version\": 1, \"metadata\": { \"title\": \"Incomplete...")

        val recoveryResult = recoveryDetector.checkAndRecover(targetFile)

        assertThat(recoveryResult).isInstanceOf(RecoveryResult.CorruptedTempDiscarded::class.java)
        assertThat(tempFile.exists()).isFalse()
        assertThat(targetFile.exists()).isTrue()

        val restored = ProjectJsonSerializer.deserialize(targetFile.readText())
        assertThat(restored.title).isEqualTo("Autosaved Project")
    }

    @Test
    fun testCrashRecoveryWhenNoTempFileExists() = runBlocking {
        targetFile.writeText(ProjectJsonSerializer.serialize(testManifest))
        val recoveryResult = recoveryDetector.checkAndRecover(targetFile)
        assertThat(recoveryResult).isEqualTo(RecoveryResult.NoTempFound)
    }
}
