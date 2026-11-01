package com.cutpro.feature.export.progress

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 250ms periodic polling ticker for Media3 Transformer progress with monotonic guarantees and smoothed ETA.
 * Strictly zero emojis.
 */
@OptIn(UnstableApi::class)
class ExportProgressTicker(
    private val scope: CoroutineScope,
    private val onProgressTick: (progressPercent: Int, elapsedMs: Long, etaMs: Long?) -> Unit
) {
    private var tickerJob: Job? = null
    private var lastMonotonicPercent = 0

    fun startPolling(transformer: Transformer) {
        stopPolling()
        lastMonotonicPercent = 0
        val startTimeMs = System.currentTimeMillis()
        val holder = ProgressHolder()

        tickerJob = scope.launch {
            while (isActive) {
                val state = transformer.getProgress(holder)
                val elapsedMs = System.currentTimeMillis() - startTimeMs

                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    val raw = holder.progress.coerceIn(0, 100)
                    lastMonotonicPercent = maxOf(lastMonotonicPercent, raw)
                    val etaMs = calculateEta(lastMonotonicPercent, elapsedMs)
                    onProgressTick(lastMonotonicPercent, elapsedMs, etaMs)
                } else if (state == Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
                    onProgressTick(0, elapsedMs, null)
                }

                delay(250) // FEAT-EXP-006 250ms periodic ticker
            }
        }
    }

    fun stopPolling() {
        tickerJob?.cancel()
        tickerJob = null
    }

    companion object {
        fun calculateEta(progressPercent: Int, elapsedMs: Long): Long? {
            if (progressPercent <= 1 || elapsedMs < 1000L) return null
            if (progressPercent >= 100) return 0L
            val progressFraction = progressPercent / 100.0
            val totalTimeMs = (elapsedMs / progressFraction).toLong()
            return maxOf(0L, totalTimeMs - elapsedMs)
        }
    }
}
