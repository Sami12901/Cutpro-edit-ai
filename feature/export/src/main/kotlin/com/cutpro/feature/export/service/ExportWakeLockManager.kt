package com.cutpro.feature.export.service

import android.content.Context
import android.os.PowerManager

/**
 * Manages CPU Partial WakeLock lifecycle during video export.
 * Strictly zero emojis.
 */
class ExportWakeLockManager(context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    val isHeld: Boolean
        get() = wakeLock?.isHeld == true

    fun acquire(tag: String = "CutPro:ExportWakeLock", timeoutMs: Long = 7_200_000L) {
        release()
        try {
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)?.apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        } finally {
            wakeLock = null
        }
    }
}
