package com.cutpro.feature.export.model

import android.net.Uri
import java.io.File

enum class ExportStatus {
    IDLE,
    PREPARING,
    RENDERING,
    FINALIZING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class ExportProgressState(
    val status: ExportStatus = ExportStatus.IDLE,
    val progressPercentage: Int = 0,
    val elapsedTimeMs: Long = 0L,
    val estimatedRemainingMs: Long? = null,
    val outputFile: File? = null,
    val outputUri: Uri? = null,
    val errorMessage: String? = null
)
