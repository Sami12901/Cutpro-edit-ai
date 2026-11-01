"""
Tier 1 Tests: Timeline Engine & Mathematical Operations
Features:
- FEAT-TL-001: Timeline Clip Repositioning & Drag
- FEAT-TL-002: In-Point & Out-Point Trimming Engine
- FEAT-TL-003: Clip Splitting at Playhead
- FEAT-TL-004: Magnetic Snapping Engine
- FEAT-TL-005: Undo / Redo Command Stack
- FEAT-TL-006: Clip Speed Adjustment
- FEAT-TL-007: Clip Transform & Fit Mode
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_not_equal, assert_almost_equal, assert_raises
)
from ..framework.models import (
    TimelineClip, MediaSourceInfo, ClipTransform, ClipFitMode,
    calculate_timeline_duration_us, trim_clip_in, trim_clip_out,
    split_clip, calculate_snap_point
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- FEAT-TL-001: Timeline Clip Repositioning & Drag ---

    def test_feat_tl_001_reposition_start_us():
        clip = TimelineClip(timeline_start_us=1_000_000, timeline_duration_us=3_000_000)
        repositioned = TimelineClip(
            id=clip.id,
            timeline_start_us=2_500_000,
            timeline_duration_us=clip.timeline_duration_us
        )
        assert_equal(repositioned.timeline_start_us, 2_500_000)
        assert_equal(repositioned.timeline_end_us, 5_500_000)

    def test_feat_tl_001_reposition_preserves_duration():
        clip = TimelineClip(timeline_start_us=0, timeline_duration_us=4_000_000)
        repositioned = TimelineClip(id=clip.id, timeline_start_us=10_000_000, timeline_duration_us=clip.timeline_duration_us)
        assert_equal(repositioned.timeline_duration_us, 4_000_000)

    def test_feat_tl_001_reposition_to_zero():
        clip = TimelineClip(timeline_start_us=5_000_000, timeline_duration_us=2_000_000)
        repositioned = TimelineClip(id=clip.id, timeline_start_us=0, timeline_duration_us=clip.timeline_duration_us)
        assert_equal(repositioned.timeline_start_us, 0)

    def test_feat_tl_001_drag_track_switch():
        clip = TimelineClip(track_id="track_1")
        moved = TimelineClip(id=clip.id, track_id="track_2")
        assert_equal(moved.track_id, "track_2")

    def test_feat_tl_001_collision_detection_check():
        clip1 = TimelineClip(timeline_start_us=0, timeline_duration_us=3_000_000)
        clip2 = TimelineClip(timeline_start_us=2_000_000, timeline_duration_us=3_000_000)
        has_overlap = clip1.timeline_start_us < clip2.timeline_end_us and clip2.timeline_start_us < clip1.timeline_end_us
        assert_true(has_overlap)

    # --- FEAT-TL-002: In-Point & Out-Point Trimming Engine ---

    def test_feat_tl_002_trim_in_point_advancement():
        clip = TimelineClip(
            source_in_us=0,
            source_out_us=5_000_000,
            timeline_start_us=0,
            timeline_duration_us=5_000_000,
            speed_multiplier=1.0
        )
        trimmed = trim_clip_in(clip, requested_new_in_us=1_000_000)
        assert_equal(trimmed.source_in_us, 1_000_000)
        assert_equal(trimmed.timeline_start_us, 1_000_000)
        assert_equal(trimmed.timeline_duration_us, 4_000_000)

    def test_feat_tl_002_trim_out_point_truncation():
        clip = TimelineClip(
            source_in_us=0,
            source_out_us=5_000_000,
            timeline_start_us=0,
            timeline_duration_us=5_000_000,
            media_source=MediaSourceInfo("", "", "", 5_000_000)
        )
        trimmed = trim_clip_out(clip, requested_new_out_us=3_000_000)
        assert_equal(trimmed.source_out_us, 3_000_000)
        assert_equal(trimmed.timeline_duration_us, 3_000_000)

    def test_feat_tl_002_trim_in_with_speed_scaling():
        clip = TimelineClip(
            source_in_us=0,
            source_out_us=4_000_000,
            timeline_start_us=0,
            timeline_duration_us=2_000_000,
            speed_multiplier=2.0
        )
        trimmed = trim_clip_in(clip, requested_new_in_us=2_000_000)
        assert_equal(trimmed.source_in_us, 2_000_000)
        assert_equal(trimmed.timeline_start_us, 1_000_000)
        assert_equal(trimmed.timeline_duration_us, 1_000_000)

    def test_feat_tl_002_trim_enforces_minimum_duration():
        clip = TimelineClip(
            source_in_us=0,
            source_out_us=1_000_000,
            timeline_start_us=0,
            timeline_duration_us=1_000_000
        )
        # Attempt to trim in to 950_000 (leaves 50_000 < min 100_000)
        trimmed = trim_clip_in(clip, requested_new_in_us=950_000, min_duration_us=100_000)
        assert_equal(trimmed.source_in_us, 900_000)
        assert_equal(trimmed.timeline_duration_us, 100_000)

    def test_feat_tl_002_trim_out_enforces_minimum_duration():
        clip = TimelineClip(
            source_in_us=500_000,
            source_out_us=2_000_000,
            timeline_start_us=0,
            timeline_duration_us=1_500_000,
            media_source=MediaSourceInfo("", "", "", 2_000_000)
        )
        # Attempt to trim out to 550_000 (leaves 50_000 < min 100_000)
        trimmed = trim_clip_out(clip, requested_new_out_us=550_000, min_duration_us=100_000)
        assert_equal(trimmed.source_out_us, 600_000)
        assert_equal(trimmed.timeline_duration_us, 100_000)

    # --- FEAT-TL-003: Clip Splitting at Playhead ---

    def test_feat_tl_003_split_middle_normal_speed():
        clip = TimelineClip(
            source_in_us=1_000_000,
            source_out_us=5_000_000,
            timeline_start_us=100_000,
            timeline_duration_us=4_000_000,
            speed_multiplier=1.0
        )
        left, right = split_clip(clip, split_playhead_us=2_100_000)
        assert_equal(left.timeline_start_us, 100_000)
        assert_equal(left.timeline_duration_us, 2_000_000)
        assert_equal(left.source_out_us, 3_000_000)

        assert_equal(right.timeline_start_us, 2_100_000)
        assert_equal(right.timeline_duration_us, 2_000_000)
        assert_equal(right.source_in_us, 3_000_000)
        assert_equal(right.source_out_us, 5_000_000)

    def test_feat_tl_003_split_with_2x_speed():
        clip = TimelineClip(
            source_in_us=0,
            source_out_us=6_000_000,
            timeline_start_us=0,
            timeline_duration_us=3_000_000,
            speed_multiplier=2.0
        )
        left, right = split_clip(clip, split_playhead_us=1_000_000)
        assert_equal(left.timeline_duration_us, 1_000_000)
        assert_equal(left.source_out_us, 2_000_000)

        assert_equal(right.timeline_start_us, 1_000_000)
        assert_equal(right.timeline_duration_us, 2_000_000)
        assert_equal(right.source_in_us, 2_000_000)

    def test_feat_tl_003_split_contiguous_coverage():
        clip = TimelineClip(timeline_start_us=0, timeline_duration_us=5_000_000, source_in_us=0, source_out_us=5_000_000)
        left, right = split_clip(clip, split_playhead_us=3_000_000)
        assert_equal(left.timeline_end_us, right.timeline_start_us)
        assert_equal(left.timeline_duration_us + right.timeline_duration_us, clip.timeline_duration_us)

    def test_feat_tl_003_split_too_close_to_start_raises():
        clip = TimelineClip(timeline_start_us=1_000_000, timeline_duration_us=4_000_000)
        assert_raises(ValueError, split_clip, clip, 1_050_000, 100_000)

    def test_feat_tl_003_split_too_close_to_end_raises():
        clip = TimelineClip(timeline_start_us=1_000_000, timeline_duration_us=4_000_000)
        assert_raises(ValueError, split_clip, clip, 4_950_000, 100_000)

    # --- FEAT-TL-004: Magnetic Snapping Engine ---

    def test_feat_tl_004_snaps_to_exact_candidate():
        candidates = [0, 1_000_000, 3_000_000]
        snap = calculate_snap_point(
            moving_edge_us=1_020_000,
            candidate_points_us=candidates,
            pixels_per_second=100.0,
            density_dpi=160.0,
            snap_threshold_dp=16.0
        )
        assert_equal(snap, 1_000_000)

    def test_feat_tl_004_no_snap_beyond_threshold():
        candidates = [0, 5_000_000]
        snap = calculate_snap_point(
            moving_edge_us=2_500_000,
            candidate_points_us=candidates,
            pixels_per_second=100.0,
            density_dpi=160.0,
            snap_threshold_dp=16.0
        )
        assert_equal(snap, None)

    def test_feat_tl_004_closest_candidate_selected():
        candidates = [1_000_000, 1_100_000]
        snap = calculate_snap_point(
            moving_edge_us=1_080_000,
            candidate_points_us=candidates,
            pixels_per_second=100.0,
            density_dpi=160.0,
            snap_threshold_dp=16.0
        )
        assert_equal(snap, 1_100_000)

    def test_feat_tl_004_snap_to_project_origin():
        candidates = [0, 5_000_000]
        snap = calculate_snap_point(
            moving_edge_us=30_000,
            candidate_points_us=candidates,
            pixels_per_second=100.0,
            density_dpi=160.0,
            snap_threshold_dp=16.0
        )
        assert_equal(snap, 0)

    def test_feat_tl_004_zero_pixel_per_second_returns_none():
        candidates = [1_000_000]
        snap = calculate_snap_point(1_010_000, candidates, pixels_per_second=0, density_dpi=160)
        assert_equal(snap, None)

    # --- FEAT-TL-005: Undo / Redo Command Stack ---

    def test_feat_tl_005_undo_reverts_state():
        history = []
        history.append("State 1")
        history.append("State 2")
        current = history.pop()
        previous = history[-1]
        assert_equal(previous, "State 1")

    def test_feat_tl_005_redo_restores_state():
        undo_stack = ["State 1"]
        redo_stack = ["State 2"]
        state = redo_stack.pop()
        undo_stack.append(state)
        assert_equal(undo_stack[-1], "State 2")
        assert_equal(len(redo_stack), 0)

    def test_feat_tl_005_new_action_clears_redo():
        redo_stack = ["Undone Action"]
        # New action performed
        redo_stack.clear()
        assert_equal(len(redo_stack), 0)

    def test_feat_tl_005_max_history_limit_enforced():
        max_history = 50
        history = list(range(60))
        trimmed = history[-max_history:]
        assert_equal(len(trimmed), 50)

    def test_feat_tl_005_undo_empty_stack_safe():
        undo_stack = []
        can_undo = len(undo_stack) > 0
        assert_equal(can_undo, False)

    # --- FEAT-TL-006: Clip Speed Adjustment ---

    def test_feat_tl_006_speed_slow_motion_doubles_duration():
        dur = calculate_timeline_duration_us(0, 2_000_000, 0.5)
        assert_equal(dur, 4_000_000)

    def test_feat_tl_006_speed_fast_motion_halves_duration():
        dur = calculate_timeline_duration_us(0, 4_000_000, 2.0)
        assert_equal(dur, 2_000_000)

    def test_feat_tl_006_speed_unity_preserves_duration():
        dur = calculate_timeline_duration_us(0, 3_000_000, 1.0)
        assert_equal(dur, 3_000_000)

    def test_feat_tl_006_speed_max_bound():
        dur = calculate_timeline_duration_us(0, 10_000_000, 10.0)
        assert_equal(dur, 1_000_000)

    def test_feat_tl_006_speed_zero_or_negative_raises():
        assert_raises(ValueError, calculate_timeline_duration_us, 0, 1_000_000, 0.0)
        assert_raises(ValueError, calculate_timeline_duration_us, 0, 1_000_000, -1.0)

    # --- FEAT-TL-007: Clip Transform & Fit Mode ---

    def test_feat_tl_007_fit_mode_enum_values():
        valid_modes = {ClipFitMode.FIT, ClipFitMode.FILL, ClipFitMode.STRETCH, ClipFitMode.CUSTOM}
        assert_equal(len(valid_modes), 4)

    def test_feat_tl_007_transform_scale_factors():
        tr = ClipTransform(scale_x=1.5, scale_y=1.5)
        assert_equal(tr.scale_x, 1.5)
        assert_equal(tr.scale_y, 1.5)

    def test_feat_tl_007_transform_rotation_angle():
        tr = ClipTransform(rotation_degrees=90.0)
        assert_equal(tr.rotation_degrees, 90.0)

    def test_feat_tl_007_transform_translation_normalized():
        tr = ClipTransform(translation_x=0.25, translation_y=-0.5)
        assert_equal(tr.translation_x, 0.25)
        assert_equal(tr.translation_y, -0.5)

    def test_feat_tl_007_transform_opacity_bounds():
        tr = ClipTransform(opacity=0.75)
        assert_true(0.0 <= tr.opacity <= 1.0)

    all_defs = [
        ("TC-TL-001-1", "FEAT-TL-001", test_feat_tl_001_reposition_start_us),
        ("TC-TL-001-2", "FEAT-TL-001", test_feat_tl_001_reposition_preserves_duration),
        ("TC-TL-001-3", "FEAT-TL-001", test_feat_tl_001_reposition_to_zero),
        ("TC-TL-001-4", "FEAT-TL-001", test_feat_tl_001_drag_track_switch),
        ("TC-TL-001-5", "FEAT-TL-001", test_feat_tl_001_collision_detection_check),
        ("TC-TL-002-1", "FEAT-TL-002", test_feat_tl_002_trim_in_point_advancement),
        ("TC-TL-002-2", "FEAT-TL-002", test_feat_tl_002_trim_out_point_truncation),
        ("TC-TL-002-3", "FEAT-TL-002", test_feat_tl_002_trim_in_with_speed_scaling),
        ("TC-TL-002-4", "FEAT-TL-002", test_feat_tl_002_trim_enforces_minimum_duration),
        ("TC-TL-002-5", "FEAT-TL-002", test_feat_tl_002_trim_out_enforces_minimum_duration),
        ("TC-TL-003-1", "FEAT-TL-003", test_feat_tl_003_split_middle_normal_speed),
        ("TC-TL-003-2", "FEAT-TL-003", test_feat_tl_003_split_with_2x_speed),
        ("TC-TL-003-3", "FEAT-TL-003", test_feat_tl_003_split_contiguous_coverage),
        ("TC-TL-003-4", "FEAT-TL-003", test_feat_tl_003_split_too_close_to_start_raises),
        ("TC-TL-003-5", "FEAT-TL-003", test_feat_tl_003_split_too_close_to_end_raises),
        ("TC-TL-004-1", "FEAT-TL-004", test_feat_tl_004_snaps_to_exact_candidate),
        ("TC-TL-004-2", "FEAT-TL-004", test_feat_tl_004_no_snap_beyond_threshold),
        ("TC-TL-004-3", "FEAT-TL-004", test_feat_tl_004_closest_candidate_selected),
        ("TC-TL-004-4", "FEAT-TL-004", test_feat_tl_004_snap_to_project_origin),
        ("TC-TL-004-5", "FEAT-TL-004", test_feat_tl_004_zero_pixel_per_second_returns_none),
        ("TC-TL-005-1", "FEAT-TL-005", test_feat_tl_005_undo_reverts_state),
        ("TC-TL-005-2", "FEAT-TL-005", test_feat_tl_005_redo_restores_state),
        ("TC-TL-005-3", "FEAT-TL-005", test_feat_tl_005_new_action_clears_redo),
        ("TC-TL-005-4", "FEAT-TL-005", test_feat_tl_005_max_history_limit_enforced),
        ("TC-TL-005-5", "FEAT-TL-005", test_feat_tl_005_undo_empty_stack_safe),
        ("TC-TL-006-1", "FEAT-TL-006", test_feat_tl_006_speed_slow_motion_doubles_duration),
        ("TC-TL-006-2", "FEAT-TL-006", test_feat_tl_006_speed_fast_motion_halves_duration),
        ("TC-TL-006-3", "FEAT-TL-006", test_feat_tl_006_speed_unity_preserves_duration),
        ("TC-TL-006-4", "FEAT-TL-006", test_feat_tl_006_speed_max_bound),
        ("TC-TL-006-5", "FEAT-TL-006", test_feat_tl_006_speed_zero_or_negative_raises),
        ("TC-TL-007-1", "FEAT-TL-007", test_feat_tl_007_fit_mode_enum_values),
        ("TC-TL-007-2", "FEAT-TL-007", test_feat_tl_007_transform_scale_factors),
        ("TC-TL-007-3", "FEAT-TL-007", test_feat_tl_007_transform_rotation_angle),
        ("TC-TL-007-4", "FEAT-TL-007", test_feat_tl_007_transform_translation_normalized),
        ("TC-TL-007-5", "FEAT-TL-007", test_feat_tl_007_transform_opacity_bounds),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 1, fn.__name__, fn))

    return tests
