package com.cutpro.core.motiongraphics.schema

class ParameterSchema(
    val definitions: List<ParameterDefinition>
) {
    private val definitionMap = definitions.associateBy { it.key }

    fun validate(params: Map<String, Any>): Map<String, Any> {
        val validated = mutableMapOf<String, Any>()
        for (def in definitions) {
            val userVal = params[def.key]
            validated[def.key] = def.validate(userVal)
        }
        return validated
    }

    fun getDefinition(key: String): ParameterDefinition? = definitionMap[key]

    fun getDefaultParams(): Map<String, Any> {
        return definitions.associate { it.key to it.defaultValue }
    }
}
