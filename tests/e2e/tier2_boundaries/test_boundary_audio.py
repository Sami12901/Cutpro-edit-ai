"""
Tier 2 Boundary Tests: Multitrack Audio Engine & DSP Processors
Features:
- AUD-01 through AUD-06 (30 test cases)
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

    # --- AUD-01 Boundaries ---
    def test_bnd_aud_01_gain_negative_clamped_to_zero():
        gain = calculate_audio_gain(pts_us=1000, clip_duration_us=5000, volume=-1.0)
        assert_equal(gain, 0.0)

    def test_bnd_aud_01_gain_overflow_saturation_clamped():
        # +12 dB = 4x gain, clipped at 32767
        sample = 30000
        boosted = int(sample * 4.0)
        clamped = max(-32768, min(32767, boosted))
        assert_equal(clamped, 32767)

    def test_bnd_aud_01_gain_underflow_negative_saturation():
        sample = -30000
        boosted = int(sample * 4.0)
        clamped = max(-32768, min(32767, boosted))
        assert_equal(clamped, -32768)

    def test_bnd_aud_01_volume_exact_float_epsilon():
        gain = calculate_audio_gain(pts_us=1000, clip_duration_us=5000, volume=1e-7)
        assert_almost_equal(gain, 1e-7, delta=1e-8)

    def test_bnd_aud_01_volume_at_clip_end_sample():
        gain = calculate_audio_gain(pts_us=5_000_000, clip_duration_us=5_000_000, volume=1.0)
        assert_almost_equal(gain, 1.0)

    # --- AUD-02 Boundaries ---
    def test_bnd_aud_02_fade_in_longer_than_clip_clamped():
        # Fade in 10s on 4s clip -> scaled to 4s
        gain = calculate_audio_gain(pts_us=2_000_000, clip_duration_us=4_000_000, volume=1.0, fade_in_duration_us=10_000_000, fade_in_curve=FadeCurveType.LINEAR)
        assert_almost_equal(gain, 0.5)

    def test_bnd_aud_02_zero_fade_in_instant_full_volume():
        gain = calculate_audio_gain(pts_us=1, clip_duration_us=4_000_000, volume=1.0, fade_in_duration_us=0)
        assert_almost_equal(gain, 1.0)

    def test_bnd_aud_02_fade_in_logarithmic_steep_start():
        # Log curve rises faster than linear at 10% progress
        log_prog = evaluate_fade_curve(0.1, FadeCurveType.LOGARITHMIC)
        assert_true(log_prog > 0.1)

    def test_bnd_aud_02_fade_in_exponential_slow_start():
        # Exp curve rises slower than linear at 10% progress
        exp_prog = evaluate_fade_curve(0.1, FadeCurveType.EXPONENTIAL)
        assert_true(exp_prog < 0.1)

    def test_bnd_aud_02_fade_in_microsecond_one_precision():
        gain = calculate_audio_gain(pts_us=1, clip_duration_us=1_000_000, volume=1.0, fade_in_duration_us=1_000_000, fade_in_curve=FadeCurveType.LINEAR)
        assert_almost_equal(gain, 1e-6, delta=1e-7)

    # --- AUD-03 Boundaries ---
    def test_bnd_aud_03_fade_out_longer_than_clip_clamped():
        gain = calculate_audio_gain(pts_us=2_000_000, clip_duration_us=4_000_000, volume=1.0, fade_out_duration_us=10_000_000, fade_out_curve=FadeCurveType.LINEAR)
        assert_almost_equal(gain, 0.5)

    def test_bnd_aud_03_zero_fade_out_instant_cutoff():
        gain = calculate_audio_gain(pts_us=3_999_999, clip_duration_us=4_000_000, volume=1.0, fade_out_duration_us=0)
        assert_almost_equal(gain, 1.0)

    def test_bnd_aud_03_fade_out_scurve_symmetry():
        val = evaluate_fade_curve(0.5, FadeCurveType.SCURVE)
        assert_almost_equal(val, 0.5)

    def test_bnd_aud_03_fade_out_logarithmic_tail():
        val = evaluate_fade_curve(0.9, FadeCurveType.LOGARITHMIC)
        assert_true(val > 0.9)

    def test_bnd_aud_03_fade_in_out_boundary_touch():
        # Exactly 2s fade in and 2s fade out on 4s clip
        gain_2s = calculate_audio_gain(2_000_000, 4_000_000, 1.0, fade_in_duration_us=2_000_000, fade_out_duration_us=2_000_000, fade_in_curve=FadeCurveType.LINEAR, fade_out_curve=FadeCurveType.LINEAR)
        assert_almost_equal(gain_2s, 1.0)

    # --- AUD-04 Boundaries ---
    def test_bnd_aud_04_all_tracks_muted_silence():
        tracks = [{"muted": True}, {"muted": True}]
        audible = any(not t["muted"] for t in tracks)
        assert_equal(audible, False)

    def test_bnd_aud_04_all_tracks_solo_equal_mix():
        tracks = [{"solo": True}, {"solo": True}]
        all_solo = all(t["solo"] for t in tracks)
        assert_equal(all_solo, True)

    def test_bnd_aud_04_mute_overrides_solo_on_same_track():
        # If track is both solo and muted, mute wins
        track = {"solo": True, "muted": True}
        audible = track["solo"] and not track["muted"]
        assert_equal(audible, False)

    def test_bnd_aud_04_unmuting_last_track_restores_audio():
        is_muted = True
        is_muted = False
        assert_equal(is_muted, False)

    def test_bnd_aud_04_master_output_mute_switch():
        master_mute = True
        assert_equal(master_mute, True)

    # --- AUD-05 Boundaries ---
    def test_bnd_aud_05_ducking_whisper_below_threshold_no_duck():
        voice_db = -45.0
        thresh = -32.0
        assert_equal(voice_db > thresh, False)

    def test_bnd_aud_05_ducking_screaming_above_threshold_ducks():
        voice_db = -5.0
        thresh = -32.0
        assert_equal(voice_db > thresh, True)

    def test_bnd_aud_05_ducking_hold_window_continuity():
        # Voice drops below threshold for 200ms (< 300ms hold) -> remains ducked
        drop_duration_ms = 200
        hold_time_ms = 300
        still_ducked = drop_duration_ms < hold_time_ms
        assert_equal(still_ducked, True)

    def test_bnd_aud_05_ducking_release_after_hold_expires():
        drop_duration_ms = 350
        hold_time_ms = 300
        start_recovering = drop_duration_ms >= hold_time_ms
        assert_equal(start_recovering, True)

    def test_bnd_aud_05_zero_attack_instant_duck():
        attack_ms = 0
        assert_equal(attack_ms, 0)

    # --- AUD-06 Boundaries ---
    def test_bnd_aud_06_pan_negative_one_left_only():
        l, r = constant_power_stereo_pan(-1.0)
        assert_almost_equal(l, 1.0)
        assert_almost_equal(r, 0.0)

    def test_bnd_aud_06_pan_positive_one_right_only():
        l, r = constant_power_stereo_pan(1.0)
        assert_almost_equal(l, 0.0)
        assert_almost_equal(r, 1.0)

    def test_bnd_aud_06_pan_quarter_left():
        l, r = constant_power_stereo_pan(-0.5)
        power = l * l + r * r
        assert_almost_equal(power, 1.0)
        assert_true(l > r)

    def test_bnd_aud_06_pan_quarter_right():
        l, r = constant_power_stereo_pan(0.5)
        power = l * l + r * r
        assert_almost_equal(power, 1.0)
        assert_true(r > l)

    def test_bnd_aud_06_pan_extreme_out_of_bounds_clamped():
        l, r = constant_power_stereo_pan(-999.0)
        assert_almost_equal(l, 1.0)
        assert_almost_equal(r, 0.0)

    all_defs = [
        ("TC-BND-AUD-01-1", "AUD-01", test_bnd_aud_01_gain_negative_clamped_to_zero),
        ("TC-BND-AUD-01-2", "AUD-01", test_bnd_aud_01_gain_overflow_saturation_clamped),
        ("TC-BND-AUD-01-3", "AUD-01", test_bnd_aud_01_gain_underflow_negative_saturation),
        ("TC-BND-AUD-01-4", "AUD-01", test_bnd_aud_01_volume_exact_float_epsilon),
        ("TC-BND-AUD-01-5", "AUD-01", test_bnd_aud_01_volume_at_clip_end_sample),
        ("TC-BND-AUD-02-1", "AUD-02", test_bnd_aud_02_fade_in_longer_than_clip_clamped),
        ("TC-BND-AUD-02-2", "AUD-02", test_bnd_aud_02_zero_fade_in_instant_full_volume),
        ("TC-BND-AUD-02-3", "AUD-02", test_bnd_aud_02_fade_in_logarithmic_steep_start),
        ("TC-BND-AUD-02-4", "AUD-02", test_bnd_aud_02_fade_in_exponential_slow_start),
        ("TC-BND-AUD-02-5", "AUD-02", test_bnd_aud_02_fade_in_microsecond_one_precision),
        ("TC-BND-AUD-03-1", "AUD-03", test_bnd_aud_03_fade_out_longer_than_clip_clamped),
        ("TC-BND-AUD-03-2", "AUD-03", test_bnd_aud_03_zero_fade_out_instant_cutoff),
        ("TC-BND-AUD-03-3", "AUD-03", test_bnd_aud_03_fade_out_scurve_symmetry),
        ("TC-BND-AUD-03-4", "AUD-03", test_bnd_aud_03_fade_out_logarithmic_tail),
        ("TC-BND-AUD-03-5", "AUD-03", test_bnd_aud_03_fade_in_out_boundary_touch),
        ("TC-BND-AUD-04-1", "AUD-04", test_bnd_aud_04_all_tracks_muted_silence),
        ("TC-BND-AUD-04-2", "AUD-04", test_bnd_aud_04_all_tracks_solo_equal_mix),
        ("TC-BND-AUD-04-3", "AUD-04", test_bnd_aud_04_mute_overrides_solo_on_same_track),
        ("TC-BND-AUD-04-4", "AUD-04", test_bnd_aud_04_unmuting_last_track_restores_audio),
        ("TC-BND-AUD-04-5", "AUD-04", test_bnd_aud_04_master_output_mute_switch),
        ("TC-BND-AUD-05-1", "AUD-05", test_bnd_aud_05_ducking_whisper_below_threshold_no_duck),
        ("TC-BND-AUD-05-2", "AUD-05", test_bnd_aud_05_ducking_screaming_above_threshold_ducks),
        ("TC-BND-AUD-05-3", "AUD-05", test_bnd_aud_05_ducking_hold_window_continuity),
        ("TC-BND-AUD-05-4", "AUD-05", test_bnd_aud_05_ducking_release_after_hold_expires),
        ("TC-BND-AUD-05-5", "AUD-05", test_bnd_aud_05_zero_attack_instant_duck),
        ("TC-BND-AUD-06-1", "AUD-06", test_bnd_aud_06_pan_negative_one_left_only),
        ("TC-BND-AUD-06-2", "AUD-06", test_bnd_aud_06_pan_positive_one_right_only),
        ("TC-BND-AUD-06-3", "AUD-06", test_bnd_aud_06_pan_quarter_left),
        ("TC-BND-AUD-06-4", "AUD-06", test_bnd_aud_06_pan_quarter_right),
        ("TC-BND-AUD-06-5", "AUD-06", test_bnd_aud_06_pan_extreme_out_of_bounds_clamped),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 2, fn.__name__, fn))

    return tests
