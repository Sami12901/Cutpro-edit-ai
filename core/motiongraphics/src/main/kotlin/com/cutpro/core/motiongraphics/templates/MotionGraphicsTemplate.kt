package com.cutpro.core.motiongraphics.templates

import android.graphics.Canvas
import com.cutpro.core.motiongraphics.model.RenderState
import com.cutpro.core.motiongraphics.schema.ParameterSchema

interface MotionGraphicsTemplate {
    val id: String
    val aliases: List<String> get() = emptyList()
    val displayName: String
    val category: TemplateCategory
    val defaultDurationUs: Long
    val schema: ParameterSchema

    fun evaluate(
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    ): RenderState

    fun render(
        canvas: Canvas,
        params: Map<String, Any>,
        timestampUs: Long,
        viewportWidth: Int,
        viewportHeight: Int
    )
}
