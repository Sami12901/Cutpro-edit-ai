package com.cutpro.core.motiongraphics.renderer

import android.graphics.Canvas
import com.cutpro.core.motiongraphics.MotionGraphicsRenderer
import com.cutpro.core.motiongraphics.model.TemplateInstance
import com.cutpro.core.motiongraphics.templates.TemplateRegistry

class MotionGraphicsCanvasRenderer : MotionGraphicsRenderer {

    override fun renderAt(
        canvas: Canvas,
        viewportWidth: Int,
        viewportHeight: Int,
        presentationTimestampUs: Long,
        templateInstance: TemplateInstance
    ) {
        if (viewportWidth <= 0 || viewportHeight <= 0) return
        val template = TemplateRegistry.getTemplateById(templateInstance.templateId) ?: return

        template.render(
            canvas = canvas,
            params = templateInstance.parameters,
            timestampUs = presentationTimestampUs,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight
        )
    }
}
