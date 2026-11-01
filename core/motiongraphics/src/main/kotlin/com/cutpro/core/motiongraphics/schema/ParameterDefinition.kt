package com.cutpro.core.motiongraphics.schema

data class ParameterDefinition(
    val key: String,
    val displayName: String,
    val type: ParameterType,
    val defaultValue: Any,
    val minValue: Number? = null,
    val maxValue: Number? = null,
    val minLength: Int = 0,
    val maxLength: Int = Int.MAX_VALUE,
    val allowedValues: List<String> = emptyList(),
    val disallowEmpty: Boolean = false,
    val disallowEmojis: Boolean = true,
    val description: String = ""
) {
    init {
        if (minValue != null) {
            val minF = minValue.toFloat()
            require(minF.isFinite()) { "Parameter '$key' minValue must be a finite number: $minValue" }
        }
        if (maxValue != null) {
            val maxF = maxValue.toFloat()
            require(maxF.isFinite()) { "Parameter '$key' maxValue must be a finite number: $maxValue" }
        }
        if (minValue != null && maxValue != null) {
            require(minValue.toFloat() <= maxValue.toFloat()) {
                "Parameter '$key' minValue ($minValue) cannot be greater than maxValue ($maxValue)"
            }
        }
        require(minLength >= 0) { "Parameter '$key' minLength cannot be negative: $minLength" }
        require(maxLength >= minLength) { "Parameter '$key' maxLength ($maxLength) cannot be less than minLength ($minLength)" }
        validate(defaultValue)
    }

    fun validate(value: Any?): Any {
        val targetValue = value ?: defaultValue

        when (type) {
            ParameterType.STRING -> {
                val str = targetValue.toString()
                if (disallowEmpty && str.trim().isEmpty()) {
                    throw IllegalArgumentException("Parameter '$key' cannot be empty or whitespace")
                }
                if (str.length < minLength) {
                    throw IllegalArgumentException("Parameter '$key' length (${str.length}) is less than minimum $minLength")
                }
                if (str.length > maxLength) {
                    throw IllegalArgumentException("Parameter '$key' length (${str.length}) exceeds maximum $maxLength")
                }
                if (disallowEmojis) {
                    ZeroEmojiValidator.validateNoEmoji(str, key)
                }
                return str
            }
            ParameterType.COLOR -> {
                when (targetValue) {
                    is Number -> {
                        val d = targetValue.toDouble()
                        if (!d.isFinite()) {
                            throw IllegalArgumentException("Parameter '$key' color must be a finite number")
                        }
                        return targetValue.toLong()
                    }
                    is String -> {
                        val clean = targetValue.trim().removePrefix("#")
                        return try {
                            clean.toLong(16)
                        } catch (e: NumberFormatException) {
                            throw IllegalArgumentException("Parameter '$key' must be a valid hex color string: '$targetValue'")
                        }
                    }
                    else -> throw IllegalArgumentException("Parameter '$key' must be a valid color representation")
                }
            }
            ParameterType.FLOAT -> {
                val num = when (targetValue) {
                    is Number -> targetValue.toFloat()
                    is String -> targetValue.trim().toFloatOrNull()
                        ?: throw IllegalArgumentException("Parameter '$key' must be a float number: '$targetValue'")
                    else -> throw IllegalArgumentException("Parameter '$key' must be a float number")
                }
                if (!num.isFinite()) {
                    throw IllegalArgumentException("Parameter '$key' must be a finite float number")
                }
                if (minValue != null && num < minValue.toFloat()) {
                    throw IllegalArgumentException("Parameter '$key' ($num) is below minimum $minValue")
                }
                if (maxValue != null && num > maxValue.toFloat()) {
                    throw IllegalArgumentException("Parameter '$key' ($num) exceeds maximum $maxValue")
                }
                return num
            }
            ParameterType.INT -> {
                val num = when (targetValue) {
                    is Number -> {
                        val d = targetValue.toDouble()
                        if (!d.isFinite()) {
                            throw IllegalArgumentException("Parameter '$key' must be a finite integer number")
                        }
                        if (d < Int.MIN_VALUE || d > Int.MAX_VALUE) {
                            throw IllegalArgumentException("Parameter '$key' ($d) exceeds integer range")
                        }
                        targetValue.toInt()
                    }
                    is String -> {
                        val trimmed = targetValue.trim()
                        trimmed.toIntOrNull()
                            ?: trimmed.toDoubleOrNull()?.let { d ->
                                if (!d.isFinite()) {
                                    throw IllegalArgumentException("Parameter '$key' must be a finite integer number")
                                }
                                if (d < Int.MIN_VALUE || d > Int.MAX_VALUE) {
                                    throw IllegalArgumentException("Parameter '$key' ($d) exceeds integer range")
                                }
                                d.toInt()
                            }
                            ?: throw IllegalArgumentException("Parameter '$key' must be an integer number: '$targetValue'")
                    }
                    else -> throw IllegalArgumentException("Parameter '$key' must be an integer number")
                }
                if (minValue != null && num < minValue.toInt()) {
                    throw IllegalArgumentException("Parameter '$key' ($num) is below minimum $minValue")
                }
                if (maxValue != null && num > maxValue.toInt()) {
                    throw IllegalArgumentException("Parameter '$key' ($num) exceeds maximum $maxValue")
                }
                return num
            }
            ParameterType.BOOLEAN -> {
                return when (targetValue) {
                    is Boolean -> targetValue
                    is String -> targetValue.trim().lowercase().toBooleanStrictOrNull()
                        ?: throw IllegalArgumentException("Parameter '$key' must be a boolean: '$targetValue'")
                    else -> throw IllegalArgumentException("Parameter '$key' must be a boolean")
                }
            }
            ParameterType.ENUM -> {
                val str = targetValue.toString()
                if (allowedValues.isNotEmpty() && str !in allowedValues) {
                    throw IllegalArgumentException("Parameter '$key' value '$str' is not one of allowed values: $allowedValues")
                }
                if (disallowEmojis) {
                    ZeroEmojiValidator.validateNoEmoji(str, key)
                }
                return str
            }
        }
    }
}
