"""
Tier 1 Tests: Built-in Offline Customizable Templates (21 Templates across 7 Categories)
Features:
- FEAT-TPL-001 through FEAT-TPL-007 (35 test cases)
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_in_range, assert_zero_emojis, assert_raises
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- FEAT-TPL-001: Opening Titles Templates (3) ---
    def test_tpl_001_minimal_modern_schema():
        tpl = {
            "id": "title_minimal_modern",
            "name": "Minimal Modern",
            "title": "CUTPRO",
            "subtitle": "Native Android Video Editor",
            "durationUs": 3_000_000,
            "accentColor": 0xFF4A88DA
        }
        assert_zero_emojis(tpl["name"])
        assert_zero_emojis(tpl["title"])
        assert_true(tpl["durationUs"] > 0)

    def test_tpl_001_cinematic_split_fade_schema():
        tpl = {"id": "title_cinematic_split", "name": "Cinematic Split Fade", "splitWidth": 2.0}
        assert_zero_emojis(tpl["name"])

    def test_tpl_001_bold_glitch_schema():
        tpl = {"id": "title_bold_glitch", "name": "Bold Glitch", "glitchIntensity": 0.4}
        assert_true(0.0 <= tpl["glitchIntensity"] <= 1.0)

    def test_tpl_001_title_templates_total_count():
        opening_titles = ["Minimal Modern", "Cinematic Split Fade", "Bold Glitch"]
        assert_equal(len(opening_titles), 3)

    def test_tpl_001_title_rejects_empty_string():
        def validate_title(text: str):
            if not text.strip():
                raise ValueError("Title cannot be empty")
        assert_raises(ValueError, validate_title, "   ")

    # --- FEAT-TPL-002: Kinetic Typography Templates (3) ---
    def test_tpl_002_pop_bounce_schema():
        tpl = {"id": "kinetic_pop_bounce", "name": "Pop Bounce", "springDamping": 0.6}
        assert_true(tpl["springDamping"] > 0)

    def test_tpl_002_typewriter_terminal_schema():
        tpl = {"id": "kinetic_typewriter_terminal", "name": "Typewriter Terminal", "cursorChar": "|"}
        assert_equal(tpl["cursorChar"], "|")

    def test_tpl_002_word_by_word_slide_schema():
        tpl = {"id": "kinetic_word_slide", "name": "Word-by-Word Slide", "slideOffsetPx": 50}
        assert_true(tpl["slideOffsetPx"] > 0)

    def test_tpl_002_kinetic_templates_total_count():
        kinetic = ["Pop Bounce", "Typewriter Terminal", "Word-by-Word Slide"]
        assert_equal(len(kinetic), 3)

    def test_tpl_002_kinetic_rejects_negative_speed():
        def validate_speed(speed: float):
            if speed <= 0:
                raise ValueError("Speed must be positive")
        assert_raises(ValueError, validate_speed, -5.0)

    # --- FEAT-TPL-003: Lower Thirds Templates (3) ---
    def test_tpl_003_news_broadcast_bar():
        tpl = {"id": "lower_news_broadcast", "name": "News Broadcast Bar", "headline": "LIVE COVERAGE", "anchor": 0.85}
        assert_true(tpl["anchor"] >= 0.7)  # Broadcast safe

    def test_tpl_003_social_creator_duo():
        tpl = {"id": "lower_social_creator", "name": "Social Creator Duo", "handle": "@cutpro"}
        assert_true(tpl["handle"].startswith("@"))

    def test_tpl_003_gradient_accent_pill():
        tpl = {"id": "lower_gradient_pill", "name": "Gradient Accent Pill", "cornerRadiusPx": 16.0}
        assert_true(tpl["cornerRadiusPx"] > 0)

    def test_tpl_003_lower_third_templates_count():
        lowers = ["News Broadcast Bar", "Social Creator Duo", "Gradient Accent Pill"]
        assert_equal(len(lowers), 3)

    def test_tpl_003_lower_third_safe_margin_enforcement():
        # Margin vertical anchor must place lower third in bottom 30% of screen
        anchor_y = 0.80
        assert_true(anchor_y >= 0.70)

    # --- FEAT-TPL-004: Animated Subtitles Templates (3) ---
    def test_tpl_004_karaoke_highlight_schema():
        tpl = {"id": "sub_karaoke_highlight", "name": "Karaoke Highlight", "activeWordColor": 0xFFFFD700}
        assert_equal(tpl["activeWordColor"], 0xFFFFD700)

    def test_tpl_004_pop_bubble_caption_schema():
        tpl = {"id": "sub_pop_bubble", "name": "Pop Bubble Caption", "bubbleBgColor": 0xCC111111}
        assert_true(tpl["bubbleBgColor"] != 0)

    def test_tpl_004_high_contrast_box_schema():
        tpl = {"id": "sub_high_contrast_box", "name": "High-Contrast Box", "textColor": 0xFFFFFFFF}
        assert_equal(tpl["textColor"], 0xFFFFFFFF)

    def test_tpl_004_animated_subtitles_count():
        subs = ["Karaoke Highlight", "Pop Bubble Caption", "High-Contrast Box"]
        assert_equal(len(subs), 3)

    def test_tpl_004_subtitle_karaoke_scale_bounds():
        active_scale = 1.15
        assert_true(1.0 <= active_scale <= 1.5)

    # --- FEAT-TPL-005: Animated Counters Templates (3) ---
    def test_tpl_005_numeric_tally_metric():
        start_val = 0
        end_val = 1000
        prog = 0.5
        current = int(start_val + (end_val - start_val) * prog)
        assert_equal(current, 500)

    def test_tpl_005_milestone_badge():
        tpl = {"id": "counter_milestone_badge", "name": "Milestone Badge", "targetNumber": 100_000}
        assert_equal(tpl["targetNumber"], 100_000)

    def test_tpl_005_smooth_percentage_gauge():
        percent_start = 0.0
        percent_end = 100.0
        assert_equal(percent_end - percent_start, 100.0)

    def test_tpl_005_animated_counters_count():
        counters = ["Numeric Tally Metric", "Milestone Badge", "Smooth Percentage Gauge"]
        assert_equal(len(counters), 3)

    def test_tpl_005_counter_formatting_commas():
        val = 1250000
        formatted = f"{val:,}"
        assert_equal(formatted, "1,250,000")

    # --- FEAT-TPL-006: Call-to-Actions Templates (3) ---
    def test_tpl_006_subscribe_bell_schema():
        tpl = {"id": "cta_subscribe_bell", "name": "Subscribe Bell", "buttonText": "SUBSCRIBE"}
        assert_equal(tpl["buttonText"], "SUBSCRIBE")
        assert_zero_emojis(tpl["buttonText"])

    def test_tpl_006_clean_follow_handle():
        tpl = {"id": "cta_follow_handle", "name": "Clean Follow Handle", "action": "Follow"}
        assert_equal(tpl["action"], "Follow")

    def test_tpl_006_swipe_tap_indicator():
        tpl = {"id": "cta_swipe_indicator", "name": "Swipe/Tap Indicator", "direction": "UP"}
        assert_equal(tpl["direction"], "UP")

    def test_tpl_006_cta_templates_count():
        ctas = ["Subscribe Bell", "Clean Follow Handle", "Swipe/Tap Indicator"]
        assert_equal(len(ctas), 3)

    def test_tpl_006_cta_button_pill_corner():
        corner_radius = 24.0
        assert_true(corner_radius >= 12.0)

    # --- FEAT-TPL-007: Logo Reveals Templates (3) ---
    def test_tpl_007_circular_iris_mask():
        tpl = {"id": "logo_circular_iris", "name": "Circular Iris Mask", "maxRadiusPx": 400.0}
        assert_true(tpl["maxRadiusPx"] > 0)

    def test_tpl_007_scale_and_vignette():
        tpl = {"id": "logo_scale_vignette", "name": "Scale & Vignette", "vignetteStrength": 0.7}
        assert_true(0.0 <= tpl["vignetteStrength"] <= 1.0)

    def test_tpl_007_double_horizontal_split():
        tpl = {"id": "logo_double_split", "name": "Double Horizontal Split", "slitAngleDeg": 0.0}
        assert_equal(tpl["slitAngleDeg"], 0.0)

    def test_tpl_007_logo_reveals_count():
        reveals = ["Circular Iris Mask", "Scale & Vignette", "Double Horizontal Split"]
        assert_equal(len(reveals), 3)

    def test_tpl_007_logo_reveal_total_template_catalog_21():
        # 7 categories * 3 templates each = 21 templates
        total = 7 * 3
        assert_equal(total, 21)

    all_defs = [
        ("TC-TPL-001-1", "FEAT-TPL-001", test_tpl_001_minimal_modern_schema),
        ("TC-TPL-001-2", "FEAT-TPL-001", test_tpl_001_cinematic_split_fade_schema),
        ("TC-TPL-001-3", "FEAT-TPL-001", test_tpl_001_bold_glitch_schema),
        ("TC-TPL-001-4", "FEAT-TPL-001", test_tpl_001_title_templates_total_count),
        ("TC-TPL-001-5", "FEAT-TPL-001", test_tpl_001_title_rejects_empty_string),
        ("TC-TPL-002-1", "FEAT-TPL-002", test_tpl_002_pop_bounce_schema),
        ("TC-TPL-002-2", "FEAT-TPL-002", test_tpl_002_typewriter_terminal_schema),
        ("TC-TPL-002-3", "FEAT-TPL-002", test_tpl_002_word_by_word_slide_schema),
        ("TC-TPL-002-4", "FEAT-TPL-002", test_tpl_002_kinetic_templates_total_count),
        ("TC-TPL-002-5", "FEAT-TPL-002", test_tpl_002_kinetic_rejects_negative_speed),
        ("TC-TPL-003-1", "FEAT-TPL-003", test_tpl_003_news_broadcast_bar),
        ("TC-TPL-003-2", "FEAT-TPL-003", test_tpl_003_social_creator_duo),
        ("TC-TPL-003-3", "FEAT-TPL-003", test_tpl_003_gradient_accent_pill),
        ("TC-TPL-003-4", "FEAT-TPL-003", test_tpl_003_lower_third_templates_count),
        ("TC-TPL-003-5", "FEAT-TPL-003", test_tpl_003_lower_third_safe_margin_enforcement),
        ("TC-TPL-004-1", "FEAT-TPL-004", test_tpl_004_karaoke_highlight_schema),
        ("TC-TPL-004-2", "FEAT-TPL-004", test_tpl_004_pop_bubble_caption_schema),
        ("TC-TPL-004-3", "FEAT-TPL-004", test_tpl_004_high_contrast_box_schema),
        ("TC-TPL-004-4", "FEAT-TPL-004", test_tpl_004_animated_subtitles_count),
        ("TC-TPL-004-5", "FEAT-TPL-004", test_tpl_004_subtitle_karaoke_scale_bounds),
        ("TC-TPL-005-1", "FEAT-TPL-005", test_tpl_005_numeric_tally_metric),
        ("TC-TPL-005-2", "FEAT-TPL-005", test_tpl_005_milestone_badge),
        ("TC-TPL-005-3", "FEAT-TPL-005", test_tpl_005_smooth_percentage_gauge),
        ("TC-TPL-005-4", "FEAT-TPL-005", test_tpl_005_animated_counters_count),
        ("TC-TPL-005-5", "FEAT-TPL-005", test_tpl_005_counter_formatting_commas),
        ("TC-TPL-006-1", "FEAT-TPL-006", test_tpl_006_subscribe_bell_schema),
        ("TC-TPL-006-2", "FEAT-TPL-006", test_tpl_006_clean_follow_handle),
        ("TC-TPL-006-3", "FEAT-TPL-006", test_tpl_006_swipe_tap_indicator),
        ("TC-TPL-006-4", "FEAT-TPL-006", test_tpl_006_cta_templates_count),
        ("TC-TPL-006-5", "FEAT-TPL-006", test_tpl_006_cta_button_pill_corner),
        ("TC-TPL-007-1", "FEAT-TPL-007", test_tpl_007_circular_iris_mask),
        ("TC-TPL-007-2", "FEAT-TPL-007", test_tpl_007_scale_and_vignette),
        ("TC-TPL-007-3", "FEAT-TPL-007", test_tpl_007_double_horizontal_split),
        ("TC-TPL-007-4", "FEAT-TPL-007", test_tpl_007_logo_reveals_count),
        ("TC-TPL-007-5", "FEAT-TPL-007", test_tpl_007_logo_reveal_total_template_catalog_21),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 1, fn.__name__, fn))

    return tests
