package com.cutpro.core.motiongraphics.model

import java.util.UUID

data class TemplateInstance(
    val id: String = UUID.randomUUID().toString(),
    val templateId: String,
    val durationUs: Long,
    val parameters: Map<String, Any> = emptyMap(),
    val customKeyframes: Map<String, PropertyTimeline<*>> = emptyMap()
)
