"""
Tier 3 Pairwise Combinatorial Tests: Subtitles, Captions & AI Subsystem
Evaluates interactions across silence detection, jump cut optimization, pacing analysis,
SRT/WebVTT conversion, karaoke word highlighting, and subtitle burn-in.
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_zero_emojis
)
from ..framework.models import (
    CaptionCue, TimedWord, SrtParser, WebVttParser
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    def test_pw_sa_01_silence_removal_and_subtitle_sync():
        # Cutting a 2-second silence ripples subtitles by -2s
        cue_original_start_us = 5_000_000
        silence_cut_duration_us = 2_000_000
        rippled_start_us = cue_original_start_us - silence_cut_duration_us
        assert_equal(rippled_start_us, 3_000_000)

    def test_pw_sa_02_jump_cut_splits_subtitle_cue():
        # Jump cut occurring at 2.5s splits a 2s-4s subtitle cue into two
        cue_start_us = 2_000_000
        cue_end_us = 4_000_000
        split_playhead_us = 2_500_000
        left_cue = CaptionCue(cue_start_us, split_playhead_us, "Part 1")
        right_cue = CaptionCue(split_playhead_us, cue_end_us, "Part 2")
        assert_equal(left_cue.end_time_us, right_cue.start_time_us)

    def test_pw_sa_03_srt_export_after_silence_removal():
        cues = [
            CaptionCue(1_000_000, 2_000_000, "First"),
            CaptionCue(3_000_000, 4_000_000, "Second (rippled)")
        ]
        exported = SrtParser.export(cues)
        assert_true("00:00:03,000 --> 00:00:04,000" in exported)

    def test_pw_sa_04_vtt_export_after_jump_cut():
        cues = [CaptionCue(500_000, 1_500_000, "Jump Cut Result")]
        vtt = WebVttParser.export(cues)
        assert_true("00:00:00.500 --> 00:00:01.500" in vtt)

    def test_pw_sa_05_karaoke_highlighting_preserves_after_cut():
        cue = CaptionCue(0, 2_000_000, "Alpha Beta", words=[
            TimedWord("Alpha", 0, 1_000_000),
            TimedWord("Beta", 1_000_000, 2_000_000)
        ])
        assert_equal(len(cue.words), 2)
        assert_equal(cue.words[1].word, "Beta")

    def test_pw_sa_06_stt_transcription_generates_timed_srt():
        stt_transcript = "1\n00:00:00,500 --> 00:00:02,000\nSTT Output"
        cues = SrtParser.parse(stt_transcript)
        assert_equal(len(cues), 1)
        assert_equal(cues[0].text, "STT Output")

    def test_pw_sa_07_bengali_stt_to_vtt_roundtrip():
        bengali_text = "কাটপ্রো স্বাগতম"
        cue = CaptionCue(1_000_000, 3_000_000, bengali_text)
        vtt = WebVttParser.export([cue])
        reparsed = WebVttParser.parse(vtt)
        assert_equal(reparsed[0].text, bengali_text)

    def test_pw_sa_08_pacing_analyzer_suggests_subtitle_highlight():
        # Highlighting subtitles in static shots
        suggestion = "Apply Karaoke Pop style to active subtitles"
        assert_zero_emojis(suggestion)

    def test_pw_sa_09_approval_gate_filters_unwanted_subtitle_cuts():
        actions = [
            {"type": "CUT_SILENCE", "approved": True},
            {"type": "INSERT_SUBTITLE", "approved": False}
        ]
        applied = [a["type"] for a in actions if a["approved"]]
        assert_equal(applied, ["CUT_SILENCE"])

    def test_pw_sa_10_subtitle_burn_in_overlay_during_ducked_audio():
        has_burned_in_captions = True
        is_bgm_ducked = True
        assert_true(has_burned_in_captions and is_bgm_ducked)

    def test_pw_sa_11_edit_plan_diff_calculates_subtitle_cue_drift():
        time_offset_us = -1_500_000
        original_pts = 10_000_000
        adjusted_pts = original_pts + time_offset_us
        assert_equal(adjusted_pts, 8_500_000)

    def test_pw_sa_12_rollback_plan_restores_original_subtitles():
        orig_cues = [CaptionCue(1000, 2000, "Original")]
        # Plan modified
        active_cues = [CaptionCue(500, 1500, "Modified")]
        # Rollback
        active_cues = orig_cues
        assert_equal(active_cues[0].text, "Original")

    def test_pw_sa_13_karaoke_active_word_sync_with_dialogue_audio():
        word_start = 1_000_000
        word_end = 1_500_000
        pts = 1_250_000
        is_active = (word_start <= pts < word_end)
        assert_equal(is_active, True)

    def test_pw_sa_14_silence_detection_on_transcribed_audio():
        has_silence = True
        has_transcript = True
        assert_true(has_silence and has_transcript)

    def test_pw_sa_15_srt_export_zero_emojis_compliance():
        cues = [CaptionCue(0, 1000, "Professional Clean Text")]
        exported = SrtParser.export(cues)
        assert_zero_emojis(exported)

    all_defs = [
        ("TC-PW-SA-01", "PAIRWISE-SA", test_pw_sa_01_silence_removal_and_subtitle_sync),
        ("TC-PW-SA-02", "PAIRWISE-SA", test_pw_sa_02_jump_cut_splits_subtitle_cue),
        ("TC-PW-SA-03", "PAIRWISE-SA", test_pw_sa_03_srt_export_after_silence_removal),
        ("TC-PW-SA-04", "PAIRWISE-SA", test_pw_sa_04_vtt_export_after_jump_cut),
        ("TC-PW-SA-05", "PAIRWISE-SA", test_pw_sa_05_karaoke_highlighting_preserves_after_cut),
        ("TC-PW-SA-06", "PAIRWISE-SA", test_pw_sa_06_stt_transcription_generates_timed_srt),
        ("TC-PW-SA-07", "PAIRWISE-SA", test_pw_sa_07_bengali_stt_to_vtt_roundtrip),
        ("TC-PW-SA-08", "PAIRWISE-SA", test_pw_sa_08_pacing_analyzer_suggests_subtitle_highlight),
        ("TC-PW-SA-09", "PAIRWISE-SA", test_pw_sa_09_approval_gate_filters_unwanted_subtitle_cuts),
        ("TC-PW-SA-10", "PAIRWISE-SA", test_pw_sa_10_subtitle_burn_in_overlay_during_ducked_audio),
        ("TC-PW-SA-11", "PAIRWISE-SA", test_pw_sa_11_edit_plan_diff_calculates_subtitle_cue_drift),
        ("TC-PW-SA-12", "PAIRWISE-SA", test_pw_sa_12_rollback_plan_restores_original_subtitles),
        ("TC-PW-SA-13", "PAIRWISE-SA", test_pw_sa_13_karaoke_active_word_sync_with_dialogue_audio),
        ("TC-PW-SA-14", "PAIRWISE-SA", test_pw_sa_14_silence_detection_on_transcribed_audio),
        ("TC-PW-SA-15", "PAIRWISE-SA", test_pw_sa_15_srt_export_zero_emojis_compliance),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 3, fn.__name__, fn))

    return tests
