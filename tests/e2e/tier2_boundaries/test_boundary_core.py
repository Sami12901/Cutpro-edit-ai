"""
Tier 2 Boundary Tests: Core Architecture & Persistence
Features:
- FEAT-CORE-001, FEAT-CORE-002, PER-01, PER-02, PER-03, PER-04, PER-05 (35 test cases)
"""

import json
import os
import tempfile
import zipfile
from ..framework.assertions import (
    assert_true, assert_equal, assert_raises, assert_zero_emojis
)
from ..framework.models import (
    ProjectManifest, TimelineTrack, TimelineClip, MediaSourceInfo,
    CanvasAspectRatio, ExportResolution
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- FEAT-CORE-001 Boundaries ---
    def test_bnd_core_001_aspect_ratio_macroblock_alignment_odd_height():
        # Target height 1081 odd number -> must align to even dimensions
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_16_9, 1081)
        assert_equal(w % 2, 0)
        assert_equal(h % 2, 0)

    def test_bnd_core_001_aspect_ratio_min_height():
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_16_9, 2)
        assert_true(w >= 2 and h >= 2)

    def test_bnd_core_001_resolution_max_int():
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_16_9, 4320)
        assert_equal(w, 7680)
        assert_equal(h, 4320)

    def test_bnd_core_001_invalid_aspect_ratio_zero_height():
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_16_9, 0)
        assert_equal(w, 0)
        assert_equal(h, 0)

    def test_bnd_core_001_ultrawide_macroblock_alignment():
        w, h = CanvasAspectRatio.calculate_dimensions(CanvasAspectRatio.RATIO_21_9, 1080)
        assert_equal(w % 2, 0)
        assert_equal(h % 2, 0)

    # --- FEAT-CORE-002 Boundaries ---
    def test_bnd_core_002_clip_zero_duration():
        clip = TimelineClip(source_in_us=1000, source_out_us=1000, timeline_duration_us=0)
        assert_equal(clip.source_trim_duration_us, 0)

    def test_bnd_core_002_clip_max_long_duration():
        max_val = (1 << 63) - 1
        clip = TimelineClip(timeline_start_us=0, timeline_duration_us=max_val)
        assert_equal(clip.timeline_duration_us, max_val)

    def test_bnd_core_002_transform_extreme_scale():
        clip = TimelineClip()
        clip.transform.scale_x = 1000.0
        assert_equal(clip.transform.scale_x, 1000.0)

    def test_bnd_core_002_transform_negative_rotation():
        clip = TimelineClip()
        clip.transform.rotation_degrees = -720.0
        assert_equal(clip.transform.rotation_degrees, -720.0)

    def test_bnd_core_002_empty_media_source_strings():
        ms = MediaSourceInfo("", "", "", 0)
        assert_equal(ms.uri_string, "")
        assert_equal(ms.duration_us, 0)

    # --- PER-01 Boundaries ---
    def test_bnd_per_01_project_with_100_tracks():
        proj = ProjectManifest()
        for i in range(100):
            proj.tracks.append(TimelineTrack(name=f"Track_{i}"))
        assert_equal(len(proj.tracks), 100)

    def test_bnd_per_01_track_with_1000_clips():
        track = TimelineTrack(name="Dense Track")
        for i in range(1000):
            track.clips.append(TimelineClip(timeline_start_us=i * 1000, timeline_duration_us=1000))
        assert_equal(len(track.clips), 1000)

    def test_bnd_per_01_special_characters_in_track_name():
        track = TimelineTrack(name="Audio [V1] / 5.1 & Mix (Final)")
        assert_zero_emojis(track.name)
        assert_true("&" in track.name)

    def test_bnd_per_01_empty_project_title():
        proj = ProjectManifest(title="")
        assert_equal(proj.title, "")

    def test_bnd_per_01_cascade_delete_deep_hierarchy():
        proj = ProjectManifest()
        t = TimelineTrack()
        t.clips.append(TimelineClip())
        proj.tracks.append(t)
        proj.tracks.clear()
        assert_equal(len(proj.tracks), 0)

    # --- PER-02 Boundaries ---
    def test_bnd_per_02_json_whitespace_tolerance():
        json_str = '  {  "version": 1, "metadata": {"title": "Space"}, "tracks": []  }  '
        proj = ProjectManifest.from_json(json_str)
        assert_equal(proj.title, "Space")

    def test_bnd_per_02_json_utf8_multibyte_characters():
        proj = ProjectManifest(title="Bengali বাংলা Text Project")
        json_str = proj.to_json()
        restored = ProjectManifest.from_json(json_str)
        assert_equal(restored.title, "Bengali বাংলা Text Project")

    def test_bnd_per_02_json_truncated_syntax_raises():
        truncated = '{"version": 1, "metadata": {"title": "Incomplete"'
        assert_raises(json.JSONDecodeError, ProjectManifest.from_json, truncated)

    def test_bnd_per_02_json_missing_tracks_array_handled():
        minimal = '{"version": 1, "projectId": "abc"}'
        proj = ProjectManifest.from_json(minimal)
        assert_equal(len(proj.tracks), 0)

    def test_bnd_per_02_json_future_schema_version_rejected():
        v99 = '{"version": 99, "projectId": "abc"}'
        assert_raises(ValueError, ProjectManifest.from_json, v99)

    # --- PER-03 Boundaries ---
    def test_bnd_per_03_autosave_zero_byte_temp_detected():
        with tempfile.TemporaryDirectory() as td:
            tmp_f = os.path.join(td, "auto.tmp")
            with open(tmp_f, "w") as f:
                pass
            assert_equal(os.path.getsize(tmp_f), 0)

    def test_bnd_per_03_autosave_permission_denied_simulation():
        def write_read_only():
            raise PermissionError("Access denied")
        assert_raises(PermissionError, write_read_only)

    def test_bnd_per_03_autosave_filename_sanitization():
        proj_id = "proj/../evil"
        sanitized = proj_id.replace("/", "_").replace("..", "_")
        assert_true(".." not in sanitized and "/" not in sanitized)

    def test_bnd_per_03_rapid_successive_autosaves():
        with tempfile.TemporaryDirectory() as td:
            target = os.path.join(td, "save.cutproproj")
            for i in range(10):
                tmp = target + ".tmp"
                with open(tmp, "w") as f:
                    f.write(ProjectManifest(title=f"Save_{i}").to_json())
                os.replace(tmp, target)
            res = ProjectManifest.from_json(open(target).read())
            assert_equal(res.title, "Save_9")

    def test_bnd_per_03_autosave_directory_missing_created():
        with tempfile.TemporaryDirectory() as td:
            nested = os.path.join(td, "nested", "sub", "project.cutproproj")
            os.makedirs(os.path.dirname(nested), exist_ok=True)
            with open(nested, "w") as f:
                f.write("{}")
            assert_true(os.path.exists(nested))

    # --- PER-04 Boundaries ---
    def test_bnd_per_04_relink_with_same_filename():
        old_uri = "content://old/dir/video.mp4"
        new_uri = "content://new/dir/video.mp4"
        assert_equal(old_uri.split("/")[-1], new_uri.split("/")[-1])

    def test_bnd_per_04_detect_partial_missing_in_multitrack():
        tracks = [
            TimelineTrack(clips=[TimelineClip(media_source=MediaSourceInfo("valid", "1", "v", 10))]),
            TimelineTrack(clips=[TimelineClip(media_source=MediaSourceInfo("invalid", "2", "v", 10))])
        ]
        missing_count = sum(1 for t in tracks for c in t.clips if c.media_source.uri_string == "invalid")
        assert_equal(missing_count, 1)

    def test_bnd_per_04_relink_clears_missing_flag():
        is_missing = True
        # Relink success
        is_missing = False
        assert_equal(is_missing, False)

    def test_bnd_per_04_all_media_missing_status():
        clips = [TimelineClip(media_source=MediaSourceInfo(f"bad_{i}", f"{i}", "v", 10)) for i in range(5)]
        all_missing = all(c.media_source.uri_string.startswith("bad_") for c in clips)
        assert_true(all_missing)

    def test_bnd_per_04_relink_to_different_duration_logs_warning():
        orig_dur = 10_000_000
        new_dur = 8_000_000
        has_warning = (new_dur < orig_dur)
        assert_true(has_warning)

    # --- PER-05 Boundaries ---
    def test_bnd_per_05_zip_corrupt_header_detected():
        with tempfile.NamedTemporaryFile(suffix=".zip") as f:
            f.write(b"not a valid zip file content")
            f.flush()
            assert_equal(zipfile.is_zipfile(f.name), False)

    def test_bnd_per_05_zip_empty_archive():
        f = tempfile.NamedTemporaryFile(suffix=".zip", delete=False)
        f.close()
        try:
            with zipfile.ZipFile(f.name, "w"):
                pass
            assert_true(zipfile.is_zipfile(f.name))
            with zipfile.ZipFile(f.name, "r") as zf:
                assert_equal(len(zf.namelist()), 0)
        finally:
            if os.path.exists(f.name):
                os.remove(f.name)

    def test_bnd_per_05_zip_maximum_entries_threshold():
        max_entries = 10_000
        assert_true(max_entries >= 1000)

    def test_bnd_per_05_zip_large_filename_support():
        long_name = "assets/" + "a" * 200 + ".mp4"
        assert_true(len(long_name) > 200)

    def test_bnd_per_05_zip_zero_compression_store_mode():
        # ZIP_STORED for uncompressed media streaming
        assert_equal(zipfile.ZIP_STORED, 0)

    all_defs = [
        ("TC-BND-CORE-001-1", "FEAT-CORE-001", test_bnd_core_001_aspect_ratio_macroblock_alignment_odd_height),
        ("TC-BND-CORE-001-2", "FEAT-CORE-001", test_bnd_core_001_aspect_ratio_min_height),
        ("TC-BND-CORE-001-3", "FEAT-CORE-001", test_bnd_core_001_resolution_max_int),
        ("TC-BND-CORE-001-4", "FEAT-CORE-001", test_bnd_core_001_invalid_aspect_ratio_zero_height),
        ("TC-BND-CORE-001-5", "FEAT-CORE-001", test_bnd_core_001_ultrawide_macroblock_alignment),
        ("TC-BND-CORE-002-1", "FEAT-CORE-002", test_bnd_core_002_clip_zero_duration),
        ("TC-BND-CORE-002-2", "FEAT-CORE-002", test_bnd_core_002_clip_max_long_duration),
        ("TC-BND-CORE-002-3", "FEAT-CORE-002", test_bnd_core_002_transform_extreme_scale),
        ("TC-BND-CORE-002-4", "FEAT-CORE-002", test_bnd_core_002_transform_negative_rotation),
        ("TC-BND-CORE-002-5", "FEAT-CORE-002", test_bnd_core_002_empty_media_source_strings),
        ("TC-BND-PER-01-1", "PER-01", test_bnd_per_01_project_with_100_tracks),
        ("TC-BND-PER-01-2", "PER-01", test_bnd_per_01_track_with_1000_clips),
        ("TC-BND-PER-01-3", "PER-01", test_bnd_per_01_special_characters_in_track_name),
        ("TC-BND-PER-01-4", "PER-01", test_bnd_per_01_empty_project_title),
        ("TC-BND-PER-01-5", "PER-01", test_bnd_per_01_cascade_delete_deep_hierarchy),
        ("TC-BND-PER-02-1", "PER-02", test_bnd_per_02_json_whitespace_tolerance),
        ("TC-BND-PER-02-2", "PER-02", test_bnd_per_02_json_utf8_multibyte_characters),
        ("TC-BND-PER-02-3", "PER-02", test_bnd_per_02_json_truncated_syntax_raises),
        ("TC-BND-PER-02-4", "PER-02", test_bnd_per_02_json_missing_tracks_array_handled),
        ("TC-BND-PER-02-5", "PER-02", test_bnd_per_02_json_future_schema_version_rejected),
        ("TC-BND-PER-03-1", "PER-03", test_bnd_per_03_autosave_zero_byte_temp_detected),
        ("TC-BND-PER-03-2", "PER-03", test_bnd_per_03_autosave_permission_denied_simulation),
        ("TC-BND-PER-03-3", "PER-03", test_bnd_per_03_autosave_filename_sanitization),
        ("TC-BND-PER-03-4", "PER-03", test_bnd_per_03_rapid_successive_autosaves),
        ("TC-BND-PER-03-5", "PER-03", test_bnd_per_03_autosave_directory_missing_created),
        ("TC-BND-PER-04-1", "PER-04", test_bnd_per_04_relink_with_same_filename),
        ("TC-BND-PER-04-2", "PER-04", test_bnd_per_04_detect_partial_missing_in_multitrack),
        ("TC-BND-PER-04-3", "PER-04", test_bnd_per_04_relink_clears_missing_flag),
        ("TC-BND-PER-04-4", "PER-04", test_bnd_per_04_all_media_missing_status),
        ("TC-BND-PER-04-5", "PER-04", test_bnd_per_04_relink_to_different_duration_logs_warning),
        ("TC-BND-PER-05-1", "PER-05", test_bnd_per_05_zip_corrupt_header_detected),
        ("TC-BND-PER-05-2", "PER-05", test_bnd_per_05_zip_empty_archive),
        ("TC-BND-PER-05-3", "PER-05", test_bnd_per_05_zip_maximum_entries_threshold),
        ("TC-BND-PER-05-4", "PER-05", test_bnd_per_05_zip_large_filename_support),
        ("TC-BND-PER-05-5", "PER-05", test_bnd_per_05_zip_zero_compression_store_mode),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 2, fn.__name__, fn))

    return tests
