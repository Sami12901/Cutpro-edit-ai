package com.cutpro.core.common

import com.cutpro.core.common.dispatcher.StandardDispatcherProvider
import com.cutpro.core.common.dispatcher.TestDispatcherProvider
import com.cutpro.core.common.error.AppError
import com.cutpro.core.common.result.CutproResult
import com.cutpro.core.common.timecode.TimecodeHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CommonCoreTest {

    @Test
    fun testDispatcherProviders() {
        val standard = StandardDispatcherProvider()
        assertThat(standard.io).isNotNull()
        assertThat(standard.default).isNotNull()

        val testProvider = TestDispatcherProvider()
        assertThat(testProvider.io).isNotNull()
        assertThat(testProvider.main).isEqualTo(testProvider.io)
    }

    @Test
    fun testCutproResultSuccess() {
        val result: CutproResult<String> = CutproResult.Success("hello")
        assertThat(result.isSuccess).isTrue()
        assertThat(result.isError).isFalse()
        assertThat(result.getOrNull()).isEqualTo("hello")
        assertThat(result.getOrThrow()).isEqualTo("hello")

        val mapped = result.map { it.length }
        assertThat(mapped.getOrNull()).isEqualTo(5)

        var captured = ""
        result.onSuccess { captured = it }
        assertThat(captured).isEqualTo("hello")
    }

    @Test
    fun testCutproResultError() {
        val error = AppError.ValidationError("Invalid parameter")
        val result: CutproResult<String> = CutproResult.Error(error)
        assertThat(result.isSuccess).isFalse()
        assertThat(result.isError).isTrue()
        assertThat(result.getOrNull()).isNull()

        var capturedError: AppError? = null
        result.onError { capturedError = it }
        assertThat(capturedError).isEqualTo(error)

        assertThrows(IllegalStateException::class.java) {
            result.getOrThrow()
        }
    }

    @Test
    fun testTimecodeFormattingAndParsing() {
        val timeUs = 3661_500_000L // 1h 1m 1s 15 frames at 30fps
        val smpte = TimecodeHelper.formatSmpte(timeUs, 30)
        assertThat(smpte).isEqualTo("01:01:01:15")

        val srt = TimecodeHelper.formatSrt(timeUs)
        assertThat(srt).isEqualTo("01:01:01,500")

        val vtt = TimecodeHelper.formatVtt(timeUs)
        assertThat(vtt).isEqualTo("01:01:01.500")

        val parsedSrt = TimecodeHelper.parseSrtTimecode("01:01:01,500")
        assertThat(parsedSrt).isEqualTo(timeUs)

        val parsedVtt = TimecodeHelper.parseVttTimecode("01:01:01.500")
        assertThat(parsedVtt).isEqualTo(timeUs)
    }
}
