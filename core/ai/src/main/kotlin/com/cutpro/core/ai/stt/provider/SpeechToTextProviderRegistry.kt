package com.cutpro.core.ai.stt.provider

import com.cutpro.core.ai.stt.SpeechToTextProvider
import com.cutpro.core.ai.stt.model.SupportedLanguage
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry for managing pluggable SpeechToTextProvider implementations.
 */
class SpeechToTextProviderRegistry {
    private val providers = ConcurrentHashMap<String, SpeechToTextProvider>()

    fun register(provider: SpeechToTextProvider) {
        providers[provider.id] = provider
    }

    fun unregister(providerId: String) {
        providers.remove(providerId)
    }

    fun get(providerId: String): SpeechToTextProvider? = providers[providerId]

    fun getAll(): List<SpeechToTextProvider> = providers.values.toList()

    fun getAvailableForLanguage(language: SupportedLanguage): List<SpeechToTextProvider> {
        return providers.values.filter { language in it.supportedLanguages }
    }
}
