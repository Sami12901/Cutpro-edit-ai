package com.cutpro.core.ai.gate

import com.cutpro.core.ai.ApprovalGate
import com.cutpro.core.ai.Timeline
import com.cutpro.core.ai.model.EditPlan
import com.cutpro.core.ai.model.PlanDiff
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.timeline.command.SnapshotTimelineCommand
import com.cutpro.core.timeline.undo.UndoRedoManager
import java.util.concurrent.ConcurrentHashMap

/**
 * StandardApprovalGate
 * Implements ApprovalGate with interactive checklist support, selective diff previews,
 * and compound atomic undo/redo via SnapshotTimelineCommand.
 */
class StandardApprovalGate(
    private val getCurrentTimeline: () -> ProjectManifest,
    private val updateTimeline: (ProjectManifest) -> Unit,
    private val undoRedoManager: UndoRedoManager? = null
) : ApprovalGate {

    private val appliedPlanSnapshots = ConcurrentHashMap<String, ProjectManifest>()

    override fun calculateDiff(plan: EditPlan, currentTimeline: Timeline): PlanDiff {
        return PlanDiffCalculator.calculateDiff(plan, currentTimeline)
    }

    fun calculateSelectiveDiff(
        plan: EditPlan,
        currentTimeline: Timeline,
        approvedItemIds: Set<String>
    ): PlanDiff {
        return PlanDiffCalculator.calculateDiff(plan, currentTimeline, approvedItemIds)
    }

    override suspend fun applyPlan(plan: EditPlan, approvedItemIds: Set<String>): Result<Unit> = runCatching {
        val current = getCurrentTimeline()
        val approvedActions = plan.actions.filter { it.id in approvedItemIds }
        if (approvedActions.isEmpty()) {
            return@runCatching
        }

        appliedPlanSnapshots[plan.id] = current

        val nextTimeline = PlanExecutionEngine.executeActions(current, approvedActions)

        val compoundCommand = SnapshotTimelineCommand(
            description = "Apply Edit Plan: ${plan.title} (${approvedActions.size} actions)",
            beforeState = current,
            afterState = nextTimeline
        )

        undoRedoManager?.execute(compoundCommand, current)
        updateTimeline(nextTimeline)
    }

    override suspend fun rollbackPlan(planId: String): Result<Unit> = runCatching {
        val prePlanSnapshot = appliedPlanSnapshots[planId]
            ?: throw NoSuchElementException("No rollback snapshot found for plan ID: $planId")

        val current = getCurrentTimeline()
        val rollbackCommand = SnapshotTimelineCommand(
            description = "Rollback Edit Plan: $planId",
            beforeState = current,
            afterState = prePlanSnapshot
        )

        undoRedoManager?.execute(rollbackCommand, current)
        updateTimeline(prePlanSnapshot)
        appliedPlanSnapshots.remove(planId)
    }

    fun isPlanApplied(planId: String): Boolean = appliedPlanSnapshots.containsKey(planId)
}
