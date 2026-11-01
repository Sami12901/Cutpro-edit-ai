package com.cutpro.core.export.model

sealed interface ExportProgress {
    val exportId: String

    data class Starting(override val exportId: String) : ExportProgress

    data class Processing(
        override val exportId: String,
        val percentage: Int,
        val elapsedMs: Long,
        val estimatedRemainingMs: Long? = null
    ) : ExportProgress

    data class Completed(
        override val exportId: String,
        val result: ExportResult
    ) : ExportProgress

    data class Failed(
        override val exportId: String,
        val cause: Throwable,
        val message: String
    ) : ExportProgress

    data class Cancelled(
        override val exportId: String
    ) : ExportProgress
}
