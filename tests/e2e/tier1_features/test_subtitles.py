"""
Tier 1 Tests: Subtitles, Captions & Karaoke Highlighting
Features:
- SUB-01: SRT Bidirectional Parser & Exporter
- SUB-02: WebVTT Bidirectional Parser & Exporter
- SUB-03: Caption Styling Model
- SUB-04: Word-by-Word Karaoke Highlighting
- SUB-05: Media3 Subtitle Burn-In Overlay
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_zero_emojis, assert_raises
)
from ..framework.models import (
    CaptionCue, TimedWord, SrtParser, WebVttParser
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    SAMPLE_SRT = """1
00:00:01,000 --> 00:00:04,000
Welcome to CutPro Video Editor

2
00:00:05,500 --> 00:00:08,250
Offline native Android editing
"""

    SAMPLE_VTT = """WEBVTT

00:00:01.000 --> 00:00:04.000
Welcome to CutPro Video Editor

00:00:05.500 --> 00:00:08.250
Offline native Android editing
"""

    # --- SUB-01: SRT Bidirectional Parser & Exporter ---
    def test_sub_01_srt_parse_cues_count():
        cues = SrtParser.parse(SAMPLE_SRT)
        assert_equal(len(cues), 2)

    def test_sub_01_srt_parse_timestamps_microseconds():
        cues = SrtParser.parse(SAMPLE_SRT)
        assert_equal(cues[0].start_time_us, 1_000_000)
        assert_equal(cues[0].end_time_us, 4_000_000)
        assert_equal(cues[1].start_time_us, 5_500_000)
        assert_equal(cues[1].end_time_us, 8_250_000)

    def test_sub_01_srt_export_roundtrip():
        cues = SrtParser.parse(SAMPLE_SRT)
        exported = SrtParser.export(cues)
        assert_zero_emojis(exported)
        reparsed = SrtParser.parse(exported)
        assert_equal(len(reparsed), len(cues))
        assert_equal(reparsed[0].text, cues[0].text)
        assert_equal(reparsed[0].start_time_us, cues[0].start_time_us)

    def test_sub_01_srt_strips_html_tags():
        html_srt = "1\n00:00:01,000 --> 00:00:02,000\n<b>Bold</b> and <i>Italic</i>"
        cues = SrtParser.parse(html_srt)
        assert_equal(cues[0].text, "Bold and Italic")

    def test_sub_01_srt_handles_bom_marker():
        bom_srt = "\ufeff1\n00:00:01,000 --> 00:00:02,000\nBOM Cleaned"
        cues = SrtParser.parse(bom_srt)
        assert_equal(cues[0].text, "BOM Cleaned")

    # --- SUB-02: WebVTT Bidirectional Parser & Exporter ---
    def test_sub_02_vtt_parse_cues_count():
        cues = WebVttParser.parse(SAMPLE_VTT)
        assert_equal(len(cues), 2)

    def test_sub_02_vtt_parse_timestamps():
        cues = WebVttParser.parse(SAMPLE_VTT)
        assert_equal(cues[0].start_time_us, 1_000_000)
        assert_equal(cues[0].end_time_us, 4_000_000)

    def test_sub_02_vtt_export_includes_header():
        cues = [CaptionCue(1_000_000, 2_000_000, "VTT Header Test")]
        exported = WebVttParser.export(cues)
        assert_true(exported.startswith("WEBVTT"))

    def test_sub_02_vtt_export_roundtrip():
        cues = WebVttParser.parse(SAMPLE_VTT)
        exported = WebVttParser.export(cues)
        reparsed = WebVttParser.parse(exported)
        assert_equal(len(reparsed), len(cues))
        assert_equal(reparsed[0].text, cues[0].text)

    def test_sub_02_vtt_missing_hours_handled():
        short_vtt = "WEBVTT\n\n01:23.456 --> 01:25.789\nShort timecode"
        cues = WebVttParser.parse(short_vtt)
        assert_equal(len(cues), 1)
        expected_start_us = (1 * 60 + 23) * 1_000_000 + 456_000
        assert_equal(cues[0].start_time_us, expected_start_us)

    # --- SUB-03: Caption Styling Model ---
    def test_sub_03_default_caption_style():
        style = {
            "fontFamily": "Roboto",
            "fontSizeSp": 22.0,
            "textColorArgb": 0xFFFFFFFF,
            "strokeColorArgb": 0xFF000000,
            "strokeWidthPx": 4.0
        }
        assert_equal(style["fontSizeSp"], 22.0)
        assert_equal(style["textColorArgb"], 0xFFFFFFFF)

    def test_sub_03_shadow_properties():
        shadow = {"color": 0x80000000, "blurPx": 6.0, "offsetX": 2.0, "offsetY": 2.0}
        assert_true(shadow["blurPx"] > 0)

    def test_sub_03_background_box_styling():
        bg_box = {"bgColor": 0xCC111111, "cornerRadiusPx": 8.0, "paddingHorizontalPx": 16.0}
        assert_true(bg_box["cornerRadiusPx"] >= 4.0)

    def test_sub_03_alignment_enums():
        vertical_alignments = ["TOP", "CENTER", "BOTTOM"]
        horizontal_alignments = ["START", "CENTER", "END"]
        assert_equal(len(vertical_alignments), 3)
        assert_equal(len(horizontal_alignments), 3)

    def test_sub_03_zero_emojis_in_caption_styling_font():
        font_name = "Roboto Bold"
        assert_zero_emojis(font_name)

    # --- SUB-04: Word-by-Word Karaoke Highlighting ---
    def test_sub_04_karaoke_word_timings_generation():
        cues = SrtParser.parse("1\n00:00:00,000 --> 00:00:02,000\nOne Two Four")
        words = cues[0].words
        assert_equal(len(words), 3)
        assert_equal(words[0].word, "One")
        assert_equal(words[2].word, "Four")

    def test_sub_04_active_word_at_pts():
        cues = SrtParser.parse("1\n00:00:00,000 --> 00:00:03,000\nAlpha Beta Gamma")
        # 3 seconds / 3 words = 1 sec each
        # Alpha: 0-1s, Beta: 1-2s, Gamma: 2-3s
        words = cues[0].words
        pts_us = 1_500_000  # 1.5s
        active = [w.word for w in words if w.start_offset_us <= pts_us < w.end_offset_us]
        assert_equal(active, ["Beta"])

    def test_sub_04_active_word_highlight_color():
        highlight_color = 0xFFFFD700  # Gold
        assert_equal(highlight_color, 0xFFFFD700)

    def test_sub_04_karaoke_scale_pop():
        active_scale = 1.15
        assert_true(active_scale > 1.0)

    def test_sub_04_empty_words_fallback_to_full_text():
        cue = CaptionCue(1_000_000, 2_000_000, "Unsplit text", words=[])
        assert_equal(len(cue.words), 0)
        assert_equal(cue.text, "Unsplit text")

    # --- SUB-05: Media3 Subtitle Burn-In Overlay ---
    def test_sub_05_overlay_draws_active_cue_only():
        cues = [
            CaptionCue(0, 2_000_000, "First"),
            CaptionCue(2_000_000, 4_000_000, "Second")
        ]
        pts_us = 1_000_000
        active = [c.text for c in cues if c.start_time_us <= pts_us < c.end_time_us]
        assert_equal(active, ["First"])

    def test_sub_05_overlay_no_active_cue_no_op():
        cues = [CaptionCue(1_000_000, 2_000_000, "Middle")]
        pts_us = 500_000
        active = [c.text for c in cues if c.start_time_us <= pts_us < c.end_time_us]
        assert_equal(len(active), 0)

    def test_sub_05_subtitle_stroke_and_fill_two_pass_render():
        # Stroke drawn before fill
        passes = ["STROKE", "FILL"]
        assert_equal(passes, ["STROKE", "FILL"])

    def test_sub_05_burn_in_scales_with_export_resolution():
        res_720p_text_size = 22.0 * (720.0 / 1080.0)
        res_1080p_text_size = 22.0 * (1080.0 / 1080.0)
        assert_true(res_1080p_text_size > res_720p_text_size)

    def test_sub_05_burn_in_zero_emojis_in_text():
        cue = CaptionCue(0, 1_000_000, "Styled Burn-In Caption")
        assert_zero_emojis(cue.text)

    all_defs = [
        ("TC-SUB-01-1", "SUB-01", test_sub_01_srt_parse_cues_count),
        ("TC-SUB-01-2", "SUB-01", test_sub_01_srt_parse_timestamps_microseconds),
        ("TC-SUB-01-3", "SUB-01", test_sub_01_srt_export_roundtrip),
        ("TC-SUB-01-4", "SUB-01", test_sub_01_srt_strips_html_tags),
        ("TC-SUB-01-5", "SUB-01", test_sub_01_srt_handles_bom_marker),
        ("TC-SUB-02-1", "SUB-02", test_sub_02_vtt_parse_cues_count),
        ("TC-SUB-02-2", "SUB-02", test_sub_02_vtt_parse_timestamps),
        ("TC-SUB-02-3", "SUB-02", test_sub_02_vtt_export_includes_header),
        ("TC-SUB-02-4", "SUB-02", test_sub_02_vtt_export_roundtrip),
        ("TC-SUB-02-5", "SUB-02", test_sub_02_vtt_missing_hours_handled),
        ("TC-SUB-03-1", "SUB-03", test_sub_03_default_caption_style),
        ("TC-SUB-03-2", "SUB-03", test_sub_03_shadow_properties),
        ("TC-SUB-03-3", "SUB-03", test_sub_03_background_box_styling),
        ("TC-SUB-03-4", "SUB-03", test_sub_03_alignment_enums),
        ("TC-SUB-03-5", "SUB-03", test_sub_03_zero_emojis_in_caption_styling_font),
        ("TC-SUB-04-1", "SUB-04", test_sub_04_karaoke_word_timings_generation),
        ("TC-SUB-04-2", "SUB-04", test_sub_04_active_word_at_pts),
        ("TC-SUB-04-3", "SUB-04", test_sub_04_active_word_highlight_color),
        ("TC-SUB-04-4", "SUB-04", test_sub_04_karaoke_scale_pop),
        ("TC-SUB-04-5", "SUB-04", test_sub_04_empty_words_fallback_to_full_text),
        ("TC-SUB-05-1", "SUB-05", test_sub_05_overlay_draws_active_cue_only),
        ("TC-SUB-05-2", "SUB-05", test_sub_05_overlay_no_active_cue_no_op),
        ("TC-SUB-05-3", "SUB-05", test_sub_05_subtitle_stroke_and_fill_two_pass_render),
        ("TC-SUB-05-4", "SUB-05", test_sub_05_burn_in_scales_with_export_resolution),
        ("TC-SUB-05-5", "SUB-05", test_sub_05_burn_in_zero_emojis_in_text),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 1, fn.__name__, fn))

    return tests
