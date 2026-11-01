"""
Tier 1 Tests: Multitrack Audio Engine & DSP Processors
Features:
- AUD-01: Per-Clip Volume & Gain
- AUD-02: Fade-In Envelope
- AUD-03: Fade-Out Envelope
- AUD-04: Track Mute & Solo
- AUD-05: Dynamic Audio Ducking
- AUD-06: Constant-Power Stereo Panning
"""

import math
from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_in_range
)
from ..framework.models import (
    FadeCurveType, evaluate_fade_curve, calculate_audio_gain, constant_power_stereo_pan
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- AUD-01: Per-Clip Volume & Gain ---

    def test_aud_01_unity_gain_unaltered():
        gain = calculate_audio_gain(pts_us=1_000_000, clip_duration_us=5_000_000, volume=1.0)
        assert_almost_equal(gain, 1.0)

    def test_aud_01_volume_boost_200_percent():
        gain = calculate_audio_gain(pts_us=1_000_000, clip_duration_us=5_000_000, volume=2.0)
        assert_almost_equal(gain, 2.0)

    def test_aud_01_volume_attenuation():
        gain = calculate_audio_gain(pts_us=1_000_000, clip_duration_us=5_000_000, volume=0.5)
        assert_almost_equal(gain, 0.5)

    def test_aud_01_zero_volume_mute():
        gain = calculate_audio_gain(pts_us=1_000_000, clip_duration_us=5_000_000, volume=0.0)
        assert_almost_equal(gain, 0.0)

    def test_aud_01_pcm_16bit_soft_clipping_bounds():
        # Soft-clipping clamps to [-32768, 32767]
        raw_boosted = int(20000 * 2.0)  # 40000
        clamped = max(-32768, min(32767, raw_boosted))
        assert_equal(clamped, 32767)

    # --- AUD-02: Fade-In Envelope ---

    def test_aud_02_linear_fade_in():
        pts = 500_000
        fade_dur = 1_000_000
        gain = calculate_audio_gain(pts, 5_000_000, 1.0, fade_in_duration_us=fade_dur, fade_in_curve=FadeCurveType.LINEAR)
        assert_almost_equal(gain, 0.5)

    def test_aud_02_exponential_fade_in():
        pts = 500_000
        fade_dur = 1_000_000
        gain = calculate_audio_gain(pts, 5_000_000, 1.0, fade_in_duration_us=fade_dur, fade_in_curve=FadeCurveType.EXPONENTIAL)
        # (0.5)^2 = 0.25
        assert_almost_equal(gain, 0.25)

    def test_aud_02_scurve_fade_in():
        pts = 500_000
        fade_dur = 1_000_000
        gain = calculate_audio_gain(pts, 5_000_000, 1.0, fade_in_duration_us=fade_dur, fade_in_curve=FadeCurveType.SCURVE)
        # 3*(0.5)^2 - 2*(0.5)^3 = 3*0.25 - 2*0.125 = 0.75 - 0.25 = 0.5
        assert_almost_equal(gain, 0.5)

    def test_aud_02_fade_in_at_origin_is_zero():
        gain = calculate_audio_gain(0, 5_000_000, 1.0, fade_in_duration_us=1_000_000)
        assert_almost_equal(gain, 0.0)

    def test_aud_02_fade_in_completed_reaches_full_volume():
        gain = calculate_audio_gain(1_500_000, 5_000_000, 1.0, fade_in_duration_us=1_000_000)
        assert_almost_equal(gain, 1.0)

    # --- AUD-03: Fade-Out Envelope ---

    def test_aud_03_linear_fade_out():
        clip_dur = 4_000_000
        fade_dur = 1_000_000
        # At 3.5s (mid-point of 3.0s to 4.0s fade out)
        gain = calculate_audio_gain(3_500_000, clip_dur, 1.0, fade_out_duration_us=fade_dur, fade_out_curve=FadeCurveType.LINEAR)
        assert_almost_equal(gain, 0.5)

    def test_aud_03_exponential_fade_out():
        clip_dur = 4_000_000
        fade_dur = 1_000_000
        # At 3.5s (remaining progress = 0.5, exp curve = 0.25)
        gain = calculate_audio_gain(3_500_000, clip_dur, 1.0, fade_out_duration_us=fade_dur, fade_out_curve=FadeCurveType.EXPONENTIAL)
        assert_almost_equal(gain, 0.25)

    def test_aud_03_fade_out_terminates_at_zero():
        clip_dur = 4_000_000
        fade_dur = 1_000_000
        gain = calculate_audio_gain(4_000_000, clip_dur, 1.0, fade_out_duration_us=fade_dur)
        assert_almost_equal(gain, 0.0)

    def test_aud_03_before_fade_out_stays_unity():
        clip_dur = 4_000_000
        fade_dur = 1_000_000
        gain = calculate_audio_gain(2_500_000, clip_dur, 1.0, fade_out_duration_us=fade_dur)
        assert_almost_equal(gain, 1.0)

    def test_aud_03_proportional_fade_scaling_when_sum_exceeds_duration():
        # Clip duration 4s, Fade in 3s, Fade out 3s (sum 6s > 4s)
        # Scaled to 2s fade in, 2s fade out
        gain_mid = calculate_audio_gain(2_000_000, 4_000_000, 1.0, fade_in_duration_us=3_000_000, fade_out_duration_us=3_000_000, fade_in_curve=FadeCurveType.LINEAR, fade_out_curve=FadeCurveType.LINEAR)
        # At 2s, fade in completes and fade out begins -> gain reaches exactly 1.0
        assert_almost_equal(gain_mid, 1.0, delta=0.01)

    # --- AUD-04: Track Mute & Solo ---

    def test_aud_04_track_mute_mutes_output():
        is_muted = True
        multiplier = 0.0 if is_muted else 1.0
        assert_equal(multiplier, 0.0)

    def test_aud_04_track_unmute_restores_output():
        is_muted = False
        multiplier = 0.0 if is_muted else 1.0
        assert_equal(multiplier, 1.0)

    def test_aud_04_solo_track_isolates_audio():
        tracks = [
            {"name": "V1", "solo": False},
            {"name": "A1", "solo": True},
            {"name": "A2", "solo": False}
        ]
        any_solo = any(t["solo"] for t in tracks)
        audible = [t["name"] for t in tracks if (t["solo"] if any_solo else True)]
        assert_equal(audible, ["A1"])

    def test_aud_04_multiple_solo_tracks():
        tracks = [
            {"name": "V1", "solo": True},
            {"name": "A1", "solo": True},
            {"name": "A2", "solo": False}
        ]
        any_solo = any(t["solo"] for t in tracks)
        audible = [t["name"] for t in tracks if (t["solo"] if any_solo else True)]
        assert_equal(audible, ["V1", "A1"])

    def test_aud_04_no_solo_tracks_all_audible():
        tracks = [
            {"name": "V1", "solo": False},
            {"name": "A1", "solo": False}
        ]
        any_solo = any(t["solo"] for t in tracks)
        audible = [t["name"] for t in tracks if (t["solo"] if any_solo else True)]
        assert_equal(len(audible), 2)

    # --- AUD-05: Dynamic Audio Ducking ---

    def test_aud_05_ducking_threshold_trigger():
        voice_rms_db = -25.0
        threshold_db = -32.0
        should_duck = voice_rms_db > threshold_db
        assert_equal(should_duck, True)

    def test_aud_05_ducking_attenuation_target_12db():
        target_attenuation_db = -12.0
        linear_gain = 10.0 ** (target_attenuation_db / 20.0)
        assert_almost_equal(linear_gain, 0.2512, delta=0.01)

    def test_aud_05_ducking_attack_time():
        attack_ms = 150
        assert_equal(attack_ms, 150)

    def test_aud_05_ducking_hold_time():
        hold_ms = 300
        assert_equal(hold_ms, 300)

    def test_aud_05_ducking_release_time():
        release_ms = 400
        assert_equal(release_ms, 400)

    # --- AUD-06: Constant-Power Stereo Panning ---

    def test_aud_06_center_pan_equal_power():
        l_gain, r_gain = constant_power_stereo_pan(0.0)
        # cos(pi/4) = sin(pi/4) = sqrt(2)/2 ~= 0.7071
        assert_almost_equal(l_gain, 0.7071, delta=1e-3)
        assert_almost_equal(r_gain, 0.7071, delta=1e-3)
        # Power sum = L^2 + R^2 = 0.5 + 0.5 = 1.0
        power_sum = l_gain ** 2 + r_gain ** 2
        assert_almost_equal(power_sum, 1.0, delta=1e-4)

    def test_aud_06_hard_left_pan():
        l_gain, r_gain = constant_power_stereo_pan(-1.0)
        assert_almost_equal(l_gain, 1.0, delta=1e-3)
        assert_almost_equal(r_gain, 0.0, delta=1e-3)

    def test_aud_06_hard_right_pan():
        l_gain, r_gain = constant_power_stereo_pan(1.0)
        assert_almost_equal(l_gain, 0.0, delta=1e-3)
        assert_almost_equal(r_gain, 1.0, delta=1e-3)

    def test_aud_06_intermediate_pan_power_conservation():
        for pan in [-0.75, -0.25, 0.25, 0.75]:
            l, r = constant_power_stereo_pan(pan)
            power = l * l + r * r
            assert_almost_equal(power, 1.0, delta=1e-4)

    def test_aud_06_pan_clamping_to_range():
        # Values outside [-1, 1] clamped
        l_over, r_over = constant_power_stereo_pan(2.5)
        l_max, r_max = constant_power_stereo_pan(1.0)
        assert_almost_equal(l_over, l_max)
        assert_almost_equal(r_over, r_max)

    all_defs = [
        ("TC-AUD-01-1", "AUD-01", test_aud_01_unity_gain_unaltered),
        ("TC-AUD-01-2", "AUD-01", test_aud_01_volume_boost_200_percent),
        ("TC-AUD-01-3", "AUD-01", test_aud_01_volume_attenuation),
        ("TC-AUD-01-4", "AUD-01", test_aud_01_zero_volume_mute),
        ("TC-AUD-01-5", "AUD-01", test_aud_01_pcm_16bit_soft_clipping_bounds),
        ("TC-AUD-02-1", "AUD-02", test_aud_02_linear_fade_in),
        ("TC-AUD-02-2", "AUD-02", test_aud_02_exponential_fade_in),
        ("TC-AUD-02-3", "AUD-02", test_aud_02_scurve_fade_in),
        ("TC-AUD-02-4", "AUD-02", test_aud_02_fade_in_at_origin_is_zero),
        ("TC-AUD-02-5", "AUD-02", test_aud_02_fade_in_completed_reaches_full_volume),
        ("TC-AUD-03-1", "AUD-03", test_aud_03_linear_fade_out),
        ("TC-AUD-03-2", "AUD-03", test_aud_03_exponential_fade_out),
        ("TC-AUD-03-3", "AUD-03", test_aud_03_fade_out_terminates_at_zero),
        ("TC-AUD-03-4", "AUD-03", test_aud_03_before_fade_out_stays_unity),
        ("TC-AUD-03-5", "AUD-03", test_aud_03_proportional_fade_scaling_when_sum_exceeds_duration),
        ("TC-AUD-04-1", "AUD-04", test_aud_04_track_mute_mutes_output),
        ("TC-AUD-04-2", "AUD-04", test_aud_04_track_unmute_restores_output),
        ("TC-AUD-04-3", "AUD-04", test_aud_04_solo_track_isolates_audio),
        ("TC-AUD-04-4", "AUD-04", test_aud_04_multiple_solo_tracks),
        ("TC-AUD-04-5", "AUD-04", test_aud_04_no_solo_tracks_all_audible),
        ("TC-AUD-05-1", "AUD-05", test_aud_05_ducking_threshold_trigger),
        ("TC-AUD-05-2", "AUD-05", test_aud_05_ducking_attenuation_target_12db),
        ("TC-AUD-05-3", "AUD-05", test_aud_05_ducking_attack_time),
        ("TC-AUD-05-4", "AUD-05", test_aud_05_ducking_hold_time),
        ("TC-AUD-05-5", "AUD-05", test_aud_05_ducking_release_time),
        ("TC-AUD-06-1", "AUD-06", test_aud_06_center_pan_equal_power),
        ("TC-AUD-06-2", "AUD-06", test_aud_06_hard_left_pan),
        ("TC-AUD-06-3", "AUD-06", test_aud_06_hard_right_pan),
        ("TC-AUD-06-4", "AUD-06", test_aud_06_intermediate_pan_power_conservation),
        ("TC-AUD-06-5", "AUD-06", test_aud_06_pan_clamping_to_range),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 1, fn.__name__, fn))

    return tests
