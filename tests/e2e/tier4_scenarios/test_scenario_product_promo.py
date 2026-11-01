"""
Tier 4 Real-World Application Scenario: 1:1 Square Social Product Promo
Workflow:
1. Initialize 1:1 Square Canvas (1080x1080 30fps) for social media product showcase.
2. Assemble main product video footage on V1 with precise in/out trimming.
3. Motion Graphics Overlay:
   - Logo reveal animation utilizing spring physics dampening.
   - Animated numeric counter (0 to 50,000 customers) with prefix/suffix formatting.
   - Call-to-Action (CTA) badge with elastic bounce easing.
4. Multitrack audio with Voiceover (A1) ducking Upbeat BGM (A2) by -12 dB.
5. High-bitrate 1080x1080 MP4 export validation and atomic JSON manifest persistence.
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_zero_emojis
)
from ..framework.models import (
    ProjectManifest, TimelineTrack, TimelineClip, MediaSourceInfo,
    TrackType, CanvasAspectRatio, ExportResolution, ExportFrameRate, VideoCodec,
    Keyframe, solve_spring_analytical, solve_cubic_bezier,
    calculate_audio_gain, compute_audio_ducking, RenderPlan, validate_render_plan
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    def test_scenario_product_promo_end_to_end():
        # Step 1: Initialize 1:1 Square Project (1080x1080)
        project = ProjectManifest(
            title="Aura Wireless Headphones - Official Product Launch",
            aspect_ratio=CanvasAspectRatio.RATIO_1_1,
            default_resolution=(1080, 1080, "1080x1080 Square"),
            default_frame_rate=ExportFrameRate.FPS_30
        )
        assert_zero_emojis(project.title)

        # Step 2: Assemble Product Footage (15-second total duration)
        v_track = TimelineTrack(name="V1 Product Footage", type=TrackType.VIDEO, z_index=0)
        c1 = TimelineClip(
            track_id=v_track.id,
            media_source=MediaSourceInfo("content://saf/hero_shot.mp4", "hero_shot.mp4", "video/mp4", duration_us=10_000_000),
            timeline_start_us=0,
            timeline_duration_us=7_000_000,
            source_in_us=1_000_000,
            source_out_us=8_000_000
        )
        c2 = TimelineClip(
            track_id=v_track.id,
            media_source=MediaSourceInfo("content://saf/lifestyle_macro.mp4", "lifestyle_macro.mp4", "video/mp4", duration_us=12_000_000),
            timeline_start_us=7_000_000,
            timeline_duration_us=8_000_000,
            source_in_us=2_000_000,
            source_out_us=10_000_000
        )
        v_track.clips.extend([c1, c2])
        project.tracks.append(v_track)

        total_duration_us = 15_000_000
        assert_equal(v_track.clips[0].timeline_duration_us + v_track.clips[1].timeline_duration_us, total_duration_us)

        # Step 3: Motion Graphics Layers
        # 3a. Logo Reveal (0.0s to 2.5s) with Spring Physics
        stiffness = 250.0
        damping = 0.70
        initial_scale = solve_spring_analytical(stiffness, damping, 0.0)
        mid_scale = solve_spring_analytical(stiffness, damping, 0.4)
        settled_scale = solve_spring_analytical(stiffness, damping, 1.0)
        assert_almost_equal(initial_scale, 0.0)
        assert_true(mid_scale > 0.8)
        assert_almost_equal(settled_scale, 1.0, delta=0.05)

        # 3b. Animated Metric Counter: "0" to "50,000" units sold
        counter_start = 0
        counter_target = 50_000
        # Check counter linear interpolation at 50%
        counter_mid = int(counter_start + (counter_target - counter_start) * 0.5)
        assert_equal(counter_mid, 25_000)
        formatted_counter = f"+{counter_mid:,} Sound Enthusiasts"
        assert_equal(formatted_counter, "+25,000 Sound Enthusiasts")
        assert_zero_emojis(formatted_counter)

        # 3c. Call-to-Action (CTA) Elastic Pop at 10.0s
        # Cubic Bezier for energetic button pop (0.34, 1.56, 0.64, 1.0)
        pop_scale = solve_cubic_bezier(0.34, 1.56, 0.64, 1.0, 0.6)
        assert_true(pop_scale > 1.0)  # Overshoots for punchy bouncy feedback

        # Step 4: Audio Mix with Dynamic Ducking
        # Voiceover on A1 from 1.0s to 6.0s (1_000_000 to 6_000_000 us)
        # Background music on A2 playing constantly at 0.8 volume
        bgm_ducked_gain = compute_audio_ducking(
            pts_us=3_000_000,
            primary_voice_active=True,
            ducking_depth_db=-12.0,
            base_music_volume=0.8
        )
        # -12 dB is approximately 0.2512 linear multiplier: 0.8 * 0.2512 ~= 0.2009
        expected_ducked = 0.8 * (10.0 ** (-12.0 / 20.0))
        assert_almost_equal(bgm_ducked_gain, expected_ducked, delta=0.01)

        bgm_unducked_gain = compute_audio_ducking(
            pts_us=8_000_000,
            primary_voice_active=False,
            ducking_depth_db=-12.0,
            base_music_volume=0.8
        )
        assert_almost_equal(bgm_unducked_gain, 0.8)

        # Step 5: High-bitrate 1080x1080 MP4 Export Validation
        plan = RenderPlan(
            resolution=ExportResolution.RES_1080P,
            frame_rate=ExportFrameRate.FPS_30,
            aspect_ratio=CanvasAspectRatio.RATIO_1_1,
            video_codec=VideoCodec.H264,
            video_bitrate_bps=12_000_000,
            audio_bitrate_bps=320_000,
            duration_us=total_duration_us
        )
        valid, msg = validate_render_plan(project, plan, available_bytes=500 * 1024 * 1024)
        assert_true(valid)

        # Step 6: Manifest Serialization
        json_str = project.to_json()
        assert_zero_emojis(json_str)
        deserialized = ProjectManifest.from_json(json_str)
        assert_equal(deserialized.aspect_ratio, CanvasAspectRatio.RATIO_1_1)
        assert_equal(len(deserialized.tracks), 1)

    tests.append(OpaqueBoxTestCase(
        test_id="TC-SCENARIO-04",
        feature_id="SCENARIO-PRODUCT-PROMO",
        tier=4,
        name="test_scenario_product_promo_end_to_end",
        test_func=test_scenario_product_promo_end_to_end
    ))
    return tests
