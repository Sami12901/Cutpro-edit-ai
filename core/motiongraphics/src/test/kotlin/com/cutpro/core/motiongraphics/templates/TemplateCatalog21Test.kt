package com.cutpro.core.motiongraphics.templates

import android.graphics.Bitmap
import android.graphics.Canvas
import com.cutpro.core.motiongraphics.MotionGraphicsRenderer
import com.cutpro.core.motiongraphics.model.TemplateInstance
import com.cutpro.core.motiongraphics.renderer.MotionGraphicsCanvasRenderer
import com.cutpro.core.motiongraphics.schema.ZeroEmojiValidator
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TemplateCatalog21Test {

    @Test
    fun registry_containsExactly21Templates() {
        val all = TemplateRegistry.getAllTemplates()
        assertThat(all).hasSize(21)
        assertThat(TemplateRegistry.totalTemplatesCount).isEqualTo(21)
    }

    @Test
    fun registry_eachCategoryHasExactly3Templates() {
        for (category in TemplateCategory.values()) {
            val list = TemplateRegistry.getTemplatesByCategory(category)
            assertThat(list).hasSize(3)
        }
    }

    @Test
    fun templates_zeroEmojisInNamesAndDefaults() {
        for (tpl in TemplateRegistry.getAllTemplates()) {
            assertThat(ZeroEmojiValidator.containsEmoji(tpl.displayName)).isFalse()
            assertThat(ZeroEmojiValidator.containsEmoji(tpl.id)).isFalse()

            val defaultParams = tpl.schema.getDefaultParams()
            for ((key, value) in defaultParams) {
                if (value is String) {
                    assertThat(ZeroEmojiValidator.containsEmoji(value)).isFalse()
                }
            }
        }
    }

    @Test
    fun templates_aliasLookupResolvesAllTemplates() {
        for (tpl in TemplateRegistry.getAllTemplates()) {
            val byId = TemplateRegistry.getTemplateById(tpl.id)
            assertThat(byId).isNotNull()
            assertThat(byId?.id).isEqualTo(tpl.id)

            for (alias in tpl.aliases) {
                val byAlias = TemplateRegistry.getTemplateById(alias)
                assertThat(byAlias).isNotNull()
                assertThat(byAlias?.id).isEqualTo(tpl.id)
            }
        }
    }

    @Test
    fun templates_evaluateAndRenderBoundaryTimestamps() {
        val renderer: MotionGraphicsRenderer = MotionGraphicsCanvasRenderer()
        val width = 1920
        val height = 1080
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        for (tpl in TemplateRegistry.getAllTemplates()) {
            val duration = tpl.defaultDurationUs
            val mid = duration / 2

            val timestamps = listOf(0L, mid, duration)
            val defaultParams = tpl.schema.getDefaultParams()

            for (t in timestamps) {
                val state = tpl.evaluate(defaultParams, t, width, height)
                assertThat(state).isNotNull()
                assertThat(state.timestampUs).isEqualTo(t)

                val instance = TemplateInstance(
                    templateId = tpl.id,
                    durationUs = duration,
                    parameters = defaultParams
                )
                // Must render without crashing
                renderer.renderAt(canvas, width, height, t, instance)
            }
        }
    }
}
