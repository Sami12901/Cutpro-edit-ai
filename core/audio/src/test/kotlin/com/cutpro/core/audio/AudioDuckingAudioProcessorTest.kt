package com.cutpro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioDuckingAudioProcessorTest {

    @Test
    fun schedule_attenuatesAudioDuringSpeechInterval() {
        val schedule = TimelineDuckingSchedule(
            intervals = listOf(SpeechInterval(1_000_000L, 2_000_000L)),
            attackTimeUs = 150_000L,
            holdTimeUs = 300_000L,
            releaseTimeUs = 400_000L,
            duckingDepthDb = -12.0f
        )
        val processor = AudioDuckingAudioProcessor(duckingSchedule = schedule)
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // Seek to 1,500,000 us (inside speech interval, fully ducked)
        processor.seekTo(1_500_000L)

        val frameCount = 10
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount * 2) {
            inputBuffer.putShort(10000.toShort())
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val out = processor.output

        assertThat(out.remaining()).isEqualTo(frameCount * 4)
        while (out.hasRemaining()) {
            val sample = out.short
            // 10000 * 0.2512 ~ 2512
            assertThat(sample.toInt()).isLessThan(3000)
            assertThat(sample.toInt()).isGreaterThan(2000)
        }
    }
}
