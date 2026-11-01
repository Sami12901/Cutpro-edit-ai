package com.cutpro.feature.dashboard.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cutpro.core.designsystem.icon.CutProIcons
import com.cutpro.feature.dashboard.TemplateItemUiModel

/**
 * Built-in 21 offline templates across 7 categories.
 * Strictly adheres to Zero Emojis across all names and descriptions.
 */
val BuiltInTemplates = listOf(
    // Opening Titles
    TemplateItemUiModel("tpl_title_1", "Opening Titles", "Minimal Modern", "Clean typography fade with smooth tracking"),
    TemplateItemUiModel("tpl_title_2", "Opening Titles", "Cinematic Split Fade", "Dramatic horizontal split reveal with letterbox framing"),
    TemplateItemUiModel("tpl_title_3", "Opening Titles", "Bold Glitch", "High energy RGB split accent with chromatic impact"),

    // Kinetic Typography
    TemplateItemUiModel("tpl_kinetic_1", "Kinetic Typography", "Pop Bounce", "Energetic spring damped scale entrance"),
    TemplateItemUiModel("tpl_kinetic_2", "Kinetic Typography", "Typewriter Terminal", "Monospace terminal typing cursor simulation"),
    TemplateItemUiModel("tpl_kinetic_3", "Kinetic Typography", "Word-by-Word Slide", "Dynamic word slide from below with easing"),

    // Lower Thirds
    TemplateItemUiModel("tpl_lower_1", "Lower Thirds", "News Broadcast Bar", "Clean lower third bar for names and speaker titles"),
    TemplateItemUiModel("tpl_lower_2", "Lower Thirds", "Social Creator Duo", "Dual-line badge with social handle alignment"),
    TemplateItemUiModel("tpl_lower_3", "Lower Thirds", "Gradient Accent Pill", "Modern pill container with subtle color ramp"),

    // Animated Subtitles
    TemplateItemUiModel("tpl_sub_1", "Animated Subtitles", "Karaoke Highlight", "Word by word active spoken highlight synchronization"),
    TemplateItemUiModel("tpl_sub_2", "Animated Subtitles", "Pop Bubble Caption", "Rounded speech bubble pop with dynamic expansion"),
    TemplateItemUiModel("tpl_sub_3", "Animated Subtitles", "High-Contrast Box", "Opaque solid background box for maximum legibility"),

    // Animated Counters
    TemplateItemUiModel("tpl_counter_1", "Animated Counters", "Numeric Tally Metric", "Fast rolling number counter with analytical ease"),
    TemplateItemUiModel("tpl_counter_2", "Animated Counters", "Milestone Badge", "Circular milestone badge reveal with progress stroke"),
    TemplateItemUiModel("tpl_counter_3", "Animated Counters", "Smooth Percentage Gauge", "Percentage indicator with animated bar fill"),

    // Call-to-Actions
    TemplateItemUiModel("tpl_cta_1", "Call-to-Actions", "Subscribe Bell", "Subtle bell chime vibration motion graphic"),
    TemplateItemUiModel("tpl_cta_2", "Call-to-Actions", "Clean Follow Handle", "Compact follow button slide with pulse checkmark"),
    TemplateItemUiModel("tpl_cta_3", "Call-to-Actions", "Swipe/Tap Indicator", "Directional swipe gesture icon overlay"),

    // Logo Reveals
    TemplateItemUiModel("tpl_logo_1", "Logo Reveals", "Circular Iris Mask", "Geometric iris circle expansion revealing logo"),
    TemplateItemUiModel("tpl_logo_2", "Logo Reveals", "Scale & Vignette", "Subtle zoom through dark vignette border"),
    TemplateItemUiModel("tpl_logo_3", "Logo Reveals", "Double Horizontal Split", "Dual horizontal gate slide with center pop")
)

@Composable
fun TemplateGallerySection(
    templates: List<TemplateItemUiModel>,
    onSelectTemplate: (TemplateItemUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Templates",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${templates.size} available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(templates, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    onClick = { onSelectTemplate(template) }
                )
            }
        }
    }
}

@Composable
fun TemplateCard(
    template: TemplateItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(180.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = CutProIcons.TextTrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = template.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = template.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = template.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
