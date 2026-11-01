package com.cutpro.core.ai.stt.model

import kotlinx.serialization.Serializable

@Serializable
enum class SupportedLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val supportedLocales: Set<String>
) {
    ENGLISH(
        code = "en",
        displayName = "English",
        nativeName = "English",
        supportedLocales = setOf("en", "en_US", "en_GB", "en_AU", "en_CA", "en-US", "en-GB")
    ),
    BENGALI(
        code = "bn",
        displayName = "Bengali",
        nativeName = "বাংলা",
        supportedLocales = setOf("bn", "bn_BD", "bn_IN", "bn-BD", "bn-IN")
    );

    companion object {
        fun fromCode(code: String): SupportedLanguage? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        fun fromLocale(locale: String): SupportedLanguage? {
            val normalized = locale.trim().replace("-", "_")
            return entries.firstOrNull { lang ->
                lang.supportedLocales.any { it.equals(normalized, ignoreCase = true) } ||
                    normalized.startsWith(lang.code, ignoreCase = true)
            }
        }
    }
}
