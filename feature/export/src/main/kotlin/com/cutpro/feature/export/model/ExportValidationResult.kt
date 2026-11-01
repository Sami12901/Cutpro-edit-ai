package com.cutpro.feature.export.model

sealed interface ExportValidationResult {
    object Valid : ExportValidationResult

    data class InsufficientStorage(
        val requiredBytes: Long,
        val availableBytes: Long,
        val message: String
    ) : ExportValidationResult

    data class MissingSourceMedia(
        val clipId: String,
        val uriString: String
    ) : ExportValidationResult

    data class InvalidClipBoundaries(
        val clipId: String
    ) : ExportValidationResult

    data class EmptyTimeline(
        val message: String
    ) : ExportValidationResult
}
