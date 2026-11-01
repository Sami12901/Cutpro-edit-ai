package com.cutpro.core.timeline

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.core.timeline.command.AddClipCommand
import com.cutpro.core.timeline.command.CompoundTimelineCommand
import com.cutpro.core.timeline.command.DeleteClipCommand
import com.cutpro.core.timeline.command.RepositionClipCommand
import com.cutpro.core.timeline.command.SnapshotTimelineCommand
import com.cutpro.core.timeline.command.SplitClipCommand
import com.cutpro.core.timeline.command.TimelineCommand
import com.cutpro.core.timeline.command.TrimClipCommand
import com.cutpro.core.timeline.command.UpdateClipCommand
import com.cutpro.core.timeline.undo.UndoRedoManager
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import java.util.Random

/**
 * Adversarial stress test suite for UndoRedoManager and Timeline command operations.
 * Evaluates:
 * 1. Max capacity stress (>50 pushes, LRU eviction, memory profile)
 * 2. Branching history and redo stack clearing
 * 3. Empty stack safety and repetitive no-ops
 * 4. Compound command atomicity and strict reverse rollback ordering
 * 5. State immutability & reference preservation
 * 6. Fuzzing with interleaved pseudorandom operations
 * 7. Boundary conditions (capacity 0, 1) and cyclic stability
 */
class UndoRedoAdversarialTest {

    private lateinit var manager: UndoRedoManager
    private lateinit var baseProject: ProjectManifest
    private val trackId = "track_v1"

    @Before
    fun setUp() {
        manager = UndoRedoManager(maxHistoryLimit = 50)
        val track = TimelineTrack(
            id = trackId,
            name = "Video 1",
            type = TrackType.VIDEO,
            zIndex = 0
        )
        baseProject = ProjectManifest(
            id = "proj_adv_test",
            title = "Adversarial Test Project",
            aspectRatio = CanvasAspectRatio.RATIO_16_9,
            defaultResolution = ExportResolution.RES_1080P,
            defaultFrameRate = ExportFrameRate.FPS_30,
            durationUs = 0L,
            tracks = listOf(track)
        )
    }

    private fun createClip(id: String, startUs: Long, durationUs: Long = 1_000_000L): TimelineClip {
        return TimelineClip(
            id = id,
            trackId = trackId,
            mediaSource = MediaSourceInfo(
                uriString = "content://media/$id",
                fileName = "$id.mp4",
                mimeType = "video/mp4",
                durationUs = 60_000_000L
            ),
            timelineStartUs = startUs,
            timelineDurationUs = durationUs,
            sourceInUs = 0L,
            sourceOutUs = durationUs
        )
    }

    // =========================================================================
    // 1. Max Capacity Stress & LRU Eviction (>50 commands)
    // =========================================================================

