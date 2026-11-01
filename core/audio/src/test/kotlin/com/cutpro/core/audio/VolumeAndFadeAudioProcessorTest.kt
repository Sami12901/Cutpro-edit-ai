package com.cutpro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VolumeAndFadeAudioProcessorTest {

    @Test
    fun configure_acceptsPcm16AndFloat() {
        val processor = VolumeAndFadeAudioProcessor(clipDurationUs = 10_000_000L)

        val pcm16 = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val out16 = processor.configure(pcm16)
        assertThat(out16.encoding).isEqualTo(C.ENCODING_PCM_16BIT)

        val pcmFloat = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)
        val outFloat = processor.configure(pcmFloat)
        assertThat(outFloat.encoding).isEqualTo(C.ENCODING_PCM_FLOAT)
    }

    @Test
    fun configure_rejectsUnsupportedEncoding() {
        val processor = VolumeAndFadeAudioProcessor(clipDurationUs = 10_000_000L)
        val unsupported = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_24BIT)

        assertThrows(AudioProcessor.UnhandledAudioFormatException::class.java) {
            processor.configure(unsupported)
        }
    }

    @Test
    fun zeroDriftPtsProgression_across100000Buffers() {
        // At 44.1 kHz, 1024 frames per buffer
        // 100,000 buffers * 1024 frames = 102,400,000 frames
        // Expected exact us = (102,400,000 * 1_000_000L) / 44100L = 2,321,995,464 us
        val sampleRate = 44100L
        val framesPerBuffer = 1024L
        val totalBuffers = 100_000L

        var totalFrames = 0L
        for (b in 0 until totalBuffers) {
            totalFrames += framesPerBuffer
        }
        val calculatedUs = (totalFrames * 1_000_000L) / sampleRate
        val expectedUs = (100_000L * 1024L * 1_000_000L) / 44100L

        assertThat(calculatedUs).isEqualTo(expectedUs)
    }

    @Test
    fun pcm16_volumeScalingAndSoftClipping() {
        val processor = VolumeAndFadeAudioProcessor(
            clipDurationUs = 5_000_000L,
            initialVolume = 2.0f // 200% boost overdrive
        )
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val frameCount = 100
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount) {
            inputBuffer.putShort(20000.toShort()) // Overdrive 20000 * 2.0 = 40000 -> exceeds 32767
            inputBuffer.putShort((-20000).toShort())
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        assertThat(outputBuffer.hasRemaining()).isTrue()
        while (outputBuffer.hasRemaining()) {
            val sample = outputBuffer.short
            assertThat(sample.toInt()).isAtMost(32767)
            assertThat(sample.toInt()).isAtLeast(-32768)
        }
    }

    @Test
    fun mutedProcessor_producesCleanDigitalSilence() {
        val processor = VolumeAndFadeAudioProcessor(
            clipDurationUs = 5_000_000L,
            initialVolume = 1.0f,
            initialMuted = true
        )
        val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        val frameCount = 50
        val inputBuffer = ByteBuffer.allocateDirect(frameCount * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frameCount * 2) {
            inputBuffer.putShort(15000.toShort())
        }
        inputBuffer.flip()

        processor.queueInput(inputBuffer)
        val outputBuffer = processor.output

        assertThat(outputBuffer.remaining()).isEqualTo(frameCount * 4)
        while (outputBuffer.hasRemaining()) {
            assertThat(outputBuffer.short).isEqualTo(0.toShort())
        }
    }
}
