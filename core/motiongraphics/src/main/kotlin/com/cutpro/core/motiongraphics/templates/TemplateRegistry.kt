package com.cutpro.core.motiongraphics.templates

import com.cutpro.core.motiongraphics.templates.counters.CurrencyCountSmoothPercentageTemplate
import com.cutpro.core.motiongraphics.templates.counters.NumberRollTallyTemplate
import com.cutpro.core.motiongraphics.templates.counters.PercentageFillMilestoneTemplate
import com.cutpro.core.motiongraphics.templates.cta.FollowBadgeSwipeIndicatorTemplate
import com.cutpro.core.motiongraphics.templates.cta.SocialHandleFollowCardTemplate
import com.cutpro.core.motiongraphics.templates.cta.SubscribeBannerBellTemplate
import com.cutpro.core.motiongraphics.templates.kinetic.DynamicBounceWordSlideTemplate
import com.cutpro.core.motiongraphics.templates.kinetic.StaggeredSlideTypewriterTemplate
import com.cutpro.core.motiongraphics.templates.kinetic.WordPopBounceTemplate
import com.cutpro.core.motiongraphics.templates.logo.GeometricMaskCircularIrisTemplate
import com.cutpro.core.motiongraphics.templates.logo.RadialFlareDoubleSplitTemplate
import com.cutpro.core.motiongraphics.templates.logo.SpinSettleScaleVignetteTemplate
import com.cutpro.core.motiongraphics.templates.lowerthirds.CleanRibbonSocialDuoTemplate
import com.cutpro.core.motiongraphics.templates.lowerthirds.DualToneLineAccentPillTemplate
import com.cutpro.core.motiongraphics.templates.lowerthirds.ModernAccentNewsBarTemplate
import com.cutpro.core.motiongraphics.templates.subtitles.BottomFadeContrastBoxTemplate
import com.cutpro.core.motiongraphics.templates.subtitles.HighlightPillKaraokeTemplate
import com.cutpro.core.motiongraphics.templates.subtitles.StrokeOutlinePopBubbleTemplate
import com.cutpro.core.motiongraphics.templates.titles.BoldImpactGlitchTemplate
import com.cutpro.core.motiongraphics.templates.titles.CinematicSplitFadeTemplate
import com.cutpro.core.motiongraphics.templates.titles.MinimalModernTitleTemplate

object TemplateRegistry {

    private val allTemplatesList = listOf(
        // Category 1: Opening Titles
        MinimalModernTitleTemplate(),
        CinematicSplitFadeTemplate(),
        BoldImpactGlitchTemplate(),

        // Category 2: Kinetic Typography
        WordPopBounceTemplate(),
        StaggeredSlideTypewriterTemplate(),
        DynamicBounceWordSlideTemplate(),

        // Category 3: Lower Thirds
        ModernAccentNewsBarTemplate(),
        CleanRibbonSocialDuoTemplate(),
        DualToneLineAccentPillTemplate(),

        // Category 4: Animated Subtitles
        HighlightPillKaraokeTemplate(),
        StrokeOutlinePopBubbleTemplate(),
        BottomFadeContrastBoxTemplate(),

        // Category 5: Animated Counters
        NumberRollTallyTemplate(),
        PercentageFillMilestoneTemplate(),
        CurrencyCountSmoothPercentageTemplate(),

        // Category 6: Call-to-Actions
        SubscribeBannerBellTemplate(),
        SocialHandleFollowCardTemplate(),
        FollowBadgeSwipeIndicatorTemplate(),

        // Category 7: Logo Reveals
        GeometricMaskCircularIrisTemplate(),
        SpinSettleScaleVignetteTemplate(),
        RadialFlareDoubleSplitTemplate()
    )

    private val templateMap: Map<String, MotionGraphicsTemplate> = buildMap {
        for (tpl in allTemplatesList) {
            put(tpl.id, tpl)
            for (alias in tpl.aliases) {
                put(alias, tpl)
            }
        }
    }

    fun getAllTemplates(): List<MotionGraphicsTemplate> = allTemplatesList

    fun getTemplateById(id: String): MotionGraphicsTemplate? = templateMap[id]

    fun getTemplatesByCategory(category: TemplateCategory): List<MotionGraphicsTemplate> =
        allTemplatesList.filter { it.category == category }

    val totalTemplatesCount: Int get() = allTemplatesList.size
}
