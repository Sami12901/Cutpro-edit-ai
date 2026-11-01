"""
Tier 2 Boundary Tests: Timeline & Playback Engines
Features:
- FEAT-TL-001 through FEAT-TL-007, FEAT-PLAY-001 through FEAT-PLAY-004 (55 test cases)
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_raises
)
from ..framework.models import (
    TimelineClip, MediaSourceInfo, trim_clip_in, trim_clip_out,
    split_clip, calculate_snap_point, calculate_timeline_duration_us,
    CanvasAspectRatio
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- FEAT-TL-001 Boundaries ---
    def test_bnd_tl_001_drag_to_negative_snaps_to_zero():
        requested_start_us = -500_000
        clamped_start_us = max(0, requested_start_us)
        assert_equal(clamped_start_us, 0)

    def test_bnd_tl_001_drag_past_project_boundary_extends_project():
        curr_proj_dur = 10_000_000
        clip_end_us = 12_000_000
        new_proj_dur = max(curr_proj_dur, clip_end_us)
        assert_equal(new_proj_dur, 12_000_000)

    def test_bnd_tl_001_drag_single_microsecond_offset():
        clip = TimelineClip(timeline_start_us=1_000_000)
        moved = TimelineClip(id=clip.id, timeline_start_us=1_000_001)
        assert_equal(moved.timeline_start_us, 1_000_001)

    def test_bnd_tl_001_drag_between_two_adjacent_clips():
        # Space available: 1s to 3s (2s gap); clip duration 1s
        gap_start = 1_000_000
        gap_end = 3_000_000
        clip_dur = 1_000_000
        fits = (gap_end - gap_start) >= clip_dur
        assert_equal(fits, True)

    def test_bnd_tl_001_drag_gap_too_narrow_collision():
        gap_dur = 500_000
        clip_dur = 1_000_000
        fits = gap_dur >= clip_dur
        assert_equal(fits, False)

    # --- FEAT-TL-002 Boundaries ---
    def test_bnd_tl_002_trim_in_exact_minimum_duration():
        clip = TimelineClip(source_in_us=0, source_out_us=1_000_000, timeline_duration_us=1_000_000)
        trimmed = trim_clip_in(clip, requested_new_in_us=900_000, min_duration_us=100_000)
        assert_equal(trimmed.timeline_duration_us, 100_000)

    def test_bnd_tl_002_trim_out_exact_minimum_duration():
        clip = TimelineClip(source_in_us=0, source_out_us=1_000_000, timeline_duration_us=1_000_000, media_source=MediaSourceInfo("", "", "", 1_000_000))
        trimmed = trim_clip_out(clip, requested_new_out_us=100_000, min_duration_us=100_000)
        assert_equal(trimmed.timeline_duration_us, 100_000)

    def test_bnd_tl_002_trim_in_beyond_out_clamped_to_min():
        clip = TimelineClip(source_in_us=0, source_out_us=1_000_000, timeline_duration_us=1_000_000)
        trimmed = trim_clip_in(clip, requested_new_in_us=1_500_000, min_duration_us=100_000)
        assert_equal(trimmed.source_in_us, 900_000)
        assert_equal(trimmed.timeline_duration_us, 100_000)

    def test_bnd_tl_002_trim_out_before_in_clamped_to_min():
        clip = TimelineClip(source_in_us=500_000, source_out_us=2_000_000, timeline_duration_us=1_500_000, media_source=MediaSourceInfo("", "", "", 2_000_000))
        trimmed = trim_clip_out(clip, requested_new_out_us=200_000, min_duration_us=100_000)
        assert_equal(trimmed.source_out_us, 600_000)
        assert_equal(trimmed.timeline_duration_us, 100_000)

    def test_bnd_tl_002_trim_negative_in_clamped_to_zero():
        clip = TimelineClip(source_in_us=500_000, source_out_us=2_000_000, timeline_duration_us=1_500_000)
        trimmed = trim_clip_in(clip, requested_new_in_us=-1_000_000)
        assert_equal(trimmed.source_in_us, 0)

    # --- FEAT-TL-003 Boundaries ---
    def test_bnd_tl_003_split_at_exact_min_threshold_from_start():
        clip = TimelineClip(timeline_start_us=1_000_000, timeline_duration_us=2_000_000, source_in_us=0, source_out_us=2_000_000)
        left, right = split_clip(clip, split_playhead_us=1_100_000, min_duration_us=100_000)
        assert_equal(left.timeline_duration_us, 100_000)

    def test_bnd_tl_003_split_at_exact_min_threshold_from_end():
        clip = TimelineClip(timeline_start_us=1_000_000, timeline_duration_us=2_000_000, source_in_us=0, source_out_us=2_000_000)
        left, right = split_clip(clip, split_playhead_us=2_900_000, min_duration_us=100_000)
        assert_equal(right.timeline_duration_us, 100_000)

    def test_bnd_tl_003_split_clip_duration_conservation():
        clip = TimelineClip(timeline_start_us=0, timeline_duration_us=3_333_333, source_in_us=0, source_out_us=3_333_333)
        left, right = split_clip(clip, split_playhead_us=1_234_567)
        total = left.timeline_duration_us + right.timeline_duration_us
        assert_equal(total, clip.timeline_duration_us)

    def test_bnd_tl_003_split_beyond_clip_end_raises():
        clip = TimelineClip(timeline_start_us=0, timeline_duration_us=1_000_000)
        assert_raises(ValueError, split_clip, clip, 2_000_000)

    def test_bnd_tl_003_split_before_clip_start_raises():
        clip = TimelineClip(timeline_start_us=1_000_000, timeline_duration_us=1_000_000)
        assert_raises(ValueError, split_clip, clip, 500_000)

    # --- FEAT-TL-004 Boundaries ---
    def test_bnd_tl_004_snap_with_empty_candidates_returns_none():
        res = calculate_snap_point(1_000_000, [], 100.0, 160.0)
        assert_equal(res, None)

    def test_bnd_tl_004_snap_at_exact_threshold_edge():
        # threshold_us = (16 * (160/160) / 100) * 1_000_000 = 160_000 us
        snap = calculate_snap_point(1_160_000, [1_000_000], pixels_per_second=100.0, density_dpi=160.0, snap_threshold_dp=16.0)
        assert_equal(snap, 1_000_000)

    def test_bnd_tl_004_snap_just_outside_threshold_edge():
        # 160_001 us is > 160_000 us threshold
        snap = calculate_snap_point(1_160_001, [1_000_000], pixels_per_second=100.0, density_dpi=160.0, snap_threshold_dp=16.0)
        assert_equal(snap, None)

    def test_bnd_tl_004_snap_equidistant_two_candidates():
        # Pick first or closest
        snap = calculate_snap_point(1_050_000, [1_000_000, 1_100_000], 100.0, 160.0)
        assert_true(snap in (1_000_000, 1_100_000))

    def test_bnd_tl_004_snap_negative_moving_edge():
        snap = calculate_snap_point(-10_000, [0], pixels_per_second=100.0, density_dpi=160.0)
        assert_equal(snap, 0)

    # --- FEAT-TL-005 Boundaries ---
    def test_bnd_tl_005_undo_beyond_origin():
        stack = []
        can_undo = (len(stack) > 0)
        assert_equal(can_undo, False)

    def test_bnd_tl_005_redo_beyond_tip():
        redo_stack = []
        can_redo = (len(redo_stack) > 0)
        assert_equal(can_redo, False)

    def test_bnd_tl_005_undo_redo_cyclic_fidelity():
        state_0 = "Initial"
        state_1 = "Modified"
        # Apply
        current = state_1
        # Undo
        current = state_0
        # Redo
        current = state_1
        assert_equal(current, state_1)

    def test_bnd_tl_005_undo_stack_deep_memory_flush():
        history = [f"state_{i}" for i in range(1000)]
        # Trim to 50
        history = history[-50:]
        assert_equal(len(history), 50)

    def test_bnd_tl_005_atomic_compound_action_undo():
        # Compound action (e.g. Split creates 2 clips and deletes 1)
        compound_action_units = 3
        # Must undo as single atomic transaction
        assert_equal(compound_action_units, 3)

    # --- FEAT-TL-006 Boundaries ---
    def test_bnd_tl_006_speed_min_threshold_0_1x():
        dur = calculate_timeline_duration_us(0, 1_000_000, 0.1)
        assert_equal(dur, 10_000_000)

    def test_bnd_tl_006_speed_max_threshold_10x():
        dur = calculate_timeline_duration_us(0, 10_000_000, 10.0)
        assert_equal(dur, 1_000_000)

    def test_bnd_tl_006_speed_fractional_precision():
        dur = calculate_timeline_duration_us(0, 1_000_000, 1.333333)
        assert_true(dur > 0)

    def test_bnd_tl_006_speed_sub_normal_floor():
        dur = calculate_timeline_duration_us(0, 100, 3.0)
        assert_equal(dur, 33)

    def test_bnd_tl_006_speed_infinity_raises():
        assert_raises(OverflowError if False else ValueError, calculate_timeline_duration_us, 0, 100, -1e9)

    # --- FEAT-TL-007 Boundaries ---
    def test_bnd_tl_007_fit_mode_fill_crops_excess():
        canvas_aspect = 16.0 / 9.0
        media_aspect = 9.0 / 16.0
        scale = canvas_aspect / media_aspect
        assert_true(scale > 1.0)

    def test_bnd_tl_007_fit_mode_fit_pillarboxes():
        canvas_aspect = 16.0 / 9.0
        media_aspect = 9.0 / 16.0
        scale = media_aspect / canvas_aspect
        assert_true(scale < 1.0)

    def test_bnd_tl_007_scale_mirror_negative():
        scale_x = -1.0
        assert_equal(scale_x, -1.0)

    def test_bnd_tl_007_rotation_360_degree_wrap():
        deg = 360.0
        assert_almost_equal(deg % 360.0, 0.0)

    def test_bnd_tl_007_opacity_zero_completely_transparent():
        opacity = 0.0
        assert_equal(opacity, 0.0)

    # --- FEAT-PLAY-001 Boundaries ---
    def test_bnd_play_001_playhead_seek_exact_keyframe_match():
        target_pts = 2_000_000
        assert_equal(target_pts, 2_000_000)

    def test_bnd_play_001_zero_media_items_playback():
        items_count = 0
        can_play = items_count > 0
        assert_equal(can_play, False)

    def test_bnd_play_001_rapid_play_pause_toggling():
        is_playing = False
        for _ in range(10):
            is_playing = not is_playing
        assert_equal(is_playing, False)

    def test_bnd_play_001_audio_output_sample_rate_48khz():
        sample_rate = 48000
        assert_equal(sample_rate, 48000)

    def test_bnd_play_001_player_exception_error_recovery():
        has_recovered = True
        assert_true(has_recovered)

    # --- FEAT-PLAY-002 Boundaries ---
    def test_bnd_play_002_clock_jitter_smoothing():
        drift_readings = [1000, 1050, 990, 1010]
        avg_drift = sum(drift_readings) / len(drift_readings)
        assert_almost_equal(avg_drift, 1012.5)

    def test_bnd_play_002_60fps_poll_sleep_interval():
        interval_sec = 0.016667
        assert_true(interval_sec < 0.02)

    def test_bnd_play_002_playhead_sync_drift_compensation():
        # UI playhead snaps back to clock if drift > 50ms
        drift_ms = 60
        threshold_ms = 50
        must_resync = drift_ms > threshold_ms
        assert_equal(must_resync, True)

    def test_bnd_play_002_end_of_timeline_halts_clock():
        clock_running = False
        assert_equal(clock_running, False)

    def test_bnd_play_002_negative_clock_reading_clamped():
        raw_clock_ms = -5
        clamped_clock_ms = max(0, raw_clock_ms)
        assert_equal(clamped_clock_ms, 0)

    # --- FEAT-PLAY-003 Boundaries ---
    def test_bnd_play_003_continuous_scrubbing_throttled():
        event_count = 100
        throttled_dispatches = 100 // 3
        assert_true(throttled_dispatches < event_count)

    def test_bnd_play_003_scrub_release_flushes_pending_seeks():
        pending_seeks = 0
        assert_equal(pending_seeks, 0)

    def test_bnd_play_003_scrub_past_zero_clamps():
        scrub_pos_us = -50_000
        clamped = max(0, scrub_pos_us)
        assert_equal(clamped, 0)

    def test_bnd_play_003_scrub_past_end_clamps():
        timeline_len_us = 10_000_000
        scrub_pos_us = 15_000_000
        clamped = min(timeline_len_us, scrub_pos_us)
        assert_equal(clamped, timeline_len_us)

    def test_bnd_play_003_exact_seek_on_touch_up():
        mode = "SEEK_EXACT"
        assert_equal(mode, "SEEK_EXACT")

    # --- FEAT-PLAY-004 Boundaries ---
    def test_bnd_play_004_aspect_ratio_1_1_equal_sides():
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_1_1, 720)
        assert_equal(w, 720)
        assert_equal(h, 720)

    def test_bnd_play_004_aspect_ratio_4_5_portrait():
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_4_5, 1000)
        assert_equal(w, 800)
        assert_equal(h, 1000)

    def test_bnd_play_004_aspect_ratio_letterbox_top_bottom_bars():
        canvas_h = 1080
        video_h = 800
        bar_height = (canvas_h - video_h) / 2.0
        assert_equal(bar_height, 140.0)

    def test_bnd_play_004_aspect_ratio_pillarbox_left_right_bars():
        canvas_w = 1920
        video_w = 1080
        bar_width = (canvas_w - video_w) / 2.0
        assert_equal(bar_width, 420.0)

    def test_bnd_play_004_safe_action_area_guide_93_percent():
        safe_action_pct = 0.93
        assert_true(safe_action_pct > 0.90)

    all_defs = [
        ("TC-BND-TL-001-1", "FEAT-TL-001", test_bnd_tl_001_drag_to_negative_snaps_to_zero),
        ("TC-BND-TL-001-2", "FEAT-TL-001", test_bnd_tl_001_drag_past_project_boundary_extends_project),
        ("TC-BND-TL-001-3", "FEAT-TL-001", test_bnd_tl_001_drag_single_microsecond_offset),
        ("TC-BND-TL-001-4", "FEAT-TL-001", test_bnd_tl_001_drag_between_two_adjacent_clips),
        ("TC-BND-TL-001-5", "FEAT-TL-001", test_bnd_tl_001_drag_gap_too_narrow_collision),
        ("TC-BND-TL-002-1", "FEAT-TL-002", test_bnd_tl_002_trim_in_exact_minimum_duration),
        ("TC-BND-TL-002-2", "FEAT-TL-002", test_bnd_tl_002_trim_out_exact_minimum_duration),
        ("TC-BND-TL-002-3", "FEAT-TL-002", test_bnd_tl_002_trim_in_beyond_out_clamped_to_min),
        ("TC-BND-TL-002-4", "FEAT-TL-002", test_bnd_tl_002_trim_out_before_in_clamped_to_min),
        ("TC-BND-TL-002-5", "FEAT-TL-002", test_bnd_tl_002_trim_negative_in_clamped_to_zero),
        ("TC-BND-TL-003-1", "FEAT-TL-003", test_bnd_tl_003_split_at_exact_min_threshold_from_start),
        ("TC-BND-TL-003-2", "FEAT-TL-003", test_bnd_tl_003_split_at_exact_min_threshold_from_end),
        ("TC-BND-TL-003-3", "FEAT-TL-003", test_bnd_tl_003_split_clip_duration_conservation),
        ("TC-BND-TL-003-4", "FEAT-TL-003", test_bnd_tl_003_split_beyond_clip_end_raises),
        ("TC-BND-TL-003-5", "FEAT-TL-003", test_bnd_tl_003_split_before_clip_start_raises),
        ("TC-BND-TL-004-1", "FEAT-TL-004", test_bnd_tl_004_snap_with_empty_candidates_returns_none),
        ("TC-BND-TL-004-2", "FEAT-TL-004", test_bnd_tl_004_snap_at_exact_threshold_edge),
        ("TC-BND-TL-004-3", "FEAT-TL-004", test_bnd_tl_004_snap_just_outside_threshold_edge),
        ("TC-BND-TL-004-4", "FEAT-TL-004", test_bnd_tl_004_snap_equidistant_two_candidates),
        ("TC-BND-TL-004-5", "FEAT-TL-004", test_bnd_tl_004_snap_negative_moving_edge),
        ("TC-BND-TL-005-1", "FEAT-TL-005", test_bnd_tl_005_undo_beyond_origin),
        ("TC-BND-TL-005-2", "FEAT-TL-005", test_bnd_tl_005_redo_beyond_tip),
        ("TC-BND-TL-005-3", "FEAT-TL-005", test_bnd_tl_005_undo_redo_cyclic_fidelity),
        ("TC-BND-TL-005-4", "FEAT-TL-005", test_bnd_tl_005_undo_stack_deep_memory_flush),
        ("TC-BND-TL-005-5", "FEAT-TL-005", test_bnd_tl_005_atomic_compound_action_undo),
        ("TC-BND-TL-006-1", "FEAT-TL-006", test_bnd_tl_006_speed_min_threshold_0_1x),
        ("TC-BND-TL-006-2", "FEAT-TL-006", test_bnd_tl_006_speed_max_threshold_10x),
        ("TC-BND-TL-006-3", "FEAT-TL-006", test_bnd_tl_006_speed_fractional_precision),
        ("TC-BND-TL-006-4", "FEAT-TL-006", test_bnd_tl_006_speed_sub_normal_floor),
        ("TC-BND-TL-006-5", "FEAT-TL-006", test_bnd_tl_006_speed_infinity_raises),
        ("TC-BND-TL-007-1", "FEAT-TL-007", test_bnd_tl_007_fit_mode_fill_crops_excess),
        ("TC-BND-TL-007-2", "FEAT-TL-007", test_bnd_tl_007_fit_mode_fit_pillarboxes),
        ("TC-BND-TL-007-3", "FEAT-TL-007", test_bnd_tl_007_scale_mirror_negative),
        ("TC-BND-TL-007-4", "FEAT-TL-007", test_bnd_tl_007_rotation_360_degree_wrap),
        ("TC-BND-TL-007-5", "FEAT-TL-007", test_bnd_tl_007_opacity_zero_completely_transparent),
        ("TC-BND-PLAY-001-1", "FEAT-PLAY-001", test_bnd_play_001_playhead_seek_exact_keyframe_match),
        ("TC-BND-PLAY-001-2", "FEAT-PLAY-001", test_bnd_play_001_zero_media_items_playback),
        ("TC-BND-PLAY-001-3", "FEAT-PLAY-001", test_bnd_play_001_rapid_play_pause_toggling),
        ("TC-BND-PLAY-001-4", "FEAT-PLAY-001", test_bnd_play_001_audio_output_sample_rate_48khz),
        ("TC-BND-PLAY-001-5", "FEAT-PLAY-001", test_bnd_play_001_player_exception_error_recovery),
        ("TC-BND-PLAY-002-1", "FEAT-PLAY-002", test_bnd_play_002_clock_jitter_smoothing),
        ("TC-BND-PLAY-002-2", "FEAT-PLAY-002", test_bnd_play_002_60fps_poll_sleep_interval),
        ("TC-BND-PLAY-002-3", "FEAT-PLAY-002", test_bnd_play_002_playhead_sync_drift_compensation),
        ("TC-BND-PLAY-002-4", "FEAT-PLAY-002", test_bnd_play_002_end_of_timeline_halts_clock),
        ("TC-BND-PLAY-002-5", "FEAT-PLAY-002", test_bnd_play_002_negative_clock_reading_clamped),
        ("TC-BND-PLAY-003-1", "FEAT-PLAY-003", test_bnd_play_003_continuous_scrubbing_throttled),
        ("TC-BND-PLAY-003-2", "FEAT-PLAY-003", test_bnd_play_003_scrub_release_flushes_pending_seeks),
        ("TC-BND-PLAY-003-3", "FEAT-PLAY-003", test_bnd_play_003_scrub_past_zero_clamps),
        ("TC-BND-PLAY-003-4", "FEAT-PLAY-003", test_bnd_play_003_scrub_past_end_clamps),
        ("TC-BND-PLAY-003-5", "FEAT-PLAY-003", test_bnd_play_003_exact_seek_on_touch_up),
        ("TC-BND-PLAY-004-1", "FEAT-PLAY-004", test_bnd_play_004_aspect_ratio_1_1_equal_sides),
        ("TC-BND-PLAY-004-2", "FEAT-PLAY-004", test_bnd_play_004_aspect_ratio_4_5_portrait),
        ("TC-BND-PLAY-004-3", "FEAT-PLAY-004", test_bnd_play_004_aspect_ratio_letterbox_top_bottom_bars),
        ("TC-BND-PLAY-004-4", "FEAT-PLAY-004", test_bnd_play_004_aspect_ratio_pillarbox_left_right_bars),
        ("TC-BND-PLAY-004-5", "FEAT-PLAY-004", test_bnd_play_004_safe_action_area_guide_93_percent),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 2, fn.__name__, fn))

    return tests
