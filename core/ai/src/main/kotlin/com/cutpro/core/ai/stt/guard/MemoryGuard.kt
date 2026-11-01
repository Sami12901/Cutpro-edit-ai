package com.cutpro.core.ai.stt.guard

import android.app.ActivityManager
import android.content.Context

interface MemoryGuard {
    fun getAvailableMemoryBytes(): Long
    fun getTotalMemoryBytes(): Long
    fun isLowMemory(): Boolean
    fun checkMemorySufficient(requiredBytes: Long = MIN_REQUIRED_RAM_BYTES): MemoryCheckResult

    companion object {
        const val MIN_REQUIRED_RAM_BYTES: Long = 500L * 1024L * 1024L // 524,288,000 bytes (500 MB)
    }
}

sealed interface MemoryCheckResult {
    data object Sufficient : MemoryCheckResult
    data class Insufficient(
        val availableBytes: Long,
        val requiredBytes: Long
    ) : MemoryCheckResult
}

class AndroidMemoryGuard(private val context: Context) : MemoryGuard {
    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    private fun getMemoryInfo(): ActivityManager.MemoryInfo {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo
    }

    override fun getAvailableMemoryBytes(): Long = getMemoryInfo().availMem
    override fun getTotalMemoryBytes(): Long = getMemoryInfo().totalMem
    override fun isLowMemory(): Boolean = getMemoryInfo().lowMemory

    override fun checkMemorySufficient(requiredBytes: Long): MemoryCheckResult {
        val info = getMemoryInfo()
        return if (!info.lowMemory && info.availMem >= requiredBytes) {
            MemoryCheckResult.Sufficient
        } else {
            MemoryCheckResult.Insufficient(info.availMem, requiredBytes)
        }
    }
}

class MockMemoryGuard(
    var availableBytes: Long = 1024L * 1024L * 1024L, // 1 GB default
    var totalBytes: Long = 4L * 1024L * 1024L * 1024L,
    var isLowMemoryState: Boolean = false
) : MemoryGuard {
    override fun getAvailableMemoryBytes(): Long = availableBytes
    override fun getTotalMemoryBytes(): Long = totalBytes
    override fun isLowMemory(): Boolean = isLowMemoryState

    override fun checkMemorySufficient(requiredBytes: Long): MemoryCheckResult {
        return if (!isLowMemoryState && availableBytes >= requiredBytes) {
            MemoryCheckResult.Sufficient
        } else {
            MemoryCheckResult.Insufficient(availableBytes, requiredBytes)
        }
    }
}
