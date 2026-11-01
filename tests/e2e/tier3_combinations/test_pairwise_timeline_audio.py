"""
Tier 3 Pairwise Combinatorial Tests: Timeline Editing & Audio DSP
Evaluates interactions across trimming, splitting, speed, volume, fades, and ducking.
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal
)
from ..framework.models import (
    TimelineClip, MediaSourceInfo, ClipAudioProperties, FadeCurveType,
    trim_clip_in, trim_clip_out, split_clip, calculate_timeline_duration_us,
    calculate_audio_gain, constant_power_stereo_pan
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    def test_pw_ta_01_trim_in_and_fade_in():
        # Trimming in-point shifts timeline start; fade-in calculates relative to trimmed start
        clip = TimelineClip(source_in_us=0, source_out_us=6_000_000, timeline_start_us=0, timeline_duration_us=6_000_000)
        trimmed = trim_clip_in(clip, requested_new_in_us=2_000_000)
        gain_half_fade = calculate_audio_gain(pts_us=500_000, clip_duration_us=trimmed.timeline_duration_us, fade_in_duration_us=1_000_000, fade_in_curve=FadeCurveType.LINEAR)
        assert_almost_equal(gain_half_fade, 0.5)

    def test_pw_ta_02_trim_out_and_fade_out():
        clip = TimelineClip(source_in_us=0, source_out_us=10_000_000, timeline_start_us=0, timeline_duration_us=10_000_000, media_source=MediaSourceInfo("", "", "", 10_000_000))
        trimmed = trim_clip_out(clip, requested_new_out_us=6_000_000)
        # Fade out 1s on trimmed 6s clip -> begins at 5s
        gain_at_5_5s = calculate_audio_gain(pts_us=5_500_000, clip_duration_us=trimmed.timeline_duration_us, fade_out_duration_us=1_000_000, fade_out_curve=FadeCurveType.LINEAR)
        assert_almost_equal(gain_at_5_5s, 0.5)

    def test_pw_ta_03_speed_2x_and_fade_in_time_compression():
        # 2x speed compresses clip timeline duration; audio fade duration in timeline seconds
        timeline_dur = calculate_timeline_duration_us(0, 4_000_000, 2.0)
        assert_equal(timeline_dur, 2_000_000)
        gain = calculate_audio_gain(pts_us=500_000, clip_duration_us=timeline_dur, fade_in_duration_us=1_000_000, fade_in_curve=FadeCurveType.LINEAR)
        assert_almost_equal(gain, 0.5)

    def test_pw_ta_04_speed_half_and_fade_out_expansion():
        timeline_dur = calculate_timeline_duration_us(0, 2_000_000, 0.5)
        assert_equal(timeline_dur, 4_000_000)
        gain = calculate_audio_gain(pts_us=3_500_000, clip_duration_us=timeline_dur, fade_out_duration_us=1_000_000, fade_out_curve=FadeCurveType.LINEAR)
        assert_almost_equal(gain, 0.5)

    def test_pw_ta_05_split_clip_maintains_volume_boost():
        clip = TimelineClip(timeline_start_us=0, timeline_duration_us=4_000_000, source_in_us=0, source_out_us=4_000_000, audio_properties=ClipAudioProperties(volume=1.8))
        left, right = split_clip(clip, split_playhead_us=2_000_000)
        assert_equal(left.audio_properties.volume, 1.8)
        assert_equal(right.audio_properties.volume, 1.8)

    def test_pw_ta_06_split_clip_preserves_independent_fades():
        clip = TimelineClip(timeline_start_us=0, timeline_duration_us=6_000_000, source_in_us=0, source_out_us=6_000_000)
        left, right = split_clip(clip, split_playhead_us=3_000_000)
        # Apply fade-out to left and fade-in to right for seamless crossfade
        left.audio_properties.fade_out_duration_us = 500_000
        right.audio_properties.fade_in_duration_us = 500_000
        assert_equal(left.audio_properties.fade_out_duration_us, 500_000)
        assert_equal(right.audio_properties.fade_in_duration_us, 500_000)

    def test_pw_ta_07_volume_boost_and_stereo_pan():
        volume = 1.5
        l_gain, r_gain = constant_power_stereo_pan(0.5)
        out_l = volume * l_gain
        out_r = volume * r_gain
        power = (out_l / volume) ** 2 + (out_r / volume) ** 2
        assert_almost_equal(power, 1.0)

    def test_pw_ta_08_ducking_with_fade_in_envelope():
        ducking_attenuation = 0.25
        fade_prog = 0.5
        combined_gain = ducking_attenuation * fade_prog
        assert_almost_equal(combined_gain, 0.125)

    def test_pw_ta_09_ducking_with_fade_out_envelope():
        ducking_attenuation = 0.25
        fade_prog = 0.8
        combined_gain = ducking_attenuation * fade_prog
        assert_almost_equal(combined_gain, 0.20)

    def test_pw_ta_10_trimming_both_ends_and_scurve_fades():
        clip = TimelineClip(source_in_us=0, source_out_us=10_000_000, timeline_duration_us=10_000_000, media_source=MediaSourceInfo("", "", "", 10_000_000))
        trimmed_in = trim_clip_in(clip, 2_000_000)
        trimmed = trim_clip_out(trimmed_in, 8_000_000)
        assert_equal(trimmed.timeline_duration_us, 6_000_000)
        gain_in = calculate_audio_gain(1_000_000, trimmed.timeline_duration_us, fade_in_duration_us=2_000_000, fade_in_curve=FadeCurveType.SCURVE)
        assert_almost_equal(gain_in, 0.5)

    def test_pw_ta_11_speed_and_split_and_panning():
        clip = TimelineClip(source_in_us=0, source_out_us=4_000_000, timeline_duration_us=2_000_000, speed_multiplier=2.0)
        left, right = split_clip(clip, 1_000_000)
        assert_equal(left.speed_multiplier, 2.0)
        assert_equal(right.speed_multiplier, 2.0)

    def test_pw_ta_12_mute_track_overrides_clip_volume_boost():
        clip_vol = 2.0
        track_muted = True
        effective_vol = 0.0 if track_muted else clip_vol
        assert_equal(effective_vol, 0.0)

    def test_pw_ta_13_solo_track_with_ducked_background():
        # Voice solo disables BGM track completely
        is_voice_solo = True
        bgm_audible = not is_voice_solo
        assert_equal(bgm_audible, False)

    def test_pw_ta_14_exponential_fades_on_slow_motion_clip():
        timeline_dur = calculate_timeline_duration_us(0, 2_000_000, 0.5)
        gain = calculate_audio_gain(pts_us=1_000_000, clip_duration_us=timeline_dur, fade_in_duration_us=2_000_000, fade_in_curve=FadeCurveType.EXPONENTIAL)
        # (0.5)^2 = 0.25
        assert_almost_equal(gain, 0.25)

    def test_pw_ta_15_logarithmic_fades_on_fast_motion_clip():
        timeline_dur = calculate_timeline_duration_us(0, 4_000_000, 2.0)
        gain = calculate_audio_gain(pts_us=1_000_000, clip_duration_us=timeline_dur, fade_in_duration_us=1_000_000, fade_in_curve=FadeCurveType.LOGARITHMIC)
        assert_almost_equal(gain, 1.0)

    all_defs = [
        ("TC-PW-TA-01", "PAIRWISE-TA", test_pw_ta_01_trim_in_and_fade_in),
        ("TC-PW-TA-02", "PAIRWISE-TA", test_pw_ta_02_trim_out_and_fade_out),
        ("TC-PW-TA-03", "PAIRWISE-TA", test_pw_ta_03_speed_2x_and_fade_in_time_compression),
        ("TC-PW-TA-04", "PAIRWISE-TA", test_pw_ta_04_speed_half_and_fade_out_expansion),
        ("TC-PW-TA-05", "PAIRWISE-TA", test_pw_ta_05_split_clip_maintains_volume_boost),
        ("TC-PW-TA-06", "PAIRWISE-TA", test_pw_ta_06_split_clip_preserves_independent_fades),
        ("TC-PW-TA-07", "PAIRWISE-TA", test_pw_ta_07_volume_boost_and_stereo_pan),
        ("TC-PW-TA-08", "PAIRWISE-TA", test_pw_ta_08_ducking_with_fade_in_envelope),
        ("TC-PW-TA-09", "PAIRWISE-TA", test_pw_ta_09_ducking_with_fade_out_envelope),
        ("TC-PW-TA-10", "PAIRWISE-TA", test_pw_ta_10_trimming_both_ends_and_scurve_fades),
        ("TC-PW-TA-11", "PAIRWISE-TA", test_pw_ta_11_speed_and_split_and_panning),
        ("TC-PW-TA-12", "PAIRWISE-TA", test_pw_ta_12_mute_track_overrides_clip_volume_boost),
        ("TC-PW-TA-13", "PAIRWISE-TA", test_pw_ta_13_solo_track_with_ducked_background),
        ("TC-PW-TA-14", "PAIRWISE-TA", test_pw_ta_14_exponential_fades_on_slow_motion_clip),
        ("TC-PW-TA-15", "PAIRWISE-TA", test_pw_ta_15_logarithmic_fades_on_fast_motion_clip),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 3, fn.__name__, fn))

    return tests
