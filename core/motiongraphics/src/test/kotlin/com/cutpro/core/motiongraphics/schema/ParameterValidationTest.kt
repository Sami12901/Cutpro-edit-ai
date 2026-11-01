package com.cutpro.core.motiongraphics.schema

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ParameterValidationTest {

    @Test
    fun zeroEmojiValidator_rejectsEmojiCharacters() {
        // Test standard emojis
        val emojis = listOf(
            "\uD83D\uDE00", // Grinning face
            "\uD83D\uDE02", // Tears of joy
            "\uD83D\uDC4D", // Thumbs up
            "\uD83C\uDFAC", // Clapper board
            "\u2764\uFE0F", // Heart
            "\u26A1",       // High voltage
            "CutPro \uD83D\uDE80 Rocket"
        )

        for (emojiStr in emojis) {
            val contains = ZeroEmojiValidator.containsEmoji(emojiStr)
            assertThat(contains).isTrue()

            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(emojiStr, "test_field")
            }
        }
    }

    @Test
    fun zeroEmojiValidator_allowsValidText() {
        val validTexts = listOf(
            "CUTPRO EDIT AI",
            "Broadcast News 2026",
            "Special characters: !@#$%^&*()_+-=[]{}|;':,.<>/?",
            "Numbers: 0123456789",
            "Multi-language: \u09ac\u09be\u0982\u09b2\u09be \u0627\u0644\u0639\u0631\u0628\u064a\u0629 \u4e2d\u6587 \u0420\u0443\u0441\u0441\u043a\u0438\u0439"
        )

        for (text in validTexts) {
            assertThat(ZeroEmojiValidator.containsEmoji(text)).isFalse()
            ZeroEmojiValidator.validateNoEmoji(text, "valid_field")
        }
    }

    @Test
    fun schema_validatesNumericRanges() {
        val def = ParameterDefinition(
            key = "opacity",
            displayName = "Opacity",
            type = ParameterType.FLOAT,
            defaultValue = 1.0f,
            minValue = 0.0f,
            maxValue = 1.0f
        )

        assertThat(def.validate(0.5f)).isEqualTo(0.5f)

        assertThrows(IllegalArgumentException::class.java) {
            def.validate(-0.1f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            def.validate(1.1f)
        }
    }

    @Test
    fun schema_disallowEmptyStrings() {
        val def = ParameterDefinition(
            key = "title",
            displayName = "Title",
            type = ParameterType.STRING,
            defaultValue = "Default",
            disallowEmpty = true
        )

        assertThrows(IllegalArgumentException::class.java) {
            def.validate("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            def.validate("   ")
        }
    }

    @Test
    fun schema_validatesEnumValues() {
        val def = ParameterDefinition(
            key = "direction",
            displayName = "Direction",
            type = ParameterType.ENUM,
            defaultValue = "UP",
            allowedValues = listOf("UP", "DOWN", "LEFT", "RIGHT")
        )

        assertThat(def.validate("DOWN")).isEqualTo("DOWN")

        assertThrows(IllegalArgumentException::class.java) {
            def.validate("DIAGONAL")
        }
    }

    @Test
    fun schema_rejectsNonFiniteFloats() {
        val def = ParameterDefinition(
            key = "intensity",
            displayName = "Intensity",
            type = ParameterType.FLOAT,
            defaultValue = 0.5f,
            minValue = 0.0f,
            maxValue = 1.0f
        )

        assertThrows(IllegalArgumentException::class.java) { def.validate(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { def.validate(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { def.validate(Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { def.validate(Float.NEGATIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { def.validate("NaN") }
        assertThrows(IllegalArgumentException::class.java) { def.validate("Infinity") }
        assertThrows(IllegalArgumentException::class.java) { def.validate("-Infinity") }
    }

    @Test
    fun schema_rejectsNonFiniteIntegersAndColors() {
        val intDef = ParameterDefinition(
            key = "count",
            displayName = "Count",
            type = ParameterType.INT,
            defaultValue = 5,
            minValue = 0,
            maxValue = 10
        )
        assertThrows(IllegalArgumentException::class.java) { intDef.validate(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { intDef.validate(Float.POSITIVE_INFINITY) }

        val colorDef = ParameterDefinition(
            key = "color",
            displayName = "Color",
            type = ParameterType.COLOR,
            defaultValue = 0xFFFFFFFFL
        )
        assertThrows(IllegalArgumentException::class.java) { colorDef.validate(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { colorDef.validate(Float.POSITIVE_INFINITY) }
    }

    @Test
    fun schema_constructorFailsFastOnInvalidDefinition() {
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("bad", "Bad", ParameterType.FLOAT, Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("inverted", "Inverted", ParameterType.FLOAT, 1.0f, minValue = 5.0f, maxValue = 2.0f)
        }
    }
}
