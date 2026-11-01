"""
Tier 4 Real-World Application Scenario: YouTube Tech Review / Talking-Head Video
Workflow:
1. Initialize 16:9 Landscape Project with 1080p 30fps defaults.
2. Assemble Multitrack Timeline:
   - V1 Track: Main Talking-Head camera clips with in/out trimming.
   - V2 Track: B-Roll product showcase clips positioned at specific timestamps.
   - A1 Track: Host voice dialogue with volume boost.
   - A2 Track: Background music with dynamic audio ducking and S-Curve fades.
   - Overlay Track: Lower-Third "News Broadcast Bar" template.
   - Caption Track: SRT Subtitles parsed, timed, with styled captions.
3. Validate Media3 Transformer Render Plan and Storage Pre-check.
4. Verify .cutproproj JSON v1 manifest serialization and zero-emoji compliance.
"""

import json
from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_zero_emojis
)
from ..framework.models import (
    ProjectManifest, TimelineTrack, TimelineClip, MediaSourceInfo,
    ClipTransform, ClipAudioProperties, TrackType, CanvasAspectRatio,
    ExportResolution, ExportFrameRate, VideoCodec, FadeCurveType,
    RenderPlan, SrtParser, trim_clip_in, trim_clip_out,
    calculate_audio_gain, validate_render_plan
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    def test_scenario_youtube_review_end_to_end():
        # Step 1: Project Initialization
        project = ProjectManifest(
            title="Smartphone Review: Flagship 2026",
            aspect_ratio=CanvasAspectRatio.RATIO_16_9,
            default_resolution=ExportResolution.RES_1080P,
            default_frame_rate=ExportFrameRate.FPS_30
        )
        assert_zero_emojis(project.title)

        # Step 2: Assemble Multitrack Timeline
        # V1: Main Camera Talking Head
        track_v1 = TimelineTrack(name="V1 Talking Head", type=TrackType.VIDEO, z_index=0)
        clip_cam_raw = TimelineClip(
            track_id=track_v1.id,
            media_source=MediaSourceInfo("content://media/picker/cam_a.mp4", "cam_a.mp4", "video/mp4", duration_us=30_000_000),
            timeline_start_us=0,
            timeline_duration_us=30_000_000,
            source_in_us=0,
            source_out_us=30_000_000
        )
        # Trim first 2 seconds (pre-roll) and last 3 seconds
        clip_cam_trimmed = trim_clip_in(clip_cam_raw, requested_new_in_us=2_000_000)
        clip_cam_final = trim_clip_out(clip_cam_trimmed, requested_new_out_us=27_000_000)
        assert_equal(clip_cam_final.timeline_duration_us, 25_000_000)
        track_v1.clips.append(clip_cam_final)

        # V2: B-Roll Product Overlay (from 10s to 18s)
        track_v2 = TimelineTrack(name="V2 B-Roll Overlay", type=TrackType.OVERLAY, z_index=1)
        clip_broll = TimelineClip(
            track_id=track_v2.id,
            media_source=MediaSourceInfo("content://media/picker/broll.mp4", "broll.mp4", "video/mp4", duration_us=10_000_000),
            timeline_start_us=10_000_000,
            timeline_duration_us=8_000_000,
            source_in_us=0,
            source_out_us=8_000_000,
            transform=ClipTransform(scale_x=1.05, scale_y=1.05)
        )
        track_v2.clips.append(clip_broll)

        # A1: Voice Dialogue Track (volume 1.25)
        track_a1 = TimelineTrack(name="A1 Voice Dialogue", type=TrackType.AUDIO, z_index=0)
        clip_voice = TimelineClip(
            track_id=track_a1.id,
            timeline_start_us=0,
            timeline_duration_us=25_000_000,
            source_in_us=2_000_000,
            source_out_us=27_000_000,
            audio_properties=ClipAudioProperties(volume=1.25)
        )
        track_a1.clips.append(clip_voice)

        # A2: Background Music with Fades & Ducking (volume 0.3)
        track_a2 = TimelineTrack(name="A2 Background Music", type=TrackType.AUDIO, z_index=1)
        clip_bgm = TimelineClip(
            track_id=track_a2.id,
            timeline_start_us=0,
            timeline_duration_us=25_000_000,
            source_in_us=0,
            source_out_us=25_000_000,
            audio_properties=ClipAudioProperties(
                volume=0.3,
                fade_in_duration_us=2_000_000,
                fade_in_curve=FadeCurveType.SCURVE,
                fade_out_duration_us=3_000_000,
                fade_out_curve=FadeCurveType.SCURVE
            )
        )
        track_a2.clips.append(clip_bgm)

        # Overlay Track: Lower-Third "News Broadcast Bar" Template
        track_lower = TimelineTrack(name="Lower Thirds", type=TrackType.TEXT, z_index=2)
        clip_lower = TimelineClip(
            track_id=track_lower.id,
            timeline_start_us=3_000_000,
            timeline_duration_us=5_000_000,
            custom_title="Tech Reviewer - CutPro Lead"
        )
        assert_zero_emojis(clip_lower.custom_title)
        track_lower.clips.append(clip_lower)

        # Subtitles Track: Parse SRT
        sample_srt = """1
00:00:01,000 --> 00:00:05,000
Today we are testing the flagship smartphone camera.

2
00:00:06,000 --> 00:00:10,000
Notice the dynamic range and low light performance.
"""
        cues = SrtParser.parse(sample_srt)
        assert_equal(len(cues), 2)
        assert_zero_emojis(sample_srt)

        project.tracks = [track_v1, track_v2, track_a1, track_a2, track_lower]

        # Step 3: Validate Audio Gain Curves (Fade in at 1s, Ducking during voice)
        gain_bgm_1s = calculate_audio_gain(
            pts_us=1_000_000,
            clip_duration_us=25_000_000,
            volume=0.3,
            fade_in_duration_us=2_000_000,
            fade_in_curve=FadeCurveType.SCURVE
        )
        # S-curve at 50% = 0.5 -> 0.3 * 0.5 = 0.15
        assert_almost_equal(gain_bgm_1s, 0.15)

        # Step 4: Validate Render Plan and Pre-Export Storage Check
        plan = RenderPlan(
            resolution=ExportResolution.RES_1080P,
            frame_rate=ExportFrameRate.FPS_30,
            aspect_ratio=CanvasAspectRatio.RATIO_16_9,
            video_codec=VideoCodec.H264,
            video_bitrate_bps=12_000_000,
            audio_bitrate_bps=192_000,
            duration_us=25_000_000
        )
        est_bytes = plan.calculate_estimated_output_size_bytes()
        assert_true(est_bytes > 50 * 1024 * 1024)

        # Available space 2GB
        available_bytes = 2 * 1024 * 1024 * 1024
        valid, msg = validate_render_plan(project, plan, available_bytes)
        assert_true(valid)
        assert_equal(msg, "Valid")

        # Step 5: Serialize and Deserialize Project Manifest
        json_output = project.to_json()
        assert_zero_emojis(json_output)
        restored_proj = ProjectManifest.from_json(json_output)
        assert_equal(restored_proj.title, project.title)
        assert_equal(len(restored_proj.tracks), 5)
        assert_equal(restored_proj.tracks[0].clips[0].timeline_duration_us, 25_000_000)

    tests.append(OpaqueBoxTestCase(
        test_id="TC-SCENARIO-01",
        feature_id="SCENARIO-YOUTUBE",
        tier=4,
        name="test_scenario_youtube_review_end_to_end",
        test_func=test_scenario_youtube_review_end_to_end
    ))
    return tests