    @Test
    fun maxCapacity_push100Commands_enforcesExact50LimitAndEvictsOldest50LRU() {
        var state = baseProject

        // Push 100 consecutive AddClipCommands
        for (i in 1..100) {
            val clip = createClip("clip_$i", startUs = (i - 1) * 1_000_000L)
            state = manager.execute(AddClipCommand(trackId, clip), state)
        }

        // Verify ceiling is strictly enforced at 50
        assertThat(manager.undoStackSize).isEqualTo(50)
        assertThat(manager.canUndo).isTrue()
        assertThat(manager.redoStackSize).isEqualTo(0)
        assertThat(manager.canRedo).isFalse()

        // Verify all 100 clips exist in the current project state
        val currentClips = state.tracks.first().clips
        assertThat(currentClips).hasSize(100)

        // Now perform 50 undos in sequence: should undo clip_100 down to clip_51
        for (i in 100 downTo 51) {
            assertThat(manager.canUndo).isTrue()
            state = manager.undo(state)!!
            val clipsAfterUndo = state.tracks.first().clips
            assertThat(clipsAfterUndo).hasSize(i - 1)
            // Verify that the removed clip was indeed clip_i
            assertThat(clipsAfterUndo.none { it.id == "clip_$i" }).isTrue()
        }

        // At this point, exact 50 commands undone.
        // Undo stack must now be empty; commands 1..50 were evicted and cannot be undone
        assertThat(manager.undoStackSize).isEqualTo(0)
        assertThat(manager.canUndo).isFalse()
        assertThat(manager.redoStackSize).isEqualTo(50)
        assertThat(manager.canRedo).isTrue()

        // 50 oldest clips remain in project because their Add commands were pruned by LRU
        val remainingClips = state.tracks.first().clips
        assertThat(remainingClips).hasSize(50)
        assertThat(remainingClips.first().id).isEqualTo("clip_1")
        assertThat(remainingClips.last().id).isEqualTo("clip_50")

        // 51st undo must be a safe no-op returning null
        val noOpUndo = manager.undo(state)
        assertThat(noOpUndo).isNull()
        assertThat(manager.undoStackSize).isEqualTo(0)
        assertThat(manager.redoStackSize).isEqualTo(50)

        // Now perform 50 redos: should restore clip_51 up to clip_100
        for (i in 51..100) {
            assertThat(manager.canRedo).isTrue()
            state = manager.redo(state)!!
            val clipsAfterRedo = state.tracks.first().clips
            assertThat(clipsAfterRedo).hasSize(i)
            assertThat(clipsAfterRedo.any { it.id == "clip_$i" }).isTrue()
        }

        // Redo stack must now be empty, undo stack full at 50
        assertThat(manager.redoStackSize).isEqualTo(0)
        assertThat(manager.canRedo).isFalse()
        assertThat(manager.undoStackSize).isEqualTo(50)

        // 51st redo must be a safe no-op returning null
        val noOpRedo = manager.redo(state)
        assertThat(noOpRedo).isNull()
        assertThat(manager.redoStackSize).isEqualTo(0)
    }

    @Test
    fun massiveCapacityStress_10000Commands_constantStackSizeAndNoOOM() {
        var state = baseProject
        val clip = createClip("fixed_clip", startUs = 0L)
        state = manager.execute(AddClipCommand(trackId, clip), state)

        // Perform 10,000 rapid mutations
        for (i in 1..10_000) {
            val updated = state.copy(title = "Iteration $i", modifiedAt = i.toLong())
            val cmd = SnapshotTimelineCommand("Step $i", state, updated)
            state = manager.execute(cmd, state)

            // Invariant: Stack size must NEVER exceed 50
            if (i % 500 == 0) {
                assertThat(manager.undoStackSize).isEqualTo(50)
                assertThat(manager.redoStackSize).isEqualTo(0)
            }
        }

        assertThat(manager.undoStackSize).isEqualTo(50)
        assertThat(state.title).isEqualTo("Iteration 10000")

        // Roll back the full 50 allowed steps
        for (i in 1..50) {
            state = manager.undo(state)!!
        }
        assertThat(manager.undoStackSize).isEqualTo(0)
        assertThat(manager.redoStackSize).isEqualTo(50)
        assertThat(state.title).isEqualTo("Iteration 9950")
    }

    // =========================================================================
    // 2. Branching History & Redo Invalidation
    // =========================================================================

    @Test
    fun branchingHistory_execute20_undo10_executeNew_completelyClearsRedoStack() {
        var state = baseProject

        // 1. Execute 20 commands
        for (i in 1..20) {
            val clip = createClip("clip_$i", startUs = (i - 1) * 1_000_000L)
            state = manager.execute(AddClipCommand(trackId, clip), state)
        }
        assertThat(manager.undoStackSize).isEqualTo(20)
        assertThat(manager.redoStackSize).isEqualTo(0)

        // 2. Undo 10 commands
        for (i in 1..10) {
            state = manager.undo(state)!!
        }
        assertThat(manager.undoStackSize).isEqualTo(10)
        assertThat(manager.redoStackSize).isEqualTo(10)
        assertThat(manager.canRedo).isTrue()

        // 3. Execute 1 new branch command
        val branchClip = createClip("clip_branch", startUs = 999_000_000L)
        state = manager.execute(AddClipCommand(trackId, branchClip), state)

        // 4. Verify redo stack is completely obliterated
        assertThat(manager.redoStackSize).isEqualTo(0)
        assertThat(manager.canRedo).isFalse()
        assertThat(manager.undoStackSize).isEqualTo(11) // 10 remaining + 1 branch

        // 5. Redo must be safe no-op
        assertThat(manager.redo(state)).isNull()

        // 6. Undo must revert the branch clip, leaving 10 clips
        state = manager.undo(state)!!
        assertThat(state.tracks.first().clips).hasSize(10)
        assertThat(state.tracks.first().clips.none { it.id == "clip_branch" }).isTrue()
        assertThat(manager.undoStackSize).isEqualTo(10)
        assertThat(manager.redoStackSize).isEqualTo(1)

        // 7. Redo now restores the branch clip, NOT the old clip_11
        state = manager.redo(state)!!
        assertThat(state.tracks.first().clips).hasSize(11)
        assertThat(state.tracks.first().clips.any { it.id == "clip_branch" }).isTrue()
        assertThat(state.tracks.first().clips.none { it.id == "clip_11" }).isTrue()
    }

