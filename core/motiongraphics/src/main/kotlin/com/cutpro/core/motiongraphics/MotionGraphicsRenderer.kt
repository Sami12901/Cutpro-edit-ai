package com.cutpro.core.motiongraphics

import android.graphics.Canvas
import com.cutpro.core.motiongraphics.model.TemplateInstance

interface MotionGraphicsRenderer {
    fun renderAt(
        canvas: Canvas,
        viewportWidth: Int,
        viewportHeight: Int,
        presentationTimestampUs: Long,
        templateInstance: TemplateInstance
    )
}
