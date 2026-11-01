package com.cutpro.core.export

import kotlinx.coroutines.flow.Flow
import com.cutpro.core.export.model.ExportPlan
import com.cutpro.core.export.model.ExportProgress
import com.cutpro.core.export.model.ExportResult

interface VideoExportEngine {
    fun validateExportPlan(plan: ExportPlan): Result<Unit>
    fun exportVideo(plan: ExportPlan): Flow<ExportProgress>
    fun cancelExport(exportId: String)
}

typealias ExportPlan = com.cutpro.core.export.model.ExportPlan
typealias ExportProgress = com.cutpro.core.export.model.ExportProgress
typealias ExportResult = com.cutpro.core.export.model.ExportResult
