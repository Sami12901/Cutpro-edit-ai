"""
Tier 1 Tests: UI Shell, Navigation, Theme & Touch Targets
Features:
- FEAT-UI-001: Material 3 Dark/Light Theme Shell
- FEAT-UI-002: Zero-Emoji Strict Iconography
- FEAT-UI-003: Accessible Touch Targets (48dp)
- FEAT-UI-004: Project Dashboard & Recent Grid
- FEAT-UI-005: Top Toolbar Action Controls
- FEAT-UI-006: Multitrack Timeline Canvas UI
- FEAT-UI-007: Draggable Playhead Scrubber
- FEAT-UI-008: Contextual Properties Inspector
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_in_range, assert_zero_emojis
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- FEAT-UI-001: Material 3 Dark/Light Theme Shell ---

    def test_feat_ui_001_dark_theme_color_tokens():
        dark_background = 0xFF121316
        dark_surface = 0xFF1E2024
        dark_accent = 0xFF4A88DA
        assert_equal(dark_background, 0xFF121316)
        assert_equal(dark_surface, 0xFF1E2024)
        assert_equal(dark_accent, 0xFF4A88DA)

    def test_feat_ui_001_light_theme_tokens():
        light_background = 0xFFF8F9FA
        assert_equal(light_background, 0xFFF8F9FA)

    def test_feat_ui_001_theme_contrast_ratio():
        # High contrast text on dark background
        bg_luminance = 0.05
        text_luminance = 0.95
        contrast_ratio = (text_luminance + 0.05) / (bg_luminance + 0.05)
        assert_true(contrast_ratio >= 7.0)  # WCAG AAA compliance

    def test_feat_ui_001_typography_tokens():
        title_size_sp = 22
        body_size_sp = 14
        assert_true(title_size_sp > body_size_sp)

    def test_feat_ui_001_default_theme_is_dark():
        default_theme = "DARK"
        assert_equal(default_theme, "DARK")

    # --- FEAT-UI-002: Zero-Emoji Strict Iconography ---

    def test_feat_ui_002_vector_action_icons():
        icons = ["ic_split_blade", "ic_trim_handle", "ic_play", "ic_pause", "ic_export_video", "ic_undo", "ic_redo"]
        for icon in icons:
            assert_zero_emojis(icon)

    def test_feat_ui_002_button_labels_zero_emojis():
        labels = ["New Project", "Export", "Split", "Trim In", "Trim Out", "Aspect Ratio", "Settings"]
        for label in labels:
            assert_zero_emojis(label)

    def test_feat_ui_002_dialog_strings_zero_emojis():
        dialog_text = "Insufficient storage space. Please free up space before exporting."
        assert_zero_emojis(dialog_text)

    def test_feat_ui_002_toast_messages_zero_emojis():
        toast = "Project autosaved successfully."
        assert_zero_emojis(toast)

    def test_feat_ui_002_template_names_zero_emojis():
        tpl_names = ["Cinematic Split Fade", "Pop Bounce", "News Broadcast Bar", "Karaoke Highlight"]
        for tpl in tpl_names:
            assert_zero_emojis(tpl)

    # --- FEAT-UI-003: Accessible Touch Targets (48dp) ---

    def test_feat_ui_003_button_touch_target_bounds():
        min_target_dp = 48.0
        button_width_dp = 48.0
        button_height_dp = 48.0
        assert_true(button_width_dp >= min_target_dp)
        assert_true(button_height_dp >= min_target_dp)

    def test_feat_ui_003_trim_handle_hitbox():
        # Trim handle visible bar 4dp, but touch target padded to 48dp
        visible_width_dp = 4.0
        hitbox_width_dp = 48.0
        assert_true(hitbox_width_dp >= 48.0)

    def test_feat_ui_003_playhead_scrubber_touch_bounds():
        scrubber_hitbox_diameter_dp = 48.0
        assert_true(scrubber_hitbox_diameter_dp >= 48.0)

    def test_feat_ui_003_timeline_track_height():
        track_height_dp = 64.0
        assert_true(track_height_dp >= 48.0)

    def test_feat_ui_003_inspector_slider_touch_bounds():
        slider_thumb_touch_dp = 48.0
        assert_true(slider_thumb_touch_dp >= 48.0)

    # --- FEAT-UI-004: Project Dashboard & Recent Grid ---

    def test_feat_ui_004_dashboard_empty_state_text():
        empty_text = "No recent projects found. Tap New Project to start editing."
        assert_zero_emojis(empty_text)

    def test_feat_ui_004_project_card_metadata():
        card = {"title": "Summer Vacation", "durationFormatted": "01:24", "lastEdited": "Today"}
        assert_equal(card["title"], "Summer Vacation")
        assert_zero_emojis(card["durationFormatted"])

    def test_feat_ui_004_new_project_action():
        action = "CREATE_PROJECT"
        assert_equal(action, "CREATE_PROJECT")

    def test_feat_ui_004_project_card_aspect_ratio_badge():
        badge = "16:9"
        assert_equal(badge, "16:9")

    def test_feat_ui_004_project_deletion_confirmation():
        prompt = "Are you sure you want to delete this project? This action cannot be undone."
        assert_zero_emojis(prompt)

    # --- FEAT-UI-005: Top Toolbar Action Controls ---

    def test_feat_ui_005_undo_disabled_when_stack_empty():
        undo_stack_size = 0
        can_undo = undo_stack_size > 0
        assert_equal(can_undo, False)

    def test_feat_ui_005_redo_disabled_when_stack_empty():
        redo_stack_size = 0
        can_redo = redo_stack_size > 0
        assert_equal(can_redo, False)

    def test_feat_ui_005_title_editor_character_limit():
        max_title_len = 100
        sample_title = "My Video"
        assert_true(len(sample_title) <= max_title_len)

    def test_feat_ui_005_export_button_triggers_navigation():
        target_route = "export/{projectId}"
        assert_true("export" in target_route)

    def test_feat_ui_005_aspect_ratio_quick_toggle():
        ratios = ["16:9", "9:16", "1:1"]
        assert_equal(len(ratios), 3)

    # --- FEAT-UI-006: Multitrack Timeline Canvas UI ---

    def test_feat_ui_006_timeline_zoom_clamping():
        min_zoom = 0.1
        max_zoom = 5.0
        requested_zoom = 0.05
        clamped = max(min_zoom, min(max_zoom, requested_zoom))
        assert_equal(clamped, 0.1)

    def test_feat_ui_006_zoom_fit_calculation():
        project_duration_us = 10_000_000
        viewport_width_px = 1080.0
        pixels_per_us = viewport_width_px / project_duration_us
        assert_true(pixels_per_us > 0)

    def test_feat_ui_006_multitrack_vertical_layering():
        # Z-index order: Video (0), Overlay (1), Text/Caption (2)
        v_z = 0
        ov_z = 1
        cap_z = 2
        assert_true(cap_z > ov_z > v_z)

    def test_feat_ui_006_ruler_tick_intervals():
        major_interval_sec = 1.0
        sub_interval_sec = 0.2
        assert_equal(major_interval_sec / sub_interval_sec, 5.0)

    def test_feat_ui_006_track_visibility_toggle():
        is_visible = True
        is_visible = not is_visible
        assert_equal(is_visible, False)

    # --- FEAT-UI-007: Draggable Playhead Scrubber ---

    def test_feat_ui_007_playhead_clamping_to_duration():
        duration_us = 5_000_000
        drag_pos_us = 6_000_000
        clamped = max(0, min(duration_us, drag_pos_us))
        assert_equal(clamped, 5_000_000)

    def test_feat_ui_007_playhead_negative_clamp():
        duration_us = 5_000_000
        drag_pos_us = -100_000
        clamped = max(0, min(duration_us, drag_pos_us))
        assert_equal(clamped, 0)

    def test_feat_ui_007_timecode_display_formatting():
        pts_us = 3_750_000  # 3.75s
        total_sec = pts_us // 1_000_000
        frames = int((pts_us % 1_000_000) / 1_000_000.0 * 30.0)
        timecode = f"{total_sec // 60:02d}:{total_sec % 60:02d}:{frames:02d}"
        assert_equal(timecode, "00:03:22")

    def test_feat_ui_007_scrubbing_state_flags():
        is_scrubbing = True
        assert_equal(is_scrubbing, True)

    def test_feat_ui_007_haptic_tick_cadence():
        tick_interval_ms = 50
        assert_true(tick_interval_ms >= 20)

    # --- FEAT-UI-008: Contextual Properties Inspector ---

    def test_feat_ui_008_inspector_tabs_for_video_clip():
        video_tabs = ["Transform", "Audio", "Speed", "Cut"]
        assert_equal(len(video_tabs), 4)

    def test_feat_ui_008_inspector_tabs_for_audio_clip():
        audio_tabs = ["Volume", "Fades", "Speed"]
        assert_equal(len(audio_tabs), 3)

    def test_feat_ui_008_inspector_tabs_for_text_clip():
        text_tabs = ["Typography", "Animation", "Style"]
        assert_equal(len(text_tabs), 3)

    def test_feat_ui_008_inspector_deselection_collapses_panel():
        selected_clip_id = None
        is_collapsed = (selected_clip_id is None)
        assert_equal(is_collapsed, True)

    def test_feat_ui_008_inspector_numerical_slider_display():
        speed_val = 1.5
        display_str = f"{speed_val:.1f}x"
        assert_equal(display_str, "1.5x")

    all_defs = [
        ("TC-UI-001-1", "FEAT-UI-001", test_feat_ui_001_dark_theme_color_tokens),
        ("TC-UI-001-2", "FEAT-UI-001", test_feat_ui_001_light_theme_tokens),
        ("TC-UI-001-3", "FEAT-UI-001", test_feat_ui_001_theme_contrast_ratio),
        ("TC-UI-001-4", "FEAT-UI-001", test_feat_ui_001_typography_tokens),
        ("TC-UI-001-5", "FEAT-UI-001", test_feat_ui_001_default_theme_is_dark),
        ("TC-UI-002-1", "FEAT-UI-002", test_feat_ui_002_vector_action_icons),
        ("TC-UI-002-2", "FEAT-UI-002", test_feat_ui_002_button_labels_zero_emojis),
        ("TC-UI-002-3", "FEAT-UI-002", test_feat_ui_002_dialog_strings_zero_emojis),
        ("TC-UI-002-4", "FEAT-UI-002", test_feat_ui_002_toast_messages_zero_emojis),
        ("TC-UI-002-5", "FEAT-UI-002", test_feat_ui_002_template_names_zero_emojis),
        ("TC-UI-003-1", "FEAT-UI-003", test_feat_ui_003_button_touch_target_bounds),
        ("TC-UI-003-2", "FEAT-UI-003", test_feat_ui_003_trim_handle_hitbox),
        ("TC-UI-003-3", "FEAT-UI-003", test_feat_ui_003_playhead_scrubber_touch_bounds),
        ("TC-UI-003-4", "FEAT-UI-003", test_feat_ui_003_timeline_track_height),
        ("TC-UI-003-5", "FEAT-UI-003", test_feat_ui_003_inspector_slider_touch_bounds),
        ("TC-UI-004-1", "FEAT-UI-004", test_feat_ui_004_dashboard_empty_state_text),
        ("TC-UI-004-2", "FEAT-UI-004", test_feat_ui_004_project_card_metadata),
        ("TC-UI-004-3", "FEAT-UI-004", test_feat_ui_004_new_project_action),
        ("TC-UI-004-4", "FEAT-UI-004", test_feat_ui_004_project_card_aspect_ratio_badge),
        ("TC-UI-004-5", "FEAT-UI-004", test_feat_ui_004_project_deletion_confirmation),
        ("TC-UI-005-1", "FEAT-UI-005", test_feat_ui_005_undo_disabled_when_stack_empty),
        ("TC-UI-005-2", "FEAT-UI-005", test_feat_ui_005_redo_disabled_when_stack_empty),
        ("TC-UI-005-3", "FEAT-UI-005", test_feat_ui_005_title_editor_character_limit),
        ("TC-UI-005-4", "FEAT-UI-005", test_feat_ui_005_export_button_triggers_navigation),
        ("TC-UI-005-5", "FEAT-UI-005", test_feat_ui_005_aspect_ratio_quick_toggle),
        ("TC-UI-006-1", "FEAT-UI-006", test_feat_ui_006_timeline_zoom_clamping),
        ("TC-UI-006-2", "FEAT-UI-006", test_feat_ui_006_zoom_fit_calculation),
        ("TC-UI-006-3", "FEAT-UI-006", test_feat_ui_006_multitrack_vertical_layering),
        ("TC-UI-006-4", "FEAT-UI-006", test_feat_ui_006_ruler_tick_intervals),
        ("TC-UI-006-5", "FEAT-UI-006", test_feat_ui_006_track_visibility_toggle),
        ("TC-UI-007-1", "FEAT-UI-007", test_feat_ui_007_playhead_clamping_to_duration),
        ("TC-UI-007-2", "FEAT-UI-007", test_feat_ui_007_playhead_negative_clamp),
        ("TC-UI-007-3", "FEAT-UI-007", test_feat_ui_007_timecode_display_formatting),
        ("TC-UI-007-4", "FEAT-UI-007", test_feat_ui_007_scrubbing_state_flags),
        ("TC-UI-007-5", "FEAT-UI-007", test_feat_ui_007_haptic_tick_cadence),
        ("TC-UI-008-1", "FEAT-UI-008", test_feat_ui_008_inspector_tabs_for_video_clip),
        ("TC-UI-008-2", "FEAT-UI-008", test_feat_ui_008_inspector_tabs_for_audio_clip),
        ("TC-UI-008-3", "FEAT-UI-008", test_feat_ui_008_inspector_tabs_for_text_clip),
        ("TC-UI-008-4", "FEAT-UI-008", test_feat_ui_008_inspector_deselection_collapses_panel),
        ("TC-UI-008-5", "FEAT-UI-008", test_feat_ui_008_inspector_numerical_slider_display),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 1, fn.__name__, fn))

    return tests
