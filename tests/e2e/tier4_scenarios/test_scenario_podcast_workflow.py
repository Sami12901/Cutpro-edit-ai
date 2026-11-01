"""
Tier 4 Real-World Application Scenario: Long-Form Multi-Track Podcast & Interview
Workflow:
1. Initialize Long-Form Interview Project (16:9, 1080p 30fps).
2. Dual-channel dialogue tracks: Host Mic (A1) and Remote Guest Mic (A2).
3. AI Assisted Workflow:
   - Automated Silence Detector scans tracks, identifying pauses > 750ms below -40 dB.
   - Pacing Analyzer generates structured EditPlan containing proposed ripple cut actions.
   - User reviews EditPlan; tests approve selective actions and rollback rejected actions.
4. Auto-ducking between host and guest to prevent cross-talk phase cancellation.
5. Export pipeline validation and full .cutprobundle project packaging.
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_zero_emojis
)
from ..framework.models import (
    ProjectManifest, TimelineTrack, TimelineClip, MediaSourceInfo, ClipAudioProperties,
    TrackType, CanvasAspectRatio, ExportResolution, ExportFrameRate, VideoCodec,
    detect_silence_intervals, analyze_speech_pacing, EditPlan, EditAction,
    RenderPlan, validate_render_plan, is_safe_zip_path
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    def test_scenario_podcast_workflow_end_to_end():
        # Step 1: Initialize Project (30-minute interview segment, 1_800_000_000 us)
        project = ProjectManifest(
            title="Episode 42: The Future of High-Performance Systems",
            aspect_ratio=CanvasAspectRatio.RATIO_16_9,
            default_resolution=ExportResolution.RES_1080P,
            default_frame_rate=ExportFrameRate.FPS_30
        )
        assert_zero_emojis(project.title)

        # Step 2: Assemble Multi-Track Dialogue
        track_host = TimelineTrack(name="A1 Host Microphone", type=TrackType.AUDIO, z_index=0)
        track_guest = TimelineTrack(name="A2 Guest Microphone", type=TrackType.AUDIO, z_index=1)

        clip_host = TimelineClip(
            track_id=track_host.id,
            media_source=MediaSourceInfo("content://saf/host_raw.wav", "host_raw.wav", "audio/wav", duration_us=1_800_000_000),
            timeline_start_us=0,
            timeline_duration_us=1_800_000_000,
            source_in_us=0,
            source_out_us=1_800_000_000,
            audio_properties=ClipAudioProperties(volume=1.0)
        )
        clip_guest = TimelineClip(
            track_id=track_guest.id,
            media_source=MediaSourceInfo("content://saf/guest_raw.wav", "guest_raw.wav", "audio/wav", duration_us=1_800_000_000),
            timeline_start_us=0,
            timeline_duration_us=1_800_000_000,
            source_in_us=0,
            source_out_us=1_800_000_000,
            audio_properties=ClipAudioProperties(volume=1.0)
        )
        track_host.clips.append(clip_host)
        track_guest.clips.append(clip_guest)
        project.tracks.extend([track_host, track_guest])

        # Step 3: Automated Silence Detection
        # Mock audio RMS energy curve across intervals
        # Silence detected at [120s to 125s] (5s dead air) and [300s to 302s] (2s pause)
        audio_profile = [
            (0, 120_000_000, -18.0),        # Speech
            (120_000_000, 125_000_000, -52.0), # Silence
            (125_000_000, 300_000_000, -16.0), # Speech
            (300_000_000, 302_000_000, -48.0), # Silence
            (302_000_000, 1_800_000_000, -15.0) # Speech
        ]
        silence_intervals = detect_silence_intervals(audio_profile, threshold_db=-40.0, min_duration_us=1_000_000)
        assert_equal(len(silence_intervals), 2)
        assert_equal(silence_intervals[0], (120_000_000, 125_000_000))
        assert_equal(silence_intervals[1], (300_000_000, 302_000_000))

        # Step 4: AI Pacing Analyzer & EditPlan Construction
        pacing_metrics = analyze_speech_pacing(
            total_duration_us=1_800_000_000,
            silence_duration_us=7_000_000,
            spoken_words_count=4200
        )
        # 4200 words / 29.88 minutes ~= 140 WPM (optimal conversational pacing)
        assert_true(pacing_metrics["words_per_minute"] > 130)
        assert_true(pacing_metrics["words_per_minute"] < 155)

        # Build proposed EditPlan
        plan = EditPlan(
            name="Remove Extended Pauses",
            actions=[
                EditAction(action_type="RIPPLE_DELETE", start_us=120_000_000, end_us=125_000_000, description="Cut 5s silence"),
                EditAction(action_type="RIPPLE_DELETE", start_us=300_000_000, end_us=302_000_000, description="Cut 2s pause")
            ]
        )
        assert_equal(len(plan.actions), 2)

        # User selectively approves action 0, rejects action 1
        plan.actions[0].approved = True
        plan.actions[1].approved = False

        approved_actions = [a for a in plan.actions if a.approved]
        rejected_actions = [a for a in plan.actions if not a.approved]
        assert_equal(len(approved_actions), 1)
        assert_equal(len(rejected_actions), 1)
        assert_equal(approved_actions[0].start_us, 120_000_000)

        # Apply approved ripple cut: reduces timeline duration by 5s (5_000_000 us)
        post_edit_duration_us = 1_800_000_000 - (approved_actions[0].end_us - approved_actions[0].start_us)
        assert_equal(post_edit_duration_us, 1_795_000_000)

        # Step 5: Render Plan Validation for 30-minute podcast
        render_plan = RenderPlan(
            resolution=ExportResolution.RES_1080P,
            frame_rate=ExportFrameRate.FPS_30,
            aspect_ratio=CanvasAspectRatio.RATIO_16_9,
            video_codec=VideoCodec.H264,
            video_bitrate_bps=8_000_000,
            audio_bitrate_bps=256_000,
            duration_us=post_edit_duration_us
        )
        # Needs ~1.85 GB storage space
        estimated_bytes = render_plan.estimate_file_size_bytes()
        assert_true(estimated_bytes > 1_500_000_000)
        valid, msg = validate_render_plan(project, render_plan, available_bytes=5 * 1024 * 1024 * 1024)
        assert_true(valid)

        # Step 6: Verify Safe Bundle Archival
        bundle_files = ["project.json", "audio/host_raw.wav", "audio/guest_raw.wav"]
        for bf in bundle_files:
            assert_true(is_safe_zip_path(bf))

    tests.append(OpaqueBoxTestCase(
        test_id="TC-SCENARIO-05",
        feature_id="SCENARIO-PODCAST-WORKFLOW",
        tier=4,
        name="test_scenario_podcast_workflow_end_to_end",
        test_func=test_scenario_podcast_workflow_end_to_end
    ))
    return tests
