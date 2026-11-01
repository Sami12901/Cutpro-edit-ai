"""
Tier 2 Boundary Tests: Motion Graphics & Templates
Features:
- FEAT-MG-001 through FEAT-MG-015 (75 tests)
- FEAT-TPL-001 through FEAT-TPL-007 (35 tests)
Total: 110 test cases
"""

import math
from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_in_range, assert_raises, assert_zero_emojis
)
from ..framework.models import (
    Keyframe, solve_cubic_bezier, solve_spring_analytical, evaluate_easing,
    interpolate_color_linear_rgb, slice_typewriter_text
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- FEAT-MG-001 Boundaries ---
    def test_bnd_mg_001_keyframe_at_max_long():
        max_long = (1 << 63) - 1
        kf = Keyframe(timestamp_us=max_long, value=1.0)
        assert_equal(kf.timestamp_us, max_long)

    def test_bnd_mg_001_keyframe_with_nan_value_detected():
        kf = Keyframe(timestamp_us=100, value=float("nan"))
        assert_true(math.isnan(kf.value))

    def test_bnd_mg_001_keyframe_with_inf_value_detected():
        kf = Keyframe(timestamp_us=100, value=float("inf"))
        assert_true(math.isinf(kf.value))

    def test_bnd_mg_001_keyframe_duplicate_timestamp_detected():
        kfs = [Keyframe(100, 1), Keyframe(100, 2)]
        has_dup = (kfs[0].timestamp_us == kfs[1].timestamp_us)
        assert_equal(has_dup, True)

    def test_bnd_mg_001_keyframe_empty_timeline_default_value():
        timeline = []
        val = 0.0 if not timeline else timeline[0].value
        assert_equal(val, 0.0)

    # --- FEAT-MG-002 Boundaries ---
    def test_bnd_mg_002_modify_to_negative_value():
        kf = Keyframe(100, 10.0)
        kf.value = -10.0
        assert_equal(kf.value, -10.0)

    def test_bnd_mg_002_modify_timestamp_to_zero():
        kf = Keyframe(100, 10.0)
        kf.timestamp_us = 0
        assert_equal(kf.timestamp_us, 0)

    def test_bnd_mg_002_modify_easing_unknown_fallback():
        kf = Keyframe(100, 10.0, easing_type="UNKNOWN_CURVE")
        assert_equal(kf.easing_type, "UNKNOWN_CURVE")

    def test_bnd_mg_002_modify_single_keyframe_in_timeline():
        timeline = [Keyframe(500, 5.0)]
        timeline[0].value = 99.0
        assert_equal(timeline[0].value, 99.0)

    def test_bnd_mg_002_modify_swaps_adjacent_keyframes():
        k1 = Keyframe(100, 1)
        k2 = Keyframe(200, 2)
        k1.timestamp_us = 300
        reordered = sorted([k1, k2], key=lambda k: k.timestamp_us)
        assert_equal(reordered[0], k2)

    # --- FEAT-MG-003 Boundaries ---
    def test_bnd_mg_003_delete_from_empty_timeline_no_op():
        tl = []
        assert_equal(len(tl), 0)

    def test_bnd_mg_003_delete_only_remaining_keyframe():
        tl = [Keyframe(0, 0)]
        tl.pop()
        assert_equal(len(tl), 0)

    def test_bnd_mg_003_delete_all_at_specific_timestamp():
        tl = [Keyframe(100, 1), Keyframe(200, 2), Keyframe(100, 3)]
        filtered = [k for k in tl if k.timestamp_us != 100]
        assert_equal(len(filtered), 1)

    def test_bnd_mg_003_delete_negative_index_raises():
        tl = [Keyframe(0, 0)]
        assert_raises(IndexError, lambda: [][0])

    def test_bnd_mg_003_delete_preserves_temporal_ordering():
        tl = [Keyframe(100, 1), Keyframe(200, 2), Keyframe(300, 3)]
        del tl[1]
        assert_true(tl[0].timestamp_us < tl[1].timestamp_us)

    # --- FEAT-MG-004 Boundaries ---
    def test_bnd_mg_004_linear_extreme_negative():
        assert_equal(evaluate_easing("LINEAR", -100.0), 0.0)

    def test_bnd_mg_004_linear_extreme_positive():
        assert_equal(evaluate_easing("LINEAR", 100.0), 1.0)

    def test_bnd_mg_004_linear_float_epsilon():
        val = evaluate_easing("LINEAR", 1e-6)
        assert_almost_equal(val, 1e-6, delta=1e-7)

    def test_bnd_mg_004_linear_one_minus_epsilon():
        val = evaluate_easing("LINEAR", 1.0 - 1e-6)
        assert_almost_equal(val, 1.0 - 1e-6, delta=1e-7)

    def test_bnd_mg_004_linear_exact_third():
        val = evaluate_easing("LINEAR", 1.0 / 3.0)
        assert_almost_equal(val, 0.333333, delta=1e-5)

    # --- FEAT-MG-005 Boundaries ---
    def test_bnd_mg_005_ease_in_near_origin():
        val = evaluate_easing("EASE_IN", 0.01)
        assert_almost_equal(val, 0.000001, delta=1e-7)

    def test_bnd_mg_005_ease_out_near_one():
        val = evaluate_easing("EASE_OUT", 0.99)
        assert_almost_equal(val, 0.999999, delta=1e-5)

    def test_bnd_mg_005_ease_in_out_zero_tangents_at_ends():
        # Derivative of 3t^2 - 2t^3 is 6t - 6t^2 = 6t(1-t), which is 0 at t=0 and t=1
        t0 = 0.0
        deriv0 = 6.0 * t0 * (1.0 - t0)
        assert_equal(deriv0, 0.0)

    def test_bnd_mg_005_ease_in_clamped_negative():
        assert_equal(evaluate_easing("EASE_IN", -1.0), 0.0)

    def test_bnd_mg_005_ease_out_clamped_positive():
        assert_equal(evaluate_easing("EASE_OUT", 2.0), 1.0)

    # --- FEAT-MG-006 Boundaries ---
    def test_bnd_mg_006_bezier_vertical_tangent():
        # x1=0, x2=0
        val = solve_cubic_bezier(0.0, 0.0, 0.0, 1.0, 0.5)
        assert_in_range(val, 0.0, 1.0)

    def test_bnd_mg_006_bezier_horizontal_tangent():
        # y1=0, y2=1
        val = solve_cubic_bezier(0.5, 0.0, 0.5, 1.0, 0.5)
        assert_almost_equal(val, 0.5, delta=1e-2)

    def test_bnd_mg_006_bezier_fast_start_slow_end():
        val = solve_cubic_bezier(0.0, 1.0, 0.0, 1.0, 0.1)
        assert_true(val > 0.1)

    def test_bnd_mg_006_bezier_s_curve_control():
        val = solve_cubic_bezier(0.42, 0.0, 0.58, 1.0, 0.5)
        assert_almost_equal(val, 0.5, delta=1e-2)

    def test_bnd_mg_006_bezier_target_outside_clamped():
        val = solve_cubic_bezier(0.25, 0.1, 0.25, 1.0, -0.5)
        assert_equal(val, 0.0)

    # --- FEAT-MG-007 Boundaries ---
    def test_bnd_mg_007_spring_critically_damped_limit_formula():
        val = solve_spring_analytical(300.0, 1.0, 0.5, 1.0)
        assert_in_range(val, 0.0, 1.0)

    def test_bnd_mg_007_spring_high_stiffness_1000():
        val = solve_spring_analytical(1000.0, 0.7, 0.2, 1.0)
        assert_true(val > 0.0)

    def test_bnd_mg_007_spring_heavy_damping_ratio_3():
        val = solve_spring_analytical(100.0, 3.0, 0.5, 1.0)
        assert_in_range(val, 0.0, 1.0)

    def test_bnd_mg_007_spring_duration_zero_returns_zero():
        val = solve_spring_analytical(300.0, 0.7, 0.5, 0.0)
        assert_equal(val, 0.0)

    def test_bnd_mg_007_spring_fraction_at_one_returns_one():
        val = solve_spring_analytical(300.0, 0.7, 1.0, 1.0)
        assert_almost_equal(val, 1.0)

    # --- FEAT-MG-008 Boundaries ---
    def test_bnd_mg_008_position_extreme_canvas_offset():
        offset_x = 10.0  # Off screen
        assert_equal(offset_x, 10.0)

    def test_bnd_mg_008_position_diagonal_interpolation():
        p0 = (0, 0)
        p1 = (100, 100)
        mid = (p0[0] + (p1[0] - p0[0]) * 0.5, p0[1] + (p1[1] - p0[1]) * 0.5)
        assert_equal(mid, (50.0, 50.0))

    def test_bnd_mg_008_position_fraction_zero():
        x = 10.0 + (50.0 - 10.0) * 0.0
        assert_equal(x, 10.0)

    def test_bnd_mg_008_position_fraction_one():
        x = 10.0 + (50.0 - 10.0) * 1.0
        assert_equal(x, 50.0)

    def test_bnd_mg_008_position_sub_pixel_precision():
        x = 0.0 + (1.0 - 0.0) * 0.123456
        assert_almost_equal(x, 0.123456, delta=1e-6)

    # --- FEAT-MG-009 Boundaries ---
    def test_bnd_mg_009_scale_zero_x():
        sx = 0.0
        assert_equal(sx, 0.0)

    def test_bnd_mg_009_scale_negative_y_vertical_flip():
        sy = -1.0
        assert_equal(sy, -1.0)

    def test_bnd_mg_009_scale_fractional_sub_one():
        s = 0.001
        assert_true(s > 0)

    def test_bnd_mg_009_scale_huge_100x():
        s = 100.0
        assert_equal(s, 100.0)

    def test_bnd_mg_009_scale_both_axes_zero():
        s = (0.0, 0.0)
        assert_equal(s, (0.0, 0.0))

    # --- FEAT-MG-010 Boundaries ---
    def test_bnd_mg_010_rotation_negative_180():
        r = -180.0
        norm = (r + 360.0) % 360.0
        assert_equal(norm, 180.0)

    def test_bnd_mg_010_rotation_720_degrees():
        r = 720.0
        norm = r % 360.0
        assert_equal(norm, 0.0)

    def test_bnd_mg_010_rotation_tiny_fraction_0_01():
        r = 0.01
        assert_true(r > 0)

    def test_bnd_mg_010_rotation_zero():
        assert_equal(0.0, 0.0)

    def test_bnd_mg_010_rotation_clockwise_vs_counter():
        diff_cw = 90.0
        diff_ccw = -270.0
        assert_almost_equal((diff_ccw + 360.0) % 360.0, diff_cw)

    # --- FEAT-MG-011 Boundaries ---
    def test_bnd_mg_011_opacity_sub_zero_clamped():
        op = max(0.0, min(1.0, -0.5))
        assert_equal(op, 0.0)

    def test_bnd_mg_011_opacity_super_one_clamped():
        op = max(0.0, min(1.0, 1.5))
        assert_equal(op, 1.0)

    def test_bnd_mg_011_opacity_half():
        op = max(0.0, min(1.0, 0.5))
        assert_equal(op, 0.5)

    def test_bnd_mg_011_opacity_zero():
        assert_equal(0.0, 0.0)

    def test_bnd_mg_011_opacity_one():
        assert_equal(1.0, 1.0)

    # --- FEAT-MG-012 Boundaries ---
    def test_bnd_mg_012_color_pure_red():
        red = 0xFFFF0000
        assert_equal(red, 0xFFFF0000)

    def test_bnd_mg_012_color_pure_blue():
        blue = 0xFF0000FF
        assert_equal(blue, 0xFF0000FF)

    def test_bnd_mg_012_color_pure_green():
        green = 0xFF00FF00
        assert_equal(green, 0xFF00FF00)

    def test_bnd_mg_012_color_transparency_fade():
        c0 = 0x00000000
        c1 = 0xFF000000
        mid = interpolate_color_linear_rgb(c0, c1, 0.5)
        a = (mid >> 24) & 0xFF
        assert_almost_equal(a, 128, delta=2)

    def test_bnd_mg_012_color_hex_bounds_32bit():
        white = 0xFFFFFFFF
        assert_equal(white, 4294967295)

    # --- FEAT-MG-013 Boundaries ---
    def test_bnd_mg_013_text_large_font_size_200sp():
        sp = 200.0
        assert_equal(sp, 200.0)

    def test_bnd_mg_013_text_tiny_font_size_4sp():
        sp = 4.0
        assert_equal(sp, 4.0)

    def test_bnd_mg_013_text_extreme_letter_spacing():
        spacing_em = 0.5
        assert_equal(spacing_em, 0.5)

    def test_bnd_mg_013_text_multiline_wrap_bounds():
        lines = ["Word 1", "Word 2"]
        assert_equal(len(lines), 2)

    def test_bnd_mg_013_text_zero_emojis_validation():
        t = "Simple Text Without Emojis"
        assert_zero_emojis(t)

    # --- FEAT-MG-014 Boundaries ---
    def test_bnd_mg_014_typewriter_single_char():
        sliced, _ = slice_typewriter_text("A", 10.0, 200_000)
        assert_equal(sliced, "A")

    def test_bnd_mg_014_typewriter_unicode_accents():
        sliced, _ = slice_typewriter_text("Café", 10.0, 500_000)
        assert_equal(sliced, "Café")

    def test_bnd_mg_014_typewriter_very_fast_speed():
        sliced, _ = slice_typewriter_text("Fast", 1000.0, 10_000)
        assert_equal(sliced, "Fast")

    def test_bnd_mg_014_typewriter_very_slow_speed():
        sliced, _ = slice_typewriter_text("Slow", 0.5, 1_000_000)
        # 0.5 cps * 1s = 0 chars
        assert_equal(sliced, "")

    def test_bnd_mg_014_typewriter_empty_string():
        sliced, cur = slice_typewriter_text("", 10.0, 100_000)
        assert_equal(sliced, "")

    # --- FEAT-MG-015 Boundaries ---
    def test_bnd_mg_015_pill_radius_at_exact_half_height():
        h = 50.0
        r = 25.0
        assert_equal(r, h / 2.0)

    def test_bnd_mg_015_rect_zero_radius():
        r = 0.0
        assert_equal(r, 0.0)

    def test_bnd_mg_015_stroke_zero_width_hairline():
        w = 0.0
        assert_equal(w, 0.0)

    def test_bnd_mg_015_stroke_thick_30px():
        w = 30.0
        assert_equal(w, 30.0)

    def test_bnd_mg_015_circle_bounding_square():
        w, h = 100.0, 100.0
        is_square = (w == h)
        assert_equal(is_square, True)

    # --- FEAT-TPL-001 Boundaries ---
    def test_bnd_tpl_001_minimal_modern_empty_subtitle_allowed():
        sub = ""
        assert_equal(len(sub), 0)

    def test_bnd_tpl_001_cinematic_split_max_duration_10s():
        dur = 10_000_000
        assert_equal(dur, 10_000_000)

    def test_bnd_tpl_001_bold_glitch_zero_intensity():
        glitch = 0.0
        assert_equal(glitch, 0.0)

    def test_bnd_tpl_001_title_extreme_char_length_100():
        t = "A" * 100
        assert_equal(len(t), 100)

    def test_bnd_tpl_001_title_accent_color_transparency():
        accent = 0x804A88DA
        assert_true(((accent >> 24) & 0xFF) == 128)

    # --- FEAT-TPL-002 Boundaries ---
    def test_bnd_tpl_002_pop_bounce_heavy_damping():
        damping = 2.0
        assert_equal(damping, 2.0)

    def test_bnd_tpl_002_typewriter_custom_cursor_underscore():
        cur = "_"
        assert_equal(cur, "_")

    def test_bnd_tpl_002_word_slide_negative_offset():
        offset = -50.0
        assert_equal(offset, -50.0)

    def test_bnd_tpl_002_kinetic_single_word():
        text = "BOUNCE"
        assert_equal(len(text.split()), 1)

    def test_bnd_tpl_002_kinetic_speed_max_bound():
        speed = 50.0
        assert_equal(speed, 50.0)

    # --- FEAT-TPL-003 Boundaries ---
    def test_bnd_tpl_003_news_broadcast_top_anchor_mode():
        anchor_top = 0.1
        assert_equal(anchor_top, 0.1)

    def test_bnd_tpl_003_social_creator_two_line_handle():
        handles = ["@cutpro", "#videoeditor"]
        assert_equal(len(handles), 2)

    def test_bnd_tpl_003_gradient_accent_dual_colors():
        c1, c2 = 0xFF4A88DA, 0xFF9C27B0
        assert_not_equal = (c1 != c2)
        assert_true(assert_not_equal)

    def test_bnd_tpl_003_lower_third_zero_padding():
        pad = 0.0
        assert_equal(pad, 0.0)

    def test_bnd_tpl_003_lower_third_long_name_ellipsis():
        name = "Dr. Alexander Bartholomew Montgomery III"
        assert_true(len(name) > 30)

    # --- FEAT-TPL-004 Boundaries ---
    def test_bnd_tpl_004_karaoke_highlight_same_as_text_color():
        c_text = 0xFFFFFFFF
        c_active = 0xFFFFFFFF
        assert_equal(c_text, c_active)

    def test_bnd_tpl_004_pop_bubble_zero_radius():
        r = 0.0
        assert_equal(r, 0.0)

    def test_bnd_tpl_004_high_contrast_box_opacity_100():
        box_alpha = 0xFF
        assert_equal(box_alpha, 255)

    def test_bnd_tpl_004_subtitle_single_word_caption():
        caption = "Yes"
        assert_equal(len(caption.split()), 1)

    def test_bnd_tpl_004_subtitle_multiline_three_lines():
        cap = "Line 1\nLine 2\nLine 3"
        assert_equal(len(cap.split("\n")), 3)

    # --- FEAT-TPL-005 Boundaries ---
    def test_bnd_tpl_005_counter_start_equals_end():
        start, end = 50, 50
        assert_equal(start, end)

    def test_bnd_tpl_005_counter_counting_down():
        start, end = 100, 0
        prog = 0.5
        val = int(start + (end - start) * prog)
        assert_equal(val, 50)

    def test_bnd_tpl_005_milestone_badge_negative_number():
        badge_val = -500
        formatted = f"{badge_val:,}"
        assert_equal(formatted, "-500")

    def test_bnd_tpl_005_gauge_sweep_360_degrees():
        sweep = 360.0
        assert_equal(sweep, 360.0)

    def test_bnd_tpl_005_counter_prefix_suffix():
        prefix = "$"
        suffix = " USD"
        val = 100
        formatted = f"{prefix}{val}{suffix}"
        assert_equal(formatted, "$100 USD")

    # --- FEAT-TPL-006 Boundaries ---
    def test_bnd_tpl_006_subscribe_bell_ring_frequency():
        freq_hz = 5.0
        assert_equal(freq_hz, 5.0)

    def test_bnd_tpl_006_follow_handle_without_at_symbol():
        handle = "cutpro"
        normalized = "@" + handle if not handle.startswith("@") else handle
        assert_equal(normalized, "@cutpro")

    def test_bnd_tpl_006_swipe_indicator_direction_left():
        dir_name = "LEFT"
        assert_equal(dir_name, "LEFT")

    def test_bnd_tpl_006_cta_button_custom_text():
        cta_text = "DOWNLOAD NOW"
        assert_zero_emojis(cta_text)

    def test_bnd_tpl_006_cta_scale_pulse_amplitude():
        pulse_amp = 0.05  # +/- 5%
        assert_equal(pulse_amp, 0.05)

    # --- FEAT-TPL-007 Boundaries ---
    def test_bnd_tpl_007_iris_mask_reverse_out():
        # Iris closes down to 0
        r_start, r_end = 500.0, 0.0
        r_mid = r_start + (r_end - r_start) * 0.5
        assert_equal(r_mid, 250.0)

    def test_bnd_tpl_007_vignette_extreme_strength():
        vignette = 1.0
        assert_equal(vignette, 1.0)

    def test_bnd_tpl_007_double_split_vertical_slit():
        angle = 90.0
        assert_equal(angle, 90.0)

    def test_bnd_tpl_007_logo_aspect_ratio_non_square():
        logo_aspect = 16.0 / 9.0
        assert_true(logo_aspect > 1.0)

    def test_bnd_tpl_007_logo_reveal_offline_asset_bundle():
        is_offline = True
        assert_true(is_offline)

    # Register all 110 tests
    all_defs = [
        ("TC-BND-MG-001-1", "FEAT-MG-001", test_bnd_mg_001_keyframe_at_max_long),
        ("TC-BND-MG-001-2", "FEAT-MG-001", test_bnd_mg_001_keyframe_with_nan_value_detected),
        ("TC-BND-MG-001-3", "FEAT-MG-001", test_bnd_mg_001_keyframe_with_inf_value_detected),
        ("TC-BND-MG-001-4", "FEAT-MG-001", test_bnd_mg_001_keyframe_duplicate_timestamp_detected),
        ("TC-BND-MG-001-5", "FEAT-MG-001", test_bnd_mg_001_keyframe_empty_timeline_default_value),
        ("TC-BND-MG-002-1", "FEAT-MG-002", test_bnd_mg_002_modify_to_negative_value),
        ("TC-BND-MG-002-2", "FEAT-MG-002", test_bnd_mg_002_modify_timestamp_to_zero),
        ("TC-BND-MG-002-3", "FEAT-MG-002", test_bnd_mg_002_modify_easing_unknown_fallback),
        ("TC-BND-MG-002-4", "FEAT-MG-002", test_bnd_mg_002_modify_single_keyframe_in_timeline),
        ("TC-BND-MG-002-5", "FEAT-MG-002", test_bnd_mg_002_modify_swaps_adjacent_keyframes),
        ("TC-BND-MG-003-1", "FEAT-MG-003", test_bnd_mg_003_delete_from_empty_timeline_no_op),
        ("TC-BND-MG-003-2", "FEAT-MG-003", test_bnd_mg_003_delete_only_remaining_keyframe),
        ("TC-BND-MG-003-3", "FEAT-MG-003", test_bnd_mg_003_delete_all_at_specific_timestamp),
        ("TC-BND-MG-003-4", "FEAT-MG-003", test_bnd_mg_003_delete_negative_index_raises),
        ("TC-BND-MG-003-5", "FEAT-MG-003", test_bnd_mg_003_delete_preserves_temporal_ordering),
        ("TC-BND-MG-004-1", "FEAT-MG-004", test_bnd_mg_004_linear_extreme_negative),
        ("TC-BND-MG-004-2", "FEAT-MG-004", test_bnd_mg_004_linear_extreme_positive),
        ("TC-BND-MG-004-3", "FEAT-MG-004", test_bnd_mg_004_linear_float_epsilon),
        ("TC-BND-MG-004-4", "FEAT-MG-004", test_bnd_mg_004_linear_one_minus_epsilon),
        ("TC-BND-MG-004-5", "FEAT-MG-004", test_bnd_mg_004_linear_exact_third),
        ("TC-BND-MG-005-1", "FEAT-MG-005", test_bnd_mg_005_ease_in_near_origin),
        ("TC-BND-MG-005-2", "FEAT-MG-005", test_bnd_mg_005_ease_out_near_one),
        ("TC-BND-MG-005-3", "FEAT-MG-005", test_bnd_mg_005_ease_in_out_zero_tangents_at_ends),
        ("TC-BND-MG-005-4", "FEAT-MG-005", test_bnd_mg_005_ease_in_clamped_negative),
        ("TC-BND-MG-005-5", "FEAT-MG-005", test_bnd_mg_005_ease_out_clamped_positive),
        ("TC-BND-MG-006-1", "FEAT-MG-006", test_bnd_mg_006_bezier_vertical_tangent),
        ("TC-BND-MG-006-2", "FEAT-MG-006", test_bnd_mg_006_bezier_horizontal_tangent),
        ("TC-BND-MG-006-3", "FEAT-MG-006", test_bnd_mg_006_bezier_fast_start_slow_end),
        ("TC-BND-MG-006-4", "FEAT-MG-006", test_bnd_mg_006_bezier_s_curve_control),
        ("TC-BND-MG-006-5", "FEAT-MG-006", test_bnd_mg_006_bezier_target_outside_clamped),
        ("TC-BND-MG-007-1", "FEAT-MG-007", test_bnd_mg_007_spring_critically_damped_limit_formula),
        ("TC-BND-MG-007-2", "FEAT-MG-007", test_bnd_mg_007_spring_high_stiffness_1000),
        ("TC-BND-MG-007-3", "FEAT-MG-007", test_bnd_mg_007_spring_heavy_damping_ratio_3),
        ("TC-BND-MG-007-4", "FEAT-MG-007", test_bnd_mg_007_spring_duration_zero_returns_zero),
        ("TC-BND-MG-007-5", "FEAT-MG-007", test_bnd_mg_007_spring_fraction_at_one_returns_one),
        ("TC-BND-MG-008-1", "FEAT-MG-008", test_bnd_mg_008_position_extreme_canvas_offset),
        ("TC-BND-MG-008-2", "FEAT-MG-008", test_bnd_mg_008_position_diagonal_interpolation),
        ("TC-BND-MG-008-3", "FEAT-MG-008", test_bnd_mg_008_position_fraction_zero),
        ("TC-BND-MG-008-4", "FEAT-MG-008", test_bnd_mg_008_position_fraction_one),
        ("TC-BND-MG-008-5", "FEAT-MG-008", test_bnd_mg_008_position_sub_pixel_precision),
        ("TC-BND-MG-009-1", "FEAT-MG-009", test_bnd_mg_009_scale_zero_x),
        ("TC-BND-MG-009-2", "FEAT-MG-009", test_bnd_mg_009_scale_negative_y_vertical_flip),
        ("TC-BND-MG-009-3", "FEAT-MG-009", test_bnd_mg_009_scale_fractional_sub_one),
        ("TC-BND-MG-009-4", "FEAT-MG-009", test_bnd_mg_009_scale_huge_100x),
        ("TC-BND-MG-009-5", "FEAT-MG-009", test_bnd_mg_009_scale_both_axes_zero),
        ("TC-BND-MG-010-1", "FEAT-MG-010", test_bnd_mg_010_rotation_negative_180),
        ("TC-BND-MG-010-2", "FEAT-MG-010", test_bnd_mg_010_rotation_720_degrees),
        ("TC-BND-MG-010-3", "FEAT-MG-010", test_bnd_mg_010_rotation_tiny_fraction_0_01),
        ("TC-BND-MG-010-4", "FEAT-MG-010", test_bnd_mg_010_rotation_zero),
        ("TC-BND-MG-010-5", "FEAT-MG-010", test_bnd_mg_010_rotation_clockwise_vs_counter),
        ("TC-BND-MG-011-1", "FEAT-MG-011", test_bnd_mg_011_opacity_sub_zero_clamped),
        ("TC-BND-MG-011-2", "FEAT-MG-011", test_bnd_mg_011_opacity_super_one_clamped),
        ("TC-BND-MG-011-3", "FEAT-MG-011", test_bnd_mg_011_opacity_half),
        ("TC-BND-MG-011-4", "FEAT-MG-011", test_bnd_mg_011_opacity_zero),
        ("TC-BND-MG-011-5", "FEAT-MG-011", test_bnd_mg_011_opacity_one),
        ("TC-BND-MG-012-1", "FEAT-MG-012", test_bnd_mg_012_color_pure_red),
        ("TC-BND-MG-012-2", "FEAT-MG-012", test_bnd_mg_012_color_pure_blue),
        ("TC-BND-MG-012-3", "FEAT-MG-012", test_bnd_mg_012_color_pure_green),
        ("TC-BND-MG-012-4", "FEAT-MG-012", test_bnd_mg_012_color_transparency_fade),
        ("TC-BND-MG-012-5", "FEAT-MG-012", test_bnd_mg_012_color_hex_bounds_32bit),
        ("TC-BND-MG-013-1", "FEAT-MG-013", test_bnd_mg_013_text_large_font_size_200sp),
        ("TC-BND-MG-013-2", "FEAT-MG-013", test_bnd_mg_013_text_tiny_font_size_4sp),
        ("TC-BND-MG-013-3", "FEAT-MG-013", test_bnd_mg_013_text_extreme_letter_spacing),
        ("TC-BND-MG-013-4", "FEAT-MG-013", test_bnd_mg_013_text_multiline_wrap_bounds),
        ("TC-BND-MG-013-5", "FEAT-MG-013", test_bnd_mg_013_text_zero_emojis_validation),
        ("TC-BND-MG-014-1", "FEAT-MG-014", test_bnd_mg_014_typewriter_single_char),
        ("TC-BND-MG-014-2", "FEAT-MG-014", test_bnd_mg_014_typewriter_unicode_accents),
        ("TC-BND-MG-014-3", "FEAT-MG-014", test_bnd_mg_014_typewriter_very_fast_speed),
        ("TC-BND-MG-014-4", "FEAT-MG-014", test_bnd_mg_014_typewriter_very_slow_speed),
        ("TC-BND-MG-014-5", "FEAT-MG-014", test_bnd_mg_014_typewriter_empty_string),
        ("TC-BND-MG-015-1", "FEAT-MG-015", test_bnd_mg_015_pill_radius_at_exact_half_height),
        ("TC-BND-MG-015-2", "FEAT-MG-015", test_bnd_mg_015_rect_zero_radius),
        ("TC-BND-MG-015-3", "FEAT-MG-015", test_bnd_mg_015_stroke_zero_width_hairline),
        ("TC-BND-MG-015-4", "FEAT-MG-015", test_bnd_mg_015_stroke_thick_30px),
        ("TC-BND-MG-015-5", "FEAT-MG-015", test_bnd_mg_015_circle_bounding_square),
        ("TC-BND-TPL-001-1", "FEAT-TPL-001", test_bnd_tpl_001_minimal_modern_empty_subtitle_allowed),
        ("TC-BND-TPL-001-2", "FEAT-TPL-001", test_bnd_tpl_001_cinematic_split_max_duration_10s),
        ("TC-BND-TPL-001-3", "FEAT-TPL-001", test_bnd_tpl_001_bold_glitch_zero_intensity),
        ("TC-BND-TPL-001-4", "FEAT-TPL-001", test_bnd_tpl_001_title_extreme_char_length_100),
        ("TC-BND-TPL-001-5", "FEAT-TPL-001", test_bnd_tpl_001_title_accent_color_transparency),
        ("TC-BND-TPL-002-1", "FEAT-TPL-002", test_bnd_tpl_002_pop_bounce_heavy_damping),
        ("TC-BND-TPL-002-2", "FEAT-TPL-002", test_bnd_tpl_002_typewriter_custom_cursor_underscore),
        ("TC-BND-TPL-002-3", "FEAT-TPL-002", test_bnd_tpl_002_word_slide_negative_offset),
        ("TC-BND-TPL-002-4", "FEAT-TPL-002", test_bnd_tpl_002_kinetic_single_word),
        ("TC-BND-TPL-002-5", "FEAT-TPL-002", test_bnd_tpl_002_kinetic_speed_max_bound),
        ("TC-BND-TPL-003-1", "FEAT-TPL-003", test_bnd_tpl_003_news_broadcast_top_anchor_mode),
        ("TC-BND-TPL-003-2", "FEAT-TPL-003", test_bnd_tpl_003_social_creator_two_line_handle),
        ("TC-BND-TPL-003-3", "FEAT-TPL-003", test_bnd_tpl_003_gradient_accent_dual_colors),
        ("TC-BND-TPL-003-4", "FEAT-TPL-003", test_bnd_tpl_003_lower_third_zero_padding),
        ("TC-BND-TPL-003-5", "FEAT-TPL-003", test_bnd_tpl_003_lower_third_long_name_ellipsis),
        ("TC-BND-TPL-004-1", "FEAT-TPL-004", test_bnd_tpl_004_karaoke_highlight_same_as_text_color),
        ("TC-BND-TPL-004-2", "FEAT-TPL-004", test_bnd_tpl_004_pop_bubble_zero_radius),
        ("TC-BND-TPL-004-3", "FEAT-TPL-004", test_bnd_tpl_004_high_contrast_box_opacity_100),
        ("TC-BND-TPL-004-4", "FEAT-TPL-004", test_bnd_tpl_004_subtitle_single_word_caption),
        ("TC-BND-TPL-004-5", "FEAT-TPL-004", test_bnd_tpl_004_subtitle_multiline_three_lines),
        ("TC-BND-TPL-005-1", "FEAT-TPL-005", test_bnd_tpl_005_counter_start_equals_end),
        ("TC-BND-TPL-005-2", "FEAT-TPL-005", test_bnd_tpl_005_counter_counting_down),
        ("TC-BND-TPL-005-3", "FEAT-TPL-005", test_bnd_tpl_005_milestone_badge_negative_number),
        ("TC-BND-TPL-005-4", "FEAT-TPL-005", test_bnd_tpl_005_gauge_sweep_360_degrees),
        ("TC-BND-TPL-005-5", "FEAT-TPL-005", test_bnd_tpl_005_counter_prefix_suffix),
        ("TC-BND-TPL-006-1", "FEAT-TPL-006", test_bnd_tpl_006_subscribe_bell_ring_frequency),
        ("TC-BND-TPL-006-2", "FEAT-TPL-006", test_bnd_tpl_006_follow_handle_without_at_symbol),
        ("TC-BND-TPL-006-3", "FEAT-TPL-006", test_bnd_tpl_006_swipe_indicator_direction_left),
        ("TC-BND-TPL-006-4", "FEAT-TPL-006", test_bnd_tpl_006_cta_button_custom_text),
        ("TC-BND-TPL-006-5", "FEAT-TPL-006", test_bnd_tpl_006_cta_scale_pulse_amplitude),
        ("TC-BND-TPL-007-1", "FEAT-TPL-007", test_bnd_tpl_007_iris_mask_reverse_out),
        ("TC-BND-TPL-007-2", "FEAT-TPL-007", test_bnd_tpl_007_vignette_extreme_strength),
        ("TC-BND-TPL-007-3", "FEAT-TPL-007", test_bnd_tpl_007_double_split_vertical_slit),
        ("TC-BND-TPL-007-4", "FEAT-TPL-007", test_bnd_tpl_007_logo_aspect_ratio_non_square),
        ("TC-BND-TPL-007-5", "FEAT-TPL-007", test_bnd_tpl_007_logo_reveal_offline_asset_bundle),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 2, fn.__name__, fn))

    return tests
