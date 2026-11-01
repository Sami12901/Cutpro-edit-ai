package com.cutpro.core.export.model

import com.cutpro.core.model.VideoCodec
import java.io.File

data class ExportResult(
    val exportId: String,
    val outputFile: File,
    val destinationUri: String? = null,
    val durationUs: Long,
    val fileSizeBytes: Long,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val codec: VideoCodec,
    val exportDurationMs: Long
)