    @Test
    fun branchingHistory_pushNewCommand_alsoClearsRedoStack() {
        val clip1 = createClip("clip_1", 0L)
        val clip2 = createClip("clip_2", 1_000_000L)

        var state = manager.execute(AddClipCommand(trackId, clip1), baseProject)
        state = manager.execute(AddClipCommand(trackId, clip2), state)
        assertThat(manager.undoStackSize).isEqualTo(2)

        // Undo clip2
        state = manager.undo(state)!!
        assertThat(manager.canRedo).isTrue()
        assertThat(manager.redoStackSize).isEqualTo(1)

        // Push a manual command without execute()
        val dummyCmd = SnapshotTimelineCommand("Manual Push", state, state)
        manager.push(dummyCmd)

        // Verify redo stack is cleared by push()
        assertThat(manager.canRedo).isFalse()
        assertThat(manager.redoStackSize).isEqualTo(0)
        assertThat(manager.undoStackSize).isEqualTo(2)
    }

    // =========================================================================
    // 3. Empty Stack Safety & Edge Cases
    // =========================================================================

    @Test
    fun emptyStack_safetyAndRepeatedNoOpsDoNotCrashOrCorrupt() {
        assertThat(manager.canUndo).isFalse()
        assertThat(manager.canRedo).isFalse()
        assertThat(manager.undoStackSize).isEqualTo(0)
        assertThat(manager.redoStackSize).isEqualTo(0)

        // 100 consecutive undos on empty stack
        for (i in 1..100) {
            val result = manager.undo(baseProject)
            assertThat(result).isNull()
            assertThat(manager.undoStackSize).isEqualTo(0)
            assertThat(manager.redoStackSize).isEqualTo(0)
        }

        // 100 consecutive redos on empty stack
        for (i in 1..100) {
            val result = manager.redo(baseProject)
            assertThat(result).isNull()
            assertThat(manager.undoStackSize).isEqualTo(0)
            assertThat(manager.redoStackSize).isEqualTo(0)
        }
    }

    @Test
    fun emptyStack_clearOnEmptyAndPopulatedStacks() {
        // Clear on already-empty manager
        manager.clear()
        assertThat(manager.undoStackSize).isEqualTo(0)
        assertThat(manager.redoStackSize).isEqualTo(0)

        // Add 10 commands
        var state = baseProject
        for (i in 1..10) {
            state = manager.execute(AddClipCommand(trackId, createClip("c_$i", i * 1_000_000L)), state)
        }
        manager.undo(state) // creates 1 redo item
        assertThat(manager.undoStackSize).isEqualTo(9)
        assertThat(manager.redoStackSize).isEqualTo(1)

        // Clear resets all stacks cleanly
        manager.clear()
        assertThat(manager.undoStackSize).isEqualTo(0)
        assertThat(manager.redoStackSize).isEqualTo(0)
        assertThat(manager.canUndo).isFalse()
        assertThat(manager.canRedo).isFalse()
        assertThat(manager.undo(state)).isNull()
        assertThat(manager.redo(state)).isNull()
    }

    // =========================================================================
    // 4. Compound Command Atomicity & Rollback Ordering
    // =========================================================================

