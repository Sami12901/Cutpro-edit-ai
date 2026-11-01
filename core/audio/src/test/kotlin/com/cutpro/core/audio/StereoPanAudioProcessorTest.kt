package com.cutpro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StereoPanAudioProcessorTest {

    @Test
    fun monoInput_expandsToStereo() {
        val panProcessor = StereoPanAudioProcessor(initialPan = 0.0f)
        val monoFormat = AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_16BIT)
        val outFormat = panProcessor.configure(monoFormat)

        assertThat(outFormat.channelCount).isEqualTo(2) // Always stereo

        panProcessor.flush()
        val inputBuffer = ByteBuffer.allocateDirect(20).order(ByteOrder.nativeOrder())
        for (i in 0 until 10) {
            inputBuffer.putShort(10000.toShort())
        }
        inputBuffer.flip()

        panProcessor.queueInput(inputBuffer)
        val out = panProcessor.output

        // 10 mono samples expand to 20 stereo samples (40 bytes)
        assertThat(out.remaining()).isEqualTo(40)
    }

    @Test
    fun hardLeftPan_mutesRightChannel() {
        val panProcessor = StereoPanAudioProcessor(initialPan = -1.0f)
        val stereoFormat = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        panProcessor.configure(stereoFormat)
        panProcessor.flush()

        val inputBuffer = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder())
        for (i in 0 until 4) {
            inputBuffer.putShort(10000.toShort()) // L
            inputBuffer.putShort(10000.toShort()) // R
        }
        inputBuffer.flip()

        panProcessor.queueInput(inputBuffer)
        val out = panProcessor.output

        while (out.remaining() >= 4) {
            val left = out.short
            val right = out.short
            assertThat(left).isGreaterThan(10000.toShort())
            assertThat(right).isEqualTo(0.toShort())
        }
    }
}
