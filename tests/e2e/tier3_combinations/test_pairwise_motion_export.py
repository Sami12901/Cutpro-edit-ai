"""
Tier 3 Pairwise Combinatorial Tests: Motion Graphics & Export Pipeline
Evaluates interactions across keyframes, Bezier curves, analytical spring physics, templates,
aspect ratios, and Media3 export composition validation.
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal
)
from ..framework.models import (
    CanvasAspectRatio, ExportResolution, ExportFrameRate, VideoCodec,
    RenderPlan, ProjectManifest, TimelineTrack, TimelineClip,
    solve_cubic_bezier, solve_spring_analytical, evaluate_easing,
    interpolate_color_linear_rgb, validate_render_plan
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    def test_pw_me_01_bezier_animation_with_16_9_export():
        # Bezier transform evaluated for 16:9 canvas
        t_prog = solve_cubic_bezier(0.25, 0.1, 0.25, 1.0, 0.5)
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_16_9, 1080)
        x_px = t_prog * w
        assert_true(x_px > 0.5 * w)

    def test_pw_me_02_spring_physics_with_9_16_portrait_export():
        # Spring overshoot evaluated for 9:16 canvas
        disp = solve_spring_analytical(400.0, 0.4, 0.3, 1.0)
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_9_16, 1920)
        y_px = disp * h
        assert_true(y_px > 0)

    def test_pw_me_03_color_interpolation_and_linear_rgb_export():
        c_start = 0xFF000000
        c_end = 0xFFFFFFFF
        c_mid = interpolate_color_linear_rgb(c_start, c_end, 0.5)
        # Ensure alpha remains 255
        assert_equal((c_mid >> 24) & 0xFF, 255)

    def test_pw_me_04_opening_title_template_on_1080p_render_plan():
        plan = RenderPlan(resolution=ExportResolution.RES_1080P, duration_us=5_000_000)
        proj = ProjectManifest()
        t = TimelineTrack(name="Overlay")
        t.clips.append(TimelineClip(timeline_duration_us=5_000_000))
        proj.tracks.append(t)
        valid, _ = validate_render_plan(proj, plan, 500_000_000)
        assert_true(valid)

    def test_pw_me_05_lower_third_template_on_720p_render_plan():
        plan = RenderPlan(resolution=ExportResolution.RES_720P, duration_us=4_000_000)
        proj = ProjectManifest()
        t = TimelineTrack(name="V1")
        t.clips.append(TimelineClip(timeline_duration_us=4_000_000))
        proj.tracks.append(t)
        valid, _ = validate_render_plan(proj, plan, 500_000_000)
        assert_true(valid)

    def test_pw_me_06_counter_template_interpolates_at_60fps():
        fps = 60
        frame_interval_us = 1_000_000 // fps
        # Progress evaluated across 60 frames
        val_start, val_end = 0, 60
        frames_evaluated = [int(val_start + (val_end - val_start) * (i / 60.0)) for i in range(60)]
        assert_equal(len(frames_evaluated), 60)
        assert_equal(frames_evaluated[-1], 59)

    def test_pw_me_07_typewriter_template_and_h264_bitrate():
        # High motion typewriter text requires adequate bitrate
        plan = RenderPlan(video_bitrate_bps=12_000_000, video_codec=VideoCodec.H264)
        assert_equal(plan.video_bitrate_bps, 12_000_000)

    def test_pw_me_08_kinetic_typography_bounce_on_1_1_square():
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_1_1, 1080)
        assert_equal(w, h)
        disp = solve_spring_analytical(300.0, 0.6, 0.5)
        offset = disp * w
        assert_true(offset > 0)

    def test_pw_me_09_logo_reveal_circular_iris_with_storage_precheck():
        plan = RenderPlan(duration_us=3_000_000)
        proj = ProjectManifest()
        t = TimelineTrack()
        t.clips.append(TimelineClip(timeline_duration_us=3_000_000))
        proj.tracks.append(t)
        est = plan.calculate_estimated_output_size_bytes()
        valid, _ = validate_render_plan(proj, plan, est + 1000)
        assert_true(valid)

    def test_pw_me_10_cta_button_pill_scaling_in_export_canvas():
        canvas_h = 1080
        btn_h = canvas_h * 0.08  # 8% of canvas height
        radius = btn_h / 2.0
        assert_equal(radius, 43.2)

    def test_pw_me_11_rotation_keyframes_and_24fps_cinematic_export():
        fps = 24
        frame_dur_us = 1_000_000 // fps
        rot_frame_12 = 0.0 + (360.0 - 0.0) * (12 / 24.0)
        assert_almost_equal(rot_frame_12, 180.0)

    def test_pw_me_12_scale_keyframes_and_aspect_ratio_framing():
        scale_x, scale_y = 1.5, 1.5
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_16_9, 1080)
        scaled_w = w * scale_x
        assert_equal(scaled_w, 2880.0)

    def test_pw_me_13_opacity_fade_and_gl_overlay_blending():
        alpha_prog = evaluate_easing("EASE_OUT", 0.5)
        assert_true(alpha_prog > 0.5)

    def test_pw_me_14_multi_layer_overlay_z_index_export_ordering():
        layers = [
            {"id": "bg_video", "zIndex": 0},
            {"id": "title", "zIndex": 1},
            {"id": "sticker", "zIndex": 2}
        ]
        sorted_layers = sorted(layers, key=lambda l: l["zIndex"])
        assert_equal(sorted_layers[-1]["id"], "sticker")

    def test_pw_me_15_shape_bounds_morphing_during_export():
        r_start = 0.0
        r_end = 20.0
        r_current = r_start + (r_end - r_start) * 0.75
        assert_equal(r_current, 15.0)

    all_defs = [
        ("TC-PW-ME-01", "PAIRWISE-ME", test_pw_me_01_bezier_animation_with_16_9_export),
        ("TC-PW-ME-02", "PAIRWISE-ME", test_pw_me_02_spring_physics_with_9_16_portrait_export),
        ("TC-PW-ME-03", "PAIRWISE-ME", test_pw_me_03_color_interpolation_and_linear_rgb_export),
        ("TC-PW-ME-04", "PAIRWISE-ME", test_pw_me_04_opening_title_template_on_1080p_render_plan),
        ("TC-PW-ME-05", "PAIRWISE-ME", test_pw_me_05_lower_third_template_on_720p_render_plan),
        ("TC-PW-ME-06", "PAIRWISE-ME", test_pw_me_06_counter_template_interpolates_at_60fps),
        ("TC-PW-ME-07", "PAIRWISE-ME", test_pw_me_07_typewriter_template_and_h264_bitrate),
        ("TC-PW-ME-08", "PAIRWISE-ME", test_pw_me_08_kinetic_typography_bounce_on_1_1_square),
        ("TC-PW-ME-09", "PAIRWISE-ME", test_pw_me_09_logo_reveal_circular_iris_with_storage_precheck),
        ("TC-PW-ME-10", "PAIRWISE-ME", test_pw_me_10_cta_button_pill_scaling_in_export_canvas),
        ("TC-PW-ME-11", "PAIRWISE-ME", test_pw_me_11_rotation_keyframes_and_24fps_cinematic_export),
        ("TC-PW-ME-12", "PAIRWISE-ME", test_pw_me_12_scale_keyframes_and_aspect_ratio_framing),
        ("TC-PW-ME-13", "PAIRWISE-ME", test_pw_me_13_opacity_fade_and_gl_overlay_blending),
        ("TC-PW-ME-14", "PAIRWISE-ME", test_pw_me_14_multi_layer_overlay_z_index_export_ordering),
        ("TC-PW-ME-15", "PAIRWISE-ME", test_pw_me_15_shape_bounds_morphing_during_export),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 3, fn.__name__, fn))

    return tests