    @Test
    fun compoundCommand_atomicityAndStrictReverseRollbackOrder() {
        val executionOrder = mutableListOf<String>()

        class MonitoredCommand(val tag: String) : TimelineCommand {
            override val description: String = "Cmd $tag"
            override fun execute(current: ProjectManifest): ProjectManifest {
                executionOrder.add("EXEC_$tag")
                return current.copy(title = "Title_$tag")
            }
            override fun undo(current: ProjectManifest): ProjectManifest {
                executionOrder.add("UNDO_$tag")
                return current.copy(title = "Undo_$tag")
            }
        }

        val child1 = MonitoredCommand("1")
        val child2 = MonitoredCommand("2")
        val child3 = MonitoredCommand("3")
        val child4 = MonitoredCommand("4")
        val child5 = MonitoredCommand("5")

        val compound = CompoundTimelineCommand("5-Step Compound", listOf(child1, child2, child3, child4, child5))

        // Execute compound
        var state = manager.execute(compound, baseProject)
        assertThat(executionOrder).containsExactly(
            "EXEC_1", "EXEC_2", "EXEC_3", "EXEC_4", "EXEC_5"
        ).inOrder()
        assertThat(manager.undoStackSize).isEqualTo(1)

        executionOrder.clear()

        // Undo compound -> MUST execute children in strictly REVERSED order (5, 4, 3, 2, 1)
        state = manager.undo(state)!!
        assertThat(executionOrder).containsExactly(
            "UNDO_5", "UNDO_4", "UNDO_3", "UNDO_2", "UNDO_1"
        ).inOrder()
        assertThat(manager.undoStackSize).isEqualTo(0)
        assertThat(manager.redoStackSize).isEqualTo(1)

        executionOrder.clear()

        // Redo compound -> MUST re-execute in forward order (1, 2, 3, 4, 5)
        state = manager.redo(state)!!
        assertThat(executionOrder).containsExactly(
            "EXEC_1", "EXEC_2", "EXEC_3", "EXEC_4", "EXEC_5"
        ).inOrder()
        assertThat(manager.undoStackSize).isEqualTo(1)
        assertThat(manager.redoStackSize).isEqualTo(0)
    }

    @Test
    fun compoundCommand_realTimelineOperations_fullLifecycleAndFidelity() {
        val clipA = createClip("clip_A", 0L, 4_000_000L)
        val clipB = createClip("clip_B", 4_000_000L, 4_000_000L)

        // Initial setup: Add clipA and clipB
        var state = manager.execute(AddClipCommand(trackId, clipA), baseProject)
        state = manager.execute(AddClipCommand(trackId, clipB), state)
        assertThat(state.tracks.first().clips).hasSize(2)
        assertThat(state.durationUs).isEqualTo(8_000_000L)

        // Compound command: Split clipA at 2s into A1, A2, and Trim clipB in-point
        val clipA1 = clipA.copy(id = "clip_A1", timelineDurationUs = 2_000_000L, sourceOutUs = 2_000_000L)
        val clipA2 = clipA.copy(id = "clip_A2", timelineStartUs = 2_000_000L, timelineDurationUs = 2_000_000L, sourceInUs = 2_000_000L)
        val trimmedClipB = clipB.copy(timelineStartUs = 5_000_000L, timelineDurationUs = 3_000_000L, sourceInUs = 1_000_000L)

        val compound = CompoundTimelineCommand(
            description = "Split A and Trim B",
            commands = listOf(
                SplitClipCommand(trackId, clipA, clipA1, clipA2),
                TrimClipCommand(trackId, clipB, trimmedClipB)
            )
        )

        // Execute
        state = manager.execute(compound, state)
        val clipsAfterCompound = state.tracks.first().clips
        assertThat(clipsAfterCompound).hasSize(3) // A1, A2, trimmedB
        assertThat(clipsAfterCompound.map { it.id }).containsExactly("clip_A1", "clip_A2", "clip_B").inOrder()
        assertThat(clipsAfterCompound.last().timelineStartUs).isEqualTo(5_000_000L)

        // Undo
        state = manager.undo(state)!!
        val clipsAfterUndo = state.tracks.first().clips
        assertThat(clipsAfterUndo).hasSize(2)
        assertThat(clipsAfterUndo.map { it.id }).containsExactly("clip_A", "clip_B").inOrder()
        assertThat(clipsAfterUndo.first().timelineDurationUs).isEqualTo(4_000_000L)
        assertThat(clipsAfterUndo.last().timelineStartUs).isEqualTo(4_000_000L)

        // Redo
        state = manager.redo(state)!!
        val clipsAfterRedo = state.tracks.first().clips
        assertThat(clipsAfterRedo).hasSize(3)
        assertThat(clipsAfterRedo.map { it.id }).containsExactly("clip_A1", "clip_A2", "clip_B").inOrder()
    }

