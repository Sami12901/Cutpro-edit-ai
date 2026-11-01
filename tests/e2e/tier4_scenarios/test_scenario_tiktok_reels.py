"""
Tier 4 Real-World Application Scenario: TikTok / Instagram Reels 9:16 Fast-Paced Video
Workflow:
1. Initialize 9:16 Vertical Portrait Project with 1080x1920 60fps defaults.
2. High-speed video assembly with Jump Cuts between speech pauses.
3. Declarative kinetic typography "Pop Bounce" title template with spring physics.
4. Word-by-word active Karaoke subtitle cue generation and highlight verification.
5. 60fps playhead timecode progression and export composition validation.
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_zero_emojis
)
from ..framework.models import (
    ProjectManifest, TimelineTrack, TimelineClip, MediaSourceInfo,
    TrackType, CanvasAspectRatio, ExportResolution, ExportFrameRate, VideoCodec,
    RenderPlan, SrtParser, solve_spring_analytical, validate_render_plan
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    def test_scenario_tiktok_reels_end_to_end():
        # Step 1: Initialize 9:16 Vertical Project
        project = ProjectManifest(
            title="Fast Reels Motion Demo",
            aspect_ratio=CanvasAspectRatio.RATIO_9_16,
            default_resolution=(1080, 1920, "1080x1920 Vertical"),
            default_frame_rate=ExportFrameRate.FPS_60
        )
        assert_zero_emojis(project.title)

        # Step 2: Assemble Video Track with Jump Cuts (3 contiguous clips)
        v_track = TimelineTrack(name="V1 Vertical Video", type=TrackType.VIDEO, z_index=0)
        c1 = TimelineClip(timeline_start_us=0, timeline_duration_us=2_000_000, source_in_us=500_000, source_out_us=2_500_000)
        c2 = TimelineClip(timeline_start_us=2_000_000, timeline_duration_us=3_000_000, source_in_us=4_000_000, source_out_us=7_000_000)
        c3 = TimelineClip(timeline_start_us=5_000_000, timeline_duration_us=2_500_000, source_in_us=8_500_000, source_out_us=11_000_000)
        v_track.clips.extend([c1, c2, c3])
        project.tracks.append(v_track)

        total_duration_us = sum(c.timeline_duration_us for c in v_track.clips)
        assert_equal(total_duration_us, 7_500_000)

        # Step 3: Kinetic Typography Template (Pop Bounce with Spring Physics)
        # Template parameter validation:
        stiffness = 350.0
        damping = 0.55
        # Evaluate scale bounce progression across discrete 60fps frames
        for frame in range(15):
            t_frac = frame / 15.0
            scale_y = solve_spring_analytical(stiffness, damping, t_frac)
            assert_true(scale_y >= 0.0)

        # Step 4: Word-by-Word Karaoke Subtitles
        reels_srt = """1
00:00:00,500 --> 00:00:02,500
Stop scrolling and watch this now!

2
00:00:03,000 --> 00:00:06,500
The best video editor for Android is finally here.
"""
        cues = SrtParser.parse(reels_srt)
        assert_equal(len(cues), 2)
        assert_true(len(cues[0].words) >= 6)

        # Test active word highlighting at PTS = 1.0s
        pts_us = 1_000_000
        active_words = [w.word for w in cues[0].words if w.start_offset_us <= pts_us < w.end_offset_us]
        assert_true(len(active_words) > 0)

        # Step 5: Render Plan Validation at 60fps
        plan = RenderPlan(
            resolution=(1080, 1920, "1080x1920"),
            frame_rate=ExportFrameRate.FPS_60,
            aspect_ratio=CanvasAspectRatio.RATIO_9_16,
            video_codec=VideoCodec.H264,
            video_bitrate_bps=15_000_000,
            audio_bitrate_bps=192_000,
            duration_us=total_duration_us
        )
        valid, msg = validate_render_plan(project, plan, available_bytes=500_000_000)
        assert_true(valid)

        # Verify Dimensions alignment
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_9_16, 1920)
        assert_equal(w, 1080)
        assert_equal(h, 1920)

    tests.append(OpaqueBoxTestCase(
        test_id="TC-SCENARIO-02",
        feature_id="SCENARIO-TIKTOK-REELS",
        tier=4,
        name="test_scenario_tiktok_reels_end_to_end",
        test_func=test_scenario_tiktok_reels_end_to_end
    ))
    return tests
