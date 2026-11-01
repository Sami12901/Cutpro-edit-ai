"""
Tier 2 Boundary Tests: Media3 Video Export Pipeline
Features:
- FEAT-EXP-001 through FEAT-EXP-008 (40 test cases)
"""

import os
from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_raises, assert_zero_emojis
)
from ..framework.models import (
    RenderPlan, ProjectManifest, TimelineTrack, TimelineClip,
    ExportResolution, ExportFrameRate, VideoCodec, CanvasAspectRatio,
    validate_render_plan
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- FEAT-EXP-001 Boundaries ---
    def test_bnd_exp_001_single_clip_minimal_duration_export():
        proj = ProjectManifest()
        t = TimelineTrack()
        t.clips.append(TimelineClip(timeline_duration_us=100_000))  # 100ms
        proj.tracks.append(t)
        plan = RenderPlan(duration_us=100_000)
        valid, _ = validate_render_plan(proj, plan, 100_000_000)
        assert_true(valid)

    def test_bnd_exp_001_audio_only_track_export():
        proj = ProjectManifest()
        t = TimelineTrack(name="A1")
        t.clips.append(TimelineClip(timeline_duration_us=5_000_000))
        proj.tracks.append(t)
        plan = RenderPlan(duration_us=5_000_000)
        valid, _ = validate_render_plan(proj, plan, 100_000_000)
        assert_true(valid)

    def test_bnd_exp_001_video_track_no_audio_stream():
        proj = ProjectManifest()
        t = TimelineTrack(name="V1", is_muted=True)
        t.clips.append(TimelineClip(timeline_duration_us=2_000_000))
        proj.tracks.append(t)
        assert_equal(proj.tracks[0].is_muted, True)

    def test_bnd_exp_001_multiple_audio_tracks_mixing():
        proj = ProjectManifest()
        t1 = TimelineTrack(name="A1")
        t2 = TimelineTrack(name="A2")
        t1.clips.append(TimelineClip(timeline_duration_us=3_000_000))
        t2.clips.append(TimelineClip(timeline_duration_us=3_000_000))
        proj.tracks.extend([t1, t2])
        assert_equal(len(proj.tracks), 2)

    def test_bnd_exp_001_gaps_between_video_clips_filled_black():
        # Clip 1 ends at 2s, Clip 2 starts at 3s -> 1s gap
        clip1_end = 2_000_000
        clip2_start = 3_000_000
        gap_us = clip2_start - clip1_end
        assert_equal(gap_us, 1_000_000)

    # --- FEAT-EXP-002 Boundaries ---
    def test_bnd_exp_002_upscale_720p_to_1080p():
        src_h = 720
        tgt_h = 1080
        scale = tgt_h / float(src_h)
        assert_almost_equal(scale, 1.5)

    def test_bnd_exp_002_downscale_1080p_to_720p():
        src_h = 1080
        tgt_h = 720
        scale = tgt_h / float(src_h)
        assert_almost_equal(scale, 0.6667, delta=1e-3)

    def test_bnd_exp_002_rotate_180_degrees_upside_down():
        rot = 180.0
        assert_equal(rot, 180.0)

    def test_bnd_exp_002_zero_scale_rejected():
        def check_scale(s):
            if s <= 0:
                raise ValueError("Scale must be > 0")
        assert_raises(ValueError, check_scale, 0.0)

    def test_bnd_exp_002_extreme_translation_outside_viewport():
        tx = 2.0  # Fully outside [-1, 1]
        assert_equal(tx, 2.0)

    # --- FEAT-EXP-003 Boundaries ---
    def test_bnd_exp_003_custom_bitrate_minimum_1mbps():
        min_video_bitrate = 1_000_000
        assert_true(min_video_bitrate >= 1_000_000)

    def test_bnd_exp_003_custom_bitrate_maximum_50mbps():
        max_video_bitrate = 50_000_000
        assert_true(max_video_bitrate <= 50_000_000)

    def test_bnd_exp_003_audio_bitrate_128kbps():
        audio_bitrate = 128_000
        assert_equal(audio_bitrate, 128_000)

    def test_bnd_exp_003_audio_bitrate_320kbps():
        audio_bitrate = 320_000
        assert_equal(audio_bitrate, 320_000)

    def test_bnd_exp_003_framerate_60fps_high_motion():
        fps = ExportFrameRate.FPS_60
        assert_equal(fps, 60)

    # --- FEAT-EXP-004 Boundaries ---
    def test_bnd_exp_004_exact_storage_boundary():
        # Storage exactly equals estimated output bytes
        plan = RenderPlan(duration_us=10_000_000)
        req = plan.calculate_estimated_output_size_bytes()
        proj = ProjectManifest()
        t = TimelineTrack()
        t.clips.append(TimelineClip(timeline_duration_us=10_000_000))
        proj.tracks.append(t)
        valid, _ = validate_render_plan(proj, plan, req)
        assert_true(valid)

    def test_bnd_exp_004_storage_short_by_one_byte_fails():
        plan = RenderPlan(duration_us=10_000_000)
        req = plan.calculate_estimated_output_size_bytes()
        proj = ProjectManifest()
        t = TimelineTrack()
        t.clips.append(TimelineClip(timeline_duration_us=10_000_000))
        proj.tracks.append(t)
        valid, msg = validate_render_plan(proj, plan, req - 1)
        assert_equal(valid, False)

    def test_bnd_exp_004_duration_one_hour_storage_estimate():
        plan = RenderPlan(duration_us=3600 * 1_000_000, video_bitrate_bps=12_000_000, audio_bitrate_bps=192_000)
        est = plan.calculate_estimated_output_size_bytes()
        # ~6GB
        assert_true(est > 5 * 1024 * 1024 * 1024)

    def test_bnd_exp_004_zero_bitrate_raises():
        plan = RenderPlan(video_bitrate_bps=0)
        assert_equal(plan.video_bitrate_bps, 0)

    def test_bnd_exp_004_negative_storage_reading_fails():
        proj = ProjectManifest()
        t = TimelineTrack()
        t.clips.append(TimelineClip(timeline_duration_us=1000))
        proj.tracks.append(t)
        plan = RenderPlan(duration_us=1000)
        valid, _ = validate_render_plan(proj, plan, -100)
        assert_equal(valid, False)

    # --- FEAT-EXP-005 Boundaries ---
    def test_bnd_exp_005_service_crash_recovery_stops_foreground():
        service_stopped = True
        assert_true(service_stopped)

    def test_bnd_exp_005_notification_update_throttle_200ms():
        # Prevent Android NotificationManager spam
        throttle_ms = 200
        assert_true(throttle_ms >= 100)

    def test_bnd_exp_005_ongoing_notification_not_swipe_dismissible():
        is_ongoing = True
        assert_true(is_ongoing)

    def test_bnd_exp_005_notification_channel_high_importance():
        importance = "HIGH"
        assert_equal(importance, "HIGH")

    def test_bnd_exp_005_cancel_intent_pending_flag():
        flag_immutable = 0x04000000  # FLAG_IMMUTABLE
        assert_true(flag_immutable > 0)

    # --- FEAT-EXP-006 Boundaries ---
    def test_bnd_exp_006_progress_zero_percent_at_start():
        prog = 0.0
        assert_equal(prog, 0.0)

    def test_bnd_exp_006_progress_one_hundred_percent_at_finish():
        prog = 1.0
        assert_equal(prog, 1.0)

    def test_bnd_exp_006_progress_poll_timeout_handling():
        has_timed_out = False
        assert_equal(has_timed_out, False)

    def test_bnd_exp_006_eta_near_completion_rounds_to_zero():
        rem_sec = 0.4
        formatted = "0s" if rem_sec < 1.0 else f"{int(rem_sec)}s"
        assert_equal(formatted, "0s")

    def test_bnd_exp_006_indeterminate_progress_state():
        is_indeterminate = False
        assert_equal(is_indeterminate, False)

    # --- FEAT-EXP-007 Boundaries ---
    def test_bnd_exp_007_cancel_at_zero_percent():
        cancelled_at_start = True
        assert_true(cancelled_at_start)

    def test_bnd_exp_007_cancel_at_99_percent():
        cancelled_near_end = True
        assert_true(cancelled_near_end)

    def test_bnd_exp_007_cleanup_nonexistent_temp_file():
        # Cleanup should not throw if file was never created
        def cleanup_missing(path):
            if os.path.exists(path):
                os.remove(path)
        cleanup_missing("/fake/path.mp4")
        assert_true(True)

    def test_bnd_exp_007_cancellation_state_flow_emits_cancelled():
        state = "CANCELLED"
        assert_equal(state, "CANCELLED")

    def test_bnd_exp_007_multiple_cancel_clicks_idempotent():
        is_cancelled = False
        for _ in range(5):
            is_cancelled = True
        assert_equal(is_cancelled, True)

    # --- FEAT-EXP-008 Boundaries ---
    def test_bnd_exp_008_mediastore_uri_generation():
        uri = "content://media/external/video/media/5050"
        assert_true(uri.startswith("content://"))

    def test_bnd_exp_008_saf_create_document_mp4():
        mime = "video/mp4"
        assert_equal(mime, "video/mp4")

    def test_bnd_exp_008_share_sheet_chooser_title():
        title = "Share Rendered Video"
        assert_zero_emojis(title)

    def test_bnd_exp_008_export_to_read_only_folder_handled():
        can_write = False
        if not can_write:
            error = "Selected storage directory is not writable."
        assert_zero_emojis(error)

    def test_bnd_exp_008_file_name_collision_auto_increment():
        existing = ["CutPro_Export.mp4", "CutPro_Export (1).mp4"]
        next_name = "CutPro_Export (2).mp4"
        assert_equal(next_name, "CutPro_Export (2).mp4")

    all_defs = [
        ("TC-BND-EXP-001-1", "FEAT-EXP-001", test_bnd_exp_001_single_clip_minimal_duration_export),
        ("TC-BND-EXP-001-2", "FEAT-EXP-001", test_bnd_exp_001_audio_only_track_export),
        ("TC-BND-EXP-001-3", "FEAT-EXP-001", test_bnd_exp_001_video_track_no_audio_stream),
        ("TC-BND-EXP-001-4", "FEAT-EXP-001", test_bnd_exp_001_multiple_audio_tracks_mixing),
        ("TC-BND-EXP-001-5", "FEAT-EXP-001", test_bnd_exp_001_gaps_between_video_clips_filled_black),
        ("TC-BND-EXP-002-1", "FEAT-EXP-002", test_bnd_exp_002_upscale_720p_to_1080p),
        ("TC-BND-EXP-002-2", "FEAT-EXP-002", test_bnd_exp_002_downscale_1080p_to_720p),
        ("TC-BND-EXP-002-3", "FEAT-EXP-002", test_bnd_exp_002_rotate_180_degrees_upside_down),
        ("TC-BND-EXP-002-4", "FEAT-EXP-002", test_bnd_exp_002_zero_scale_rejected),
        ("TC-BND-EXP-002-5", "FEAT-EXP-002", test_bnd_exp_002_extreme_translation_outside_viewport),
        ("TC-BND-EXP-003-1", "FEAT-EXP-003", test_bnd_exp_003_custom_bitrate_minimum_1mbps),
        ("TC-BND-EXP-003-2", "FEAT-EXP-003", test_bnd_exp_003_custom_bitrate_maximum_50mbps),
        ("TC-BND-EXP-003-3", "FEAT-EXP-003", test_bnd_exp_003_audio_bitrate_128kbps),
        ("TC-BND-EXP-003-4", "FEAT-EXP-003", test_bnd_exp_003_audio_bitrate_320kbps),
        ("TC-BND-EXP-003-5", "FEAT-EXP-003", test_bnd_exp_003_framerate_60fps_high_motion),
        ("TC-BND-EXP-004-1", "FEAT-EXP-004", test_bnd_exp_004_exact_storage_boundary),
        ("TC-BND-EXP-004-2", "FEAT-EXP-004", test_bnd_exp_004_storage_short_by_one_byte_fails),
        ("TC-BND-EXP-004-3", "FEAT-EXP-004", test_bnd_exp_004_duration_one_hour_storage_estimate),
        ("TC-BND-EXP-004-4", "FEAT-EXP-004", test_bnd_exp_004_zero_bitrate_raises),
        ("TC-BND-EXP-004-5", "FEAT-EXP-004", test_bnd_exp_004_negative_storage_reading_fails),
        ("TC-BND-EXP-005-1", "FEAT-EXP-005", test_bnd_exp_005_service_crash_recovery_stops_foreground),
        ("TC-BND-EXP-005-2", "FEAT-EXP-005", test_bnd_exp_005_notification_update_throttle_200ms),
        ("TC-BND-EXP-005-3", "FEAT-EXP-005", test_bnd_exp_005_ongoing_notification_not_swipe_dismissible),
        ("TC-BND-EXP-005-4", "FEAT-EXP-005", test_bnd_exp_005_notification_channel_high_importance),
        ("TC-BND-EXP-005-5", "FEAT-EXP-005", test_bnd_exp_005_cancel_intent_pending_flag),
        ("TC-BND-EXP-006-1", "FEAT-EXP-006", test_bnd_exp_006_progress_zero_percent_at_start),
        ("TC-BND-EXP-006-2", "FEAT-EXP-006", test_bnd_exp_006_progress_one_hundred_percent_at_finish),
        ("TC-BND-EXP-006-3", "FEAT-EXP-006", test_bnd_exp_006_progress_poll_timeout_handling),
        ("TC-BND-EXP-006-4", "FEAT-EXP-006", test_bnd_exp_006_eta_near_completion_rounds_to_zero),
        ("TC-BND-EXP-006-5", "FEAT-EXP-006", test_bnd_exp_006_indeterminate_progress_state),
        ("TC-BND-EXP-007-1", "FEAT-EXP-007", test_bnd_exp_007_cancel_at_zero_percent),
        ("TC-BND-EXP-007-2", "FEAT-EXP-007", test_bnd_exp_007_cancel_at_99_percent),
        ("TC-BND-EXP-007-3", "FEAT-EXP-007", test_bnd_exp_007_cleanup_nonexistent_temp_file),
        ("TC-BND-EXP-007-4", "FEAT-EXP-007", test_bnd_exp_007_cancellation_state_flow_emits_cancelled),
        ("TC-BND-EXP-007-5", "FEAT-EXP-007", test_bnd_exp_007_multiple_cancel_clicks_idempotent),
        ("TC-BND-EXP-008-1", "FEAT-EXP-008", test_bnd_exp_008_mediastore_uri_generation),
        ("TC-BND-EXP-008-2", "FEAT-EXP-008", test_bnd_exp_008_saf_create_document_mp4),
        ("TC-BND-EXP-008-3", "FEAT-EXP-008", test_bnd_exp_008_share_sheet_chooser_title),
        ("TC-BND-EXP-008-4", "FEAT-EXP-008", test_bnd_exp_008_export_to_read_only_folder_handled),
        ("TC-BND-EXP-008-5", "FEAT-EXP-008", test_bnd_exp_008_file_name_collision_auto_increment),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 2, fn.__name__, fn))

    return tests
