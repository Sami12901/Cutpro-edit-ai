"""
Tier 4 Real-World Application Scenario: 24fps 16:9 Cinematic Documentary Scene
Workflow:
1. Initialize 16:9 24fps Cinematic Project.
2. Slow-motion drone shot adjusted to 0.5x playback speed with automatic duration doubling.
3. Cinematic Split Fade opening title animation with ease-in-out easing curves.
4. Ambient background soundtrack with Constant-Power Stereo Panning and Exponential Fades.
5. Pre-export storage pre-check and atomic manifest serialization.
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_zero_emojis
)
from ..framework.models import (
    ProjectManifest, TimelineTrack, TimelineClip, MediaSourceInfo,
    ClipTransform, ClipAudioProperties, TrackType, CanvasAspectRatio,
    ExportResolution, ExportFrameRate, VideoCodec, FadeCurveType,
    RenderPlan, calculate_timeline_duration_us, calculate_audio_gain,
    constant_power_stereo_pan, evaluate_easing, validate_render_plan
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    def test_scenario_cinematic_doc_end_to_end():
        # Step 1: Initialize 24fps Cinematic Project
        project = ProjectManifest(
            title="The Silent Mountains: A Cinematic Journey",
            aspect_ratio=CanvasAspectRatio.RATIO_16_9,
            default_resolution=ExportResolution.RES_1080P,
            default_frame_rate=ExportFrameRate.FPS_24
        )
        assert_zero_emojis(project.title)

        # Step 2: Slow-Motion Video Clip (0.5x speed)
        # Source trim 10 seconds -> at 0.5x speed yields 20 seconds on timeline
        drone_source_dur = 10_000_000
        speed = 0.5
        timeline_dur = calculate_timeline_duration_us(0, drone_source_dur, speed)
        assert_equal(timeline_dur, 20_000_000)

        v_track = TimelineTrack(name="V1 Cinematic Aerial", type=TrackType.VIDEO, z_index=0)
        clip_drone = TimelineClip(
            track_id=v_track.id,
            media_source=MediaSourceInfo("content://saf/drone.mov", "drone.mov", "video/quicktime", duration_us=drone_source_dur),
            timeline_start_us=0,
            timeline_duration_us=timeline_dur,
            source_in_us=0,
            source_out_us=drone_source_dur,
            speed_multiplier=speed
        )
        v_track.clips.append(clip_drone)
        project.tracks.append(v_track)

        # Step 3: Cinematic Title Animation (Split Fade with EaseInOut)
        title_prog = evaluate_easing("EASE_IN_OUT", 0.5)
        assert_almost_equal(title_prog, 0.5)

        # Step 4: Multichannel Audio Mixing & Panning
        # Ambient track panned slightly left (-0.3)
        l_gain, r_gain = constant_power_stereo_pan(-0.3)
        power = l_gain ** 2 + r_gain ** 2
        assert_almost_equal(power, 1.0)
        assert_true(l_gain > r_gain)

        # Exponential Fade-in over 4 seconds
        gain_2s = calculate_audio_gain(
            pts_us=2_000_000,
            clip_duration_us=timeline_dur,
            volume=0.8,
            fade_in_duration_us=4_000_000,
            fade_in_curve=FadeCurveType.EXPONENTIAL
        )
        # At 2s (progress 0.5), exp curve is 0.25 -> 0.8 * 0.25 = 0.20
        assert_almost_equal(gain_2s, 0.20)

        # Step 5: Render Plan Validation at 24fps
        plan = RenderPlan(
            resolution=ExportResolution.RES_1080P,
            frame_rate=ExportFrameRate.FPS_24,
            aspect_ratio=CanvasAspectRatio.RATIO_16_9,
            video_codec=VideoCodec.H264,
            video_bitrate_bps=14_000_000,
            audio_bitrate_bps=256_000,
            duration_us=timeline_dur
        )
        valid, msg = validate_render_plan(project, plan, available_bytes=1024 * 1024 * 1024)
        assert_true(valid)

        # Verify Manifest JSON integrity
        json_manifest = project.to_json()
        assert_zero_emojis(json_manifest)
        restored = ProjectManifest.from_json(json_manifest)
        assert_equal(restored.tracks[0].clips[0].speed_multiplier, 0.5)

    tests.append(OpaqueBoxTestCase(
        test_id="TC-SCENARIO-03",
        feature_id="SCENARIO-CINEMATIC-DOC",
        tier=4,
        name="test_scenario_cinematic_doc_end_to_end",
        test_func=test_scenario_cinematic_doc_end_to_end
    ))
    return tests