    @Test
    fun nestedCompoundCommands_hierarchicalReverseExecutionAndRollback() {
        val executionOrder = mutableListOf<String>()

        class TaggedCmd(val tag: String) : TimelineCommand {
            override val description: String = tag
            override fun execute(current: ProjectManifest): ProjectManifest {
                executionOrder.add("EXEC_$tag")
                return current
            }
            override fun undo(current: ProjectManifest): ProjectManifest {
                executionOrder.add("UNDO_$tag")
                return current
            }
        }

        val innerCompound = CompoundTimelineCommand("Inner", listOf(TaggedCmd("B"), TaggedCmd("C")))
        val outerCompound = CompoundTimelineCommand("Outer", listOf(TaggedCmd("A"), innerCompound, TaggedCmd("D")))

        var state = manager.execute(outerCompound, baseProject)
        assertThat(executionOrder).containsExactly("EXEC_A", "EXEC_B", "EXEC_C", "EXEC_D").inOrder()

        executionOrder.clear()
        state = manager.undo(state)!!
        assertThat(executionOrder).containsExactly("UNDO_D", "UNDO_C", "UNDO_B", "UNDO_A").inOrder()

        executionOrder.clear()
        state = manager.redo(state)!!
        assertThat(executionOrder).containsExactly("EXEC_A", "EXEC_B", "EXEC_C", "EXEC_D").inOrder()
    }

    @Test
    fun emptyCompoundCommand_returnsIdenticalStateWithoutSideEffects() {
        val emptyCompound = CompoundTimelineCommand("Empty", emptyList())
        var state = manager.execute(emptyCompound, baseProject)
        assertThat(state).isEqualTo(baseProject)
        assertThat(manager.undoStackSize).isEqualTo(1)

        state = manager.undo(state)!!
        assertThat(state).isEqualTo(baseProject)
        assertThat(manager.undoStackSize).isEqualTo(0)

        state = manager.redo(state)!!
        assertThat(state).isEqualTo(baseProject)
    }

    // =========================================================================
    // 5. State Immutability & Reference Preservation
    // =========================================================================

    @Test
    fun stateImmutability_verifiesManifestsAndTracksAreNeverMutatedInPlace() {
        val initialProject = baseProject
        val clip = createClip("clip_immutable", 0L)

        // Execute Add
        val postAdd = manager.execute(AddClipCommand(trackId, clip), initialProject)

        // Verify initialProject reference was completely untouched
        assertThat(initialProject.tracks.first().clips).isEmpty()
        assertThat(initialProject.durationUs).isEqualTo(0L)
        assertThat(postAdd.tracks.first().clips).hasSize(1)
        assertThat(postAdd).isNotSameInstanceAs(initialProject)

        // Execute Undo
        val postUndo = manager.undo(postAdd)!!
        assertThat(postUndo.tracks.first().clips).isEmpty()
        // postAdd instance retains its clip
        assertThat(postAdd.tracks.first().clips).hasSize(1)
    }

    // =========================================================================
    // 6. Fuzzing Stress with Pseudorandom Interleaved Operations
    // =========================================================================

