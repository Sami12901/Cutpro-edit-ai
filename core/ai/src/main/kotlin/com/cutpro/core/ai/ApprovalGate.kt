package com.cutpro.core.ai

import com.cutpro.core.ai.model.EditPlan
import com.cutpro.core.ai.model.PlanDiff

/**
 * Interface Contract 5: ApprovalGate
 * Provides selective checklist diff calculation, execution, and rollback.
 */
interface ApprovalGate {
    fun calculateDiff(plan: EditPlan, currentTimeline: Timeline): PlanDiff
    suspend fun applyPlan(plan: EditPlan, approvedItemIds: Set<String>): Result<Unit>
    suspend fun rollbackPlan(planId: String): Result<Unit>
}
