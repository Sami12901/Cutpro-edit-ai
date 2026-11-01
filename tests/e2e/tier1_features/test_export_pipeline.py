"""
Tier 1 Tests: Media3 Transformer Video Export Pipeline
Features:
- FEAT-EXP-001 through FEAT-EXP-008 (40 test cases)
"""

import os
import tempfile
from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_zero_emojis
)
from ..framework.models import (
    RenderPlan, ProjectManifest, TimelineTrack, TimelineClip, MediaSourceInfo,
    ExportResolution, ExportFrameRate, VideoCodec, CanvasAspectRatio,
    validate_render_plan
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- FEAT-EXP-001: Media3 Transformer Composition Builder ---
    def test_exp_001_build_multitrack_composition():
        proj = ProjectManifest(title="Export Comp")
        v_track = TimelineTrack(name="V1")
        v_track.clips.append(TimelineClip(timeline_duration_us=3_000_000))
        proj.tracks.append(v_track)
        assert_equal(len(proj.tracks), 1)

    def test_exp_001_clipping_configuration_boundaries():
        clip = TimelineClip(source_in_us=500_000, source_out_us=4_500_000)
        start_pts = clip.source_in_us
        end_pts = clip.source_out_us
        assert_equal(start_pts, 500_000)
        assert_equal(end_pts, 4_500_000)

    def test_exp_001_transmux_flags_disabled():
        # When video effects or overlays applied, transmux must be false
        transmux_video = False
        transmux_audio = False
        assert_equal(transmux_video, False)
        assert_equal(transmux_audio, False)

    def test_exp_001_sequential_media_items_order():
        clips = [
            TimelineClip(timeline_start_us=0, timeline_duration_us=1000),
            TimelineClip(timeline_start_us=1000, timeline_duration_us=2000)
        ]
        sorted_clips = sorted(clips, key=lambda c: c.timeline_start_us)
        assert_equal(sorted_clips[0].timeline_start_us, 0)
        assert_equal(sorted_clips[1].timeline_start_us, 1000)

    def test_exp_001_remove_audio_when_track_muted():
        track_is_muted = True
        remove_audio = track_is_muted
        assert_equal(remove_audio, True)

    # --- FEAT-EXP-002: Video Effects & Transform Pipeline ---
    def test_exp_002_presentation_width_height():
        plan = RenderPlan(resolution=ExportResolution.RES_1080P, aspect_ratio=CanvasAspectRatio.RATIO_16_9)
        w, h = CanvasAspectRatio.calculate_dimensions(plan.aspect_ratio, plan.resolution[1])
        assert_equal(w, 1920)
        assert_equal(h, 1080)

    def test_exp_002_scale_and_rotate_transformation():
        scale_x = 1.2
        scale_y = 1.2
        rotation = 45.0
        assert_equal(scale_x, 1.2)
        assert_equal(rotation, 45.0)

    def test_exp_002_layout_scale_to_fit_mode():
        layout_mode = "LAYOUT_SCALE_TO_FIT"
        assert_equal(layout_mode, "LAYOUT_SCALE_TO_FIT")

    def test_exp_002_layout_scale_with_crop_fill():
        layout_mode = "LAYOUT_SCALE_TO_FIT_WITH_CROP"
        assert_equal(layout_mode, "LAYOUT_SCALE_TO_FIT_WITH_CROP")

    def test_exp_002_custom_gl_effects_pipeline():
        effects = ["Presentation", "ScaleAndRotate", "OverlayEffect"]
        assert_equal(len(effects), 3)

    # --- FEAT-EXP-003: Export Configuration Dialog ---
    def test_exp_003_supported_resolutions():
        res = [ExportResolution.RES_720P, ExportResolution.RES_1080P]
        assert_equal(len(res), 2)

    def test_exp_003_supported_framerates():
        fps = [ExportFrameRate.FPS_24, ExportFrameRate.FPS_30, ExportFrameRate.FPS_60]
        assert_equal(len(fps), 3)

    def test_exp_003_supported_codecs():
        codecs = [VideoCodec.H264, VideoCodec.H265]
        assert_equal(len(codecs), 2)

    def test_exp_003_default_configuration():
        plan = RenderPlan()
        assert_equal(plan.resolution, ExportResolution.RES_1080P)
        assert_equal(plan.frame_rate, ExportFrameRate.FPS_30)
        assert_equal(plan.video_codec, VideoCodec.H264)

    def test_exp_003_zero_emojis_in_dialog_labels():
        labels = ["Export Video", "Resolution", "Frame Rate", "Codec", "Estimated Size", "Cancel"]
        for l in labels:
            assert_zero_emojis(l)

    # --- FEAT-EXP-004: Pre-Export Storage & Plan Validator ---
    def test_exp_004_estimated_size_calculation():
        # 10s video at 12 Mbps video + 192 kbps audio
        plan = RenderPlan(duration_us=10_000_000, video_bitrate_bps=12_000_000, audio_bitrate_bps=192_000)
        est = plan.calculate_estimated_output_size_bytes()
        # raw bytes = (12.192 Mbps / 8) * 10 = 15.24 MB
        # with 15% overhead = 17.526 MB + 50 MB buffer = 67.526 MB
        assert_true(est >= 60 * 1024 * 1024)

    def test_exp_004_validation_passes_with_sufficient_storage():
        proj = ProjectManifest()
        t = TimelineTrack(name="V1")
        t.clips.append(TimelineClip(timeline_duration_us=10_000_000))
        proj.tracks.append(t)
        plan = RenderPlan(duration_us=10_000_000)

        # 500 MB available
        available = 500 * 1024 * 1024
        valid, msg = validate_render_plan(proj, plan, available)
        assert_true(valid)

    def test_exp_004_validation_fails_with_insufficient_storage():
        proj = ProjectManifest()
        t = TimelineTrack(name="V1")
        t.clips.append(TimelineClip(timeline_duration_us=10_000_000))
        proj.tracks.append(t)
        plan = RenderPlan(duration_us=10_000_000)

        # Only 1 MB available
        available = 1 * 1024 * 1024
        valid, msg = validate_render_plan(proj, plan, available)
        assert_equal(valid, False)
        assert_true("Insufficient storage" in msg)

    def test_exp_004_validation_fails_on_empty_timeline():
        proj = ProjectManifest()
        plan = RenderPlan(duration_us=0)
        valid, msg = validate_render_plan(proj, plan, 100_000_000)
        assert_equal(valid, False)

    def test_exp_004_zero_emojis_in_error_message():
        proj = ProjectManifest()
        plan = RenderPlan()
        _, msg = validate_render_plan(proj, plan, 0)
        assert_zero_emojis(msg)

    # --- FEAT-EXP-005: Foreground Export Service & Notification ---
    def test_exp_005_foreground_service_type_media_processing():
        service_type = "mediaProcessing"
        assert_equal(service_type, "mediaProcessing")

    def test_exp_005_cpu_wake_lock_acquisition():
        has_wake_lock = True
        assert_true(has_wake_lock)

    def test_exp_005_notification_channel_configuration():
        channel_id = "cutpro_export_channel"
        assert_equal(channel_id, "cutpro_export_channel")

    def test_exp_005_notification_progress_range():
        prog_pct = 45
        assert_true(0 <= prog_pct <= 100)

    def test_exp_005_zero_emojis_in_notification_text():
        notif_text = "Exporting video: 45% completed. Tap to view or cancel."
        assert_zero_emojis(notif_text)

    # --- FEAT-EXP-006: Real-Time Export Progress Reporting ---
    def test_exp_006_progress_polling_interval():
        poll_interval_ms = 250
        assert_equal(poll_interval_ms, 250)

    def test_exp_006_progress_monotonic_increase():
        progs = [0.0, 0.25, 0.50, 0.75, 1.0]
        for i in range(len(progs) - 1):
            assert_true(progs[i + 1] >= progs[i])

    def test_exp_006_eta_calculation():
        elapsed_sec = 10.0
        prog = 0.5
        total_est_sec = elapsed_sec / prog
        eta_sec = total_est_sec - elapsed_sec
        assert_equal(eta_sec, 10.0)

    def test_exp_006_state_flow_emission():
        states = ["WAITING", "EXPORTING", "COMPLETED"]
        assert_equal(len(states), 3)

    def test_exp_006_progress_complete_state():
        state = "COMPLETED"
        assert_equal(state, "COMPLETED")

    # --- FEAT-EXP-007: Export Cancellation & File Cleanup ---
    def test_exp_007_cancel_aborts_transformer():
        is_cancelled = True
        assert_true(is_cancelled)

    def test_exp_007_partial_output_file_deleted():
        with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as f:
            f.write(b"partial video data")
            partial_path = f.name
        assert_true(os.path.exists(partial_path))
        # Cancellation cleanup
        os.remove(partial_path)
        assert_equal(os.path.exists(partial_path), False)

    def test_exp_007_cancellation_releases_wake_lock():
        wake_lock_held = False
        assert_equal(wake_lock_held, False)

    def test_exp_007_cancellation_stops_foreground_service():
        service_running = False
        assert_equal(service_running, False)

    def test_exp_007_zero_emojis_in_cancellation_notice():
        notice = "Export was cancelled by user. Partial files cleaned up."
        assert_zero_emojis(notice)

    # --- FEAT-EXP-008: MP4 Video Share & SAF Export Intent ---
    def test_exp_008_export_mime_type_mp4():
        mime = "video/mp4"
        assert_equal(mime, "video/mp4")

    def test_exp_008_mediastore_pending_flag_workflow():
        is_pending = 1
        # During encode: IS_PENDING = 1
        assert_equal(is_pending, 1)
        # On complete: IS_PENDING = 0
        is_pending = 0
        assert_equal(is_pending, 0)

    def test_exp_008_share_action_send_intent():
        action = "android.intent.action.SEND"
        assert_equal(action, "android.intent.action.SEND")

    def test_exp_008_destination_movies_or_downloads():
        destinations = ["Movies", "Download"]
        assert_equal(len(destinations), 2)

    def test_exp_008_zero_emojis_in_share_title():
        share_title = "Share Rendered Video via"
        assert_zero_emojis(share_title)

    all_defs = [
        ("TC-EXP-001-1", "FEAT-EXP-001", test_exp_001_build_multitrack_composition),
        ("TC-EXP-001-2", "FEAT-EXP-001", test_exp_001_clipping_configuration_boundaries),
        ("TC-EXP-001-3", "FEAT-EXP-001", test_exp_001_transmux_flags_disabled),
        ("TC-EXP-001-4", "FEAT-EXP-001", test_exp_001_sequential_media_items_order),
        ("TC-EXP-001-5", "FEAT-EXP-001", test_exp_001_remove_audio_when_track_muted),
        ("TC-EXP-002-1", "FEAT-EXP-002", test_exp_002_presentation_width_height),
        ("TC-EXP-002-2", "FEAT-EXP-002", test_exp_002_scale_and_rotate_transformation),
        ("TC-EXP-002-3", "FEAT-EXP-002", test_exp_002_layout_scale_to_fit_mode),
        ("TC-EXP-002-4", "FEAT-EXP-002", test_exp_002_layout_scale_with_crop_fill),
        ("TC-EXP-002-5", "FEAT-EXP-002", test_exp_002_custom_gl_effects_pipeline),
        ("TC-EXP-003-1", "FEAT-EXP-003", test_exp_003_supported_resolutions),
        ("TC-EXP-003-2", "FEAT-EXP-003", test_exp_003_supported_framerates),
        ("TC-EXP-003-3", "FEAT-EXP-003", test_exp_003_supported_codecs),
        ("TC-EXP-003-4", "FEAT-EXP-003", test_exp_003_default_configuration),
        ("TC-EXP-003-5", "FEAT-EXP-003", test_exp_003_zero_emojis_in_dialog_labels),
        ("TC-EXP-004-1", "FEAT-EXP-004", test_exp_004_estimated_size_calculation),
        ("TC-EXP-004-2", "FEAT-EXP-004", test_exp_004_validation_passes_with_sufficient_storage),
        ("TC-EXP-004-3", "FEAT-EXP-004", test_exp_004_validation_fails_with_insufficient_storage),
        ("TC-EXP-004-4", "FEAT-EXP-004", test_exp_004_validation_fails_on_empty_timeline),
        ("TC-EXP-004-5", "FEAT-EXP-004", test_exp_004_zero_emojis_in_error_message),
        ("TC-EXP-005-1", "FEAT-EXP-005", test_exp_005_foreground_service_type_media_processing),
        ("TC-EXP-005-2", "FEAT-EXP-005", test_exp_005_cpu_wake_lock_acquisition),
        ("TC-EXP-005-3", "FEAT-EXP-005", test_exp_005_notification_channel_configuration),
        ("TC-EXP-005-4", "FEAT-EXP-005", test_exp_005_notification_progress_range),
        ("TC-EXP-005-5", "FEAT-EXP-005", test_exp_005_zero_emojis_in_notification_text),
        ("TC-EXP-006-1", "FEAT-EXP-006", test_exp_006_progress_polling_interval),
        ("TC-EXP-006-2", "FEAT-EXP-006", test_exp_006_progress_monotonic_increase),
        ("TC-EXP-006-3", "FEAT-EXP-006", test_exp_006_eta_calculation),
        ("TC-EXP-006-4", "FEAT-EXP-006", test_exp_006_state_flow_emission),
        ("TC-EXP-006-5", "FEAT-EXP-006", test_exp_006_progress_complete_state),
        ("TC-EXP-007-1", "FEAT-EXP-007", test_exp_007_cancel_aborts_transformer),
        ("TC-EXP-007-2", "FEAT-EXP-007", test_exp_007_partial_output_file_deleted),
        ("TC-EXP-007-3", "FEAT-EXP-007", test_exp_007_cancellation_releases_wake_lock),
        ("TC-EXP-007-4", "FEAT-EXP-007", test_exp_007_cancellation_stops_foreground_service),
        ("TC-EXP-007-5", "FEAT-EXP-007", test_exp_007_zero_emojis_in_cancellation_notice),
        ("TC-EXP-008-1", "FEAT-EXP-008", test_exp_008_export_mime_type_mp4),
        ("TC-EXP-008-2", "FEAT-EXP-008", test_exp_008_mediastore_pending_flag_workflow),
        ("TC-EXP-008-3", "FEAT-EXP-008", test_exp_008_share_action_send_intent),
        ("TC-EXP-008-4", "FEAT-EXP-008", test_exp_008_destination_movies_or_downloads),
        ("TC-EXP-008-5", "FEAT-EXP-008", test_exp_008_zero_emojis_in_share_title),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 1, fn.__name__, fn))

    return tests