    @Test
    fun fuzzingStress_2000RandomInterleavedOperations_preservesAllInvariants() {
        val random = Random(4242)
        var state = baseProject
        var clipCounter = 0

        for (op in 1..2000) {
            val action = random.nextInt(6)
            when (action) {
                0, 1 -> { // 33% chance to Execute Add
                    clipCounter++
                    val clip = createClip("clip_$clipCounter", startUs = (clipCounter % 50) * 100_000L)
                    val cmd = AddClipCommand(trackId, clip)
                    state = manager.execute(cmd, state)
                }
                2 -> { // 16% chance to Execute Title Update
                    val newTitle = "Title $op"
                    val cmd = SnapshotTimelineCommand("Update Title", state, state.copy(title = newTitle))
                    state = manager.execute(cmd, state)
                }
                3 -> { // 16% chance to Undo
                    if (manager.canUndo) {
                        state = manager.undo(state)!!
                    } else {
                        val nullResult = manager.undo(state)
                        assertThat(nullResult).isNull()
                    }
                }
                4 -> { // 16% chance to Redo
                    if (manager.canRedo) {
                        state = manager.redo(state)!!
                    } else {
                        val nullResult = manager.redo(state)
                        assertThat(nullResult).isNull()
                    }
                }
                5 -> { // 16% chance of random burst
                    if (random.nextBoolean() && manager.canUndo) {
                        state = manager.undo(state)!!
                    } else if (manager.canRedo) {
                        state = manager.redo(state)!!
                    }
                }
            }

            // Invariants checked after EVERY single operation:
            assertThat(manager.undoStackSize).isAtMost(50)
            assertThat(manager.undoStackSize).isAtLeast(0)
            assertThat(manager.redoStackSize).isAtLeast(0)
            assertThat(manager.canUndo).isEqualTo(manager.undoStackSize > 0)
            assertThat(manager.canRedo).isEqualTo(manager.redoStackSize > 0)
            assertThat(state.tracks).isNotEmpty()
            assertThat(state.durationUs).isAtLeast(0L)
        }
    }

    // =========================================================================
    // 7. Rapid Cyclic Oscillation
    // =========================================================================

    @Test
    fun rapidOscillation_1000CyclicUndoRedoOperations_preservesExactBitwiseFidelity() {
        val clip = createClip("cycle_clip", 0L, 5_000_000L)
        val addedState = manager.execute(AddClipCommand(trackId, clip), baseProject)

        var current = addedState
        for (i in 1..1000) {
            current = manager.undo(current)!!
            assertThat(current.tracks.first().clips).isEmpty()
            assertThat(current.durationUs).isEqualTo(0L)

            current = manager.redo(current)!!
            assertThat(current.tracks.first().clips).hasSize(1)
            assertThat(current.durationUs).isEqualTo(5_000_000L)
            assertThat(current.tracks.first().clips.first().id).isEqualTo("cycle_clip")
        }

        // Verify end state is identical
        assertThat(current.tracks.first().clips).isEqualTo(addedState.tracks.first().clips)
        assertThat(current.durationUs).isEqualTo(addedState.durationUs)
    }

    // =========================================================================
    // 8. Custom History Limit Boundaries (1 and 0)
    // =========================================================================

    @Test
    fun edgeCase_customHistoryLimit_one_correctlyEvictsAndPreservesFidelity() {
        val singleManager = UndoRedoManager(maxHistoryLimit = 1)
        val clipA = createClip("clip_A", 0L)
        val clipB = createClip("clip_B", 1_000_000L)

        var state = singleManager.execute(AddClipCommand(trackId, clipA), baseProject)
        assertThat(singleManager.undoStackSize).isEqualTo(1)

        // Adding clipB evicts clipA from history
        state = singleManager.execute(AddClipCommand(trackId, clipB), state)
        assertThat(singleManager.undoStackSize).isEqualTo(1)

        // Undo once reverts clipB
        state = singleManager.undo(state)!!
        assertThat(state.tracks.first().clips).hasSize(1)
        assertThat(state.tracks.first().clips.first().id).isEqualTo("clip_A")
        assertThat(singleManager.undoStackSize).isEqualTo(0)
        assertThat(singleManager.canUndo).isFalse()

        // Second undo fails because clipA was evicted
        assertThat(singleManager.undo(state)).isNull()

        // Redo restores clipB
        state = singleManager.redo(state)!!
        assertThat(state.tracks.first().clips).hasSize(2)
        assertThat(singleManager.redoStackSize).isEqualTo(0)
    }

    @Test
    fun edgeCase_customHistoryLimit_zero_safeNoOpStorage() {
        val zeroManager = UndoRedoManager(maxHistoryLimit = 0)
        val clip = createClip("clip_zero", 0L)

        val state = zeroManager.execute(AddClipCommand(trackId, clip), baseProject)
        // Command was executed, but stack size is instantly capped at 0
        assertThat(state.tracks.first().clips).hasSize(1)
        assertThat(zeroManager.undoStackSize).isEqualTo(0)
        assertThat(zeroManager.canUndo).isFalse()
        assertThat(zeroManager.undo(state)).isNull()
    }
}
