package com.cutpro.core.export.overlay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cutpro.core.motiongraphics.model.AnimationLoopMode
import com.cutpro.core.motiongraphics.model.TemplateInstance
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MotionGraphicsCanvasOverlayTest {

    @Test
    fun timelineItem_boundsAndLocalTime_onceMode() {
        val instance = TemplateInstance(
            templateId = "minimal_modern_title",
            durationUs = 3_000_000L
        )
        val item = MotionGraphicsTimelineItem(
            templateInstance = instance,
            timelineStartUs = 2_000_000L,
            timelineDurationUs = 3_000_000L,
            loopMode = AnimationLoopMode.ONCE
        )

        assertThat(item.isActiveAt(1_999_999L)).isFalse()
        assertThat(item.isActiveAt(2_000_000L)).isTrue()
        assertThat(item.isActiveAt(3_500_000L)).isTrue()
        assertThat(item.isActiveAt(5_000_000L)).isFalse()

        assertThat(item.calculateLocalTimeUs(2_000_000L)).isEqualTo(0L)
        assertThat(item.calculateLocalTimeUs(3_000_000L)).isEqualTo(1_000_000L)
        assertThat(item.calculateLocalTimeUs(6_000_000L)).isEqualTo(3_000_000L) // clamped
    }

    @Test
    fun timelineItem_boundsAndLocalTime_loopMode() {
        val instance = TemplateInstance(
            templateId = "minimal_modern_title",
            durationUs = 2_000_000L
        )
        val item = MotionGraphicsTimelineItem(
            templateInstance = instance,
            timelineStartUs = 1_000_000L,
            timelineDurationUs = 6_000_000L,
            loopMode = AnimationLoopMode.LOOP
        )

        assertThat(item.calculateLocalTimeUs(1_000_000L)).isEqualTo(0L)
        assertThat(item.calculateLocalTimeUs(2_500_000L)).isEqualTo(1_500_000L)
        assertThat(item.calculateLocalTimeUs(3_000_000L)).isEqualTo(0L)
        assertThat(item.calculateLocalTimeUs(4_500_000L)).isEqualTo(1_500_000L)
    }

    @Test
    fun timelineItem_boundsAndLocalTime_pingPongMode() {
        val instance = TemplateInstance(
            templateId = "minimal_modern_title",
            durationUs = 1_000_000L
        )
        val item = MotionGraphicsTimelineItem(
            templateInstance = instance,
            timelineStartUs = 0L,
            timelineDurationUs = 3_000_000L,
            loopMode = AnimationLoopMode.PING_PONG
        )

        assertThat(item.calculateLocalTimeUs(0L)).isEqualTo(0L)
        assertThat(item.calculateLocalTimeUs(500_000L)).isEqualTo(500_000L)
        assertThat(item.calculateLocalTimeUs(1_500_000L)).isEqualTo(500_000L) // bouncing back
    }

    @Test
    fun canvasOverlay_generatesAndRecyclesBitmap() {
        val instance = TemplateInstance(
            templateId = "minimal_modern_title",
            durationUs = 2_000_000L,
            parameters = mapOf("title_text" to "Title", "subtitle_text" to "Subtitle")
        )
        val item = MotionGraphicsTimelineItem(
            templateInstance = instance,
            timelineStartUs = 0L,
            timelineDurationUs = 2_000_000L
        )
        val overlay = MotionGraphicsCanvasOverlay(
            items = listOf(item),
            width = 1280,
            height = 720
        )

        val bmp1 = overlay.getBitmap(500_000L)
        assertThat(bmp1).isNotNull()
        assertThat(bmp1.width).isEqualTo(1280)
        assertThat(bmp1.height).isEqualTo(720)

        // Same bitmap recycled
        val bmp2 = overlay.getBitmap(1_000_000L)
        assertThat(bmp2).isSameInstanceAs(bmp1)

        overlay.release()
        overlay.close() // Idempotent
    }
}
