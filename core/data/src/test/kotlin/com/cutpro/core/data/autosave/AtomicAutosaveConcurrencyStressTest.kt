package com.cutpro.core.data.autosave

import com.cutpro.core.data.recovery.ProjectRecoveryDetector
import com.cutpro.core.data.recovery.RecoveryResult
import com.cutpro.core.data.serialization.ProjectJsonSerializer
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.ProjectManifest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Adversarial concurrency and corruption stress harness for AtomicAutosaveEngine.
 * Challenges:
 * - 100 concurrent coroutines attempting rapid atomic saves to the same target file
 * - Rapid concurrent mutations with AutosaveCoordinator
 * - Invariant: Zero file corruption, valid JSON serialization, and zero abandoned .tmp files
 * - Simulated low disk space safety checks
 * - Recovery detector integrity under simulated crash residue
 */
class AtomicAutosaveConcurrencyStressTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var projectDir: File
    private lateinit var targetFile: File
    private lateinit var engine: AtomicAutosaveEngine
    private lateinit var recoveryDetector: ProjectRecoveryDetector

    @Before
    fun setup() {
        projectDir = tempFolder.newFolder("stress-autosave")
        targetFile = File(projectDir, "project.cutproproj")
        engine = AtomicAutosaveEngine()
        recoveryDetector = ProjectRecoveryDetector()
    }

    private fun createRevisionManifest(revisionNumber: Int): ProjectManifest {
        return ProjectManifest(
            id = "proj-stress-$revisionNumber",
            title = "Autosaved Revision $revisionNumber",
            aspectRatio = CanvasAspectRatio.RATIO_16_9,
            defaultResolution = ExportResolution.RES_1080P,
            defaultFrameRate = ExportFrameRate.FPS_30
        )
    }

    @Test
    fun challengeConcurrentAutosave_rapidTriggers_zeroFileCorruption() = runBlocking {
        val concurrencyLevel = 100
        val results = (1..concurrencyLevel).map { rev ->
            async(Dispatchers.Default) {
                val manifest = createRevisionManifest(rev)
                engine.saveProjectAtomically(manifest, targetFile)
            }
        }.awaitAll()

        // Invariant 1: All atomic saves must complete successfully without crashing
        for ((idx, res) in results.withIndex()) {
            assertThat(res.isSuccess).isTrue()
        }

        // Invariant 2: Target file must exist and be non-empty
        assertThat(targetFile.exists()).isTrue()
        assertThat(targetFile.length()).isGreaterThan(0L)

        // Invariant 3: No staging temporary files should remain leaked
        val tempFile = File(projectDir, "project.cutproproj.tmp")
        assertThat(tempFile.exists()).isFalse()

        // Invariant 4: File content must be valid, uncorrupted JSON deserializeable to ProjectManifest
        val rawJson = targetFile.readText()
        val loadedManifest = ProjectJsonSerializer.deserialize(rawJson)

        assertThat(loadedManifest.id).startsWith("proj-stress-")
        assertThat(loadedManifest.title).startsWith("Autosaved Revision ")
        assertThat(loadedManifest.aspectRatio).isEqualTo(CanvasAspectRatio.RATIO_16_9)
        assertThat(loadedManifest.defaultResolution).isEqualTo(ExportResolution.RES_1080P)
        assertThat(loadedManifest.defaultFrameRate).isEqualTo(ExportFrameRate.FPS_30)
    }

    @Test
    fun challengeAutosaveCoordinator_rapidConcurrentMutations_maintainsIntegrity() = runBlocking {
        val coordinator = AutosaveCoordinator(
            scope = this,
            autosaveEngine = engine,
            debounceDelayMs = 50L // Short debounce for stress testing
        )

        // Dispatch 50 rapid mutations
        for (i in 1..50) {
            coordinator.onProjectMutated(createRevisionManifest(i), targetFile)
        }

        // Immediately trigger synchronous save
        val saveResult = coordinator.triggerAutosaveNow()
        assertThat(saveResult.isSuccess).isTrue()

        // Verify target file is intact
        assertThat(targetFile.exists()).isTrue()
        val loaded = ProjectJsonSerializer.deserialize(targetFile.readText())
        assertThat(loaded.id).startsWith("proj-stress-")
    }

    @Test
    fun challengeDiskSpaceExhaustion_abortsSafelyWithoutCorruptingTarget() = runBlocking {
        // Save initial valid project state
        val initialManifest = createRevisionManifest(1)
        val initialResult = engine.saveProjectAtomically(initialManifest, targetFile)
        assertThat(initialResult.isSuccess).isTrue()
        val initialContent = targetFile.readText()

        // Now attempt to save with an impossibly high minFreeBytes requirement
        val nextManifest = createRevisionManifest(2)
        val constrainedResult = engine.saveProjectAtomically(
            manifest = nextManifest,
            targetFile = targetFile,
            minFreeBytes = Long.MAX_VALUE
        )

        // Invariant 1: Must safely reject with InsufficientDiskSpaceException
        assertThat(constrainedResult.isFailure).isTrue()
        assertThat(constrainedResult.exceptionOrNull()).isInstanceOf(InsufficientDiskSpaceException::class.java)

        // Invariant 2: Original file content must NOT be corrupted or modified
        assertThat(targetFile.exists()).isTrue()
        assertThat(targetFile.readText()).isEqualTo(initialContent)

        // Invariant 3: Staging file must not be promoted
        val tempFile = File(projectDir, "project.cutproproj.tmp")
        assertThat(tempFile.exists()).isFalse()
    }

    @Test
    fun challengeRecoveryDetector_discardsCorruptedTempAndPreservesPrimary() = runBlocking {
        val goodManifest = createRevisionManifest(10)
        targetFile.writeText(ProjectJsonSerializer.serialize(goodManifest))

        // Create a truncated, corrupted temp file
        val tempFile = File(projectDir, "project.cutproproj.tmp")
        tempFile.writeText("{\"version\": 1, \"metadata\": { \"title\": \"Trun---")

        val result = recoveryDetector.checkAndRecover(targetFile)

        assertThat(result).isInstanceOf(RecoveryResult.CorruptedTempDiscarded::class.java)
        assertThat(tempFile.exists()).isFalse()
        assertThat(targetFile.exists()).isTrue()

        val active = ProjectJsonSerializer.deserialize(targetFile.readText())
        assertThat(active.title).isEqualTo("Autosaved Revision 10")
    }
}
