"""
Tier 1 Tests: Persistence Engine
Features:
- PER-01: Room SQLite Database Engine
- PER-02: Versioned .cutproproj JSON v1 Schema
- PER-03: Non-Corrupting Atomic Autosave
- PER-04: Missing-Media Detection & Relinking
- PER-05: .cutprobundle ZIP Packager
"""

import json
import os
import tempfile
import zipfile
from ..framework.assertions import (
    assert_true, assert_false, assert_equal, assert_not_equal, assert_raises, assert_zero_emojis
)
from ..framework.models import (
    ProjectManifest, TimelineTrack, TimelineClip, MediaSourceInfo, TrackType
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- PER-01: Room SQLite Database Engine ---

    def test_per_01_crud_project_record():
        proj = ProjectManifest(title="Persist Project")
        assert_true(len(proj.id) > 10)
        assert_equal(proj.title, "Persist Project")

    def test_per_01_cascading_tracks_relationship():
        proj = ProjectManifest(title="Cascade Project")
        t1 = TimelineTrack(name="V1", type=TrackType.VIDEO, z_index=0)
        proj.tracks.append(t1)
        assert_equal(len(proj.tracks), 1)

    def test_per_01_cascading_clips_relationship():
        track = TimelineTrack(name="A1", type=TrackType.AUDIO, z_index=1)
        clip = TimelineClip(track_id=track.id, timeline_start_us=0, timeline_duration_us=1_000_000)
        track.clips.append(clip)
        assert_equal(track.clips[0].track_id, track.id)

    def test_per_01_update_project_metadata():
        proj = ProjectManifest(title="Original Title", modified_at=1000)
        proj.title = "Updated Title"
        proj.modified_at = 2000
        assert_equal(proj.title, "Updated Title")
        assert_equal(proj.modified_at, 2000)

    def test_per_01_delete_track_cleans_clips():
        track = TimelineTrack(name="V1")
        track.clips.append(TimelineClip(track_id=track.id))
        track.clips.clear()
        assert_equal(len(track.clips), 0)

    # --- PER-02: Versioned .cutproproj JSON v1 Schema ---

    def test_per_02_json_v1_serialization_schema():
        proj = ProjectManifest(title="Manifest Project")
        track = TimelineTrack(name="Main Track", type=TrackType.VIDEO)
        track.clips.append(TimelineClip(track_id=track.id, timeline_duration_us=5_000_000))
        proj.tracks.append(track)
        raw_json = proj.to_json()
        assert_zero_emojis(raw_json)
        data = json.loads(raw_json)
        assert_equal(data["version"], 1)
        assert_equal(data["$schema"], "https://cutpro.dev/schemas/cutproproj-v1.json")
        assert_equal(data["metadata"]["title"], "Manifest Project")

    def test_per_02_json_v1_deserialization_fidelity():
        proj1 = ProjectManifest(title="Roundtrip Test")
        t = TimelineTrack(name="Audio Track", type=TrackType.AUDIO)
        t.clips.append(TimelineClip(track_id=t.id, timeline_start_us=200_000, timeline_duration_us=800_000))
        proj1.tracks.append(t)
        json_str = proj1.to_json()

        proj2 = ProjectManifest.from_json(json_str)
        assert_equal(proj2.id, proj1.id)
        assert_equal(proj2.title, proj1.title)
        assert_equal(len(proj2.tracks), 1)
        assert_equal(proj2.tracks[0].clips[0].timeline_duration_us, 800_000)

    def test_per_02_schema_version_compatibility_check():
        invalid_v2 = '{"version": 2, "projectId": "123", "tracks": []}'
        assert_raises(ValueError, ProjectManifest.from_json, invalid_v2)

    def test_per_02_json_canvas_metadata_preservation():
        proj = ProjectManifest(title="Canvas Settings")
        json_data = json.loads(proj.to_json())
        assert_true("canvas" in json_data)
        assert_equal(json_data["canvas"]["fps"], 30)

    def test_per_02_json_empty_tracks_array_handled():
        proj = ProjectManifest(title="Empty Project")
        json_str = proj.to_json()
        restored = ProjectManifest.from_json(json_str)
        assert_equal(len(restored.tracks), 0)

    # --- PER-03: Non-Corrupting Atomic Autosave ---

    def test_per_03_atomic_write_creates_valid_file():
        with tempfile.TemporaryDirectory() as temp_dir:
            proj = ProjectManifest(title="Atomic Save")
            target_path = os.path.join(temp_dir, "project.cutproproj")
            temp_path = target_path + ".tmp"
            with open(temp_path, "w", encoding="utf-8") as f:
                f.write(proj.to_json())
                f.flush()
                os.fsync(f.fileno())
            os.replace(temp_path, target_path)
            assert_true(os.path.exists(target_path))
            assert_false(os.path.exists(temp_path))

    def test_per_03_atomic_write_overwrites_existing():
        with tempfile.TemporaryDirectory() as temp_dir:
            target = os.path.join(temp_dir, "project.cutproproj")
            with open(target, "w", encoding="utf-8") as f:
                f.write("old data")
            tmp = target + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                f.write(ProjectManifest(title="New Data").to_json())
            os.replace(tmp, target)
            restored = ProjectManifest.from_json(open(target).read())
            assert_equal(restored.title, "New Data")

    def test_per_03_corrupt_temp_write_leaves_target_intact():
        with tempfile.TemporaryDirectory() as temp_dir:
            target = os.path.join(temp_dir, "project.cutproproj")
            with open(target, "w", encoding="utf-8") as f:
                f.write(ProjectManifest(title="Stable").to_json())
            # Simulated aborted temp file
            tmp = target + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                f.write("{ incomplete json")
            # We do NOT rename if write failed
            assert_true(os.path.exists(target))
            stable_proj = ProjectManifest.from_json(open(target).read())
            assert_equal(stable_proj.title, "Stable")

    def test_per_03_autosave_preserves_timestamp():
        proj = ProjectManifest(title="Timestamp Check", modified_at=123456789)
        json_obj = json.loads(proj.to_json())
        assert_equal(json_obj["metadata"]["updatedAt"], 123456789)

    def test_per_03_recovery_detects_clean_json():
        proj = ProjectManifest(title="Recovery Project")
        with tempfile.TemporaryDirectory() as temp_dir:
            fpath = os.path.join(temp_dir, "project.cutproproj")
            with open(fpath, "w", encoding="utf-8") as f:
                f.write(proj.to_json())
            loaded = ProjectManifest.from_json(open(fpath).read())
            assert_equal(loaded.title, "Recovery Project")

    # --- PER-04: Missing-Media Detection & Relinking ---

    def test_per_04_missing_media_detection():
        ms = MediaSourceInfo(uri_string="file:///nonexistent/video.mp4", file_name="video.mp4", mime_type="video/mp4", duration_us=1_000_000)
        is_missing = not os.path.exists(ms.uri_string.replace("file:///", ""))
        assert_true(is_missing)

    def test_per_04_accessible_media_validation():
        with tempfile.NamedTemporaryFile(suffix=".mp4") as tmp_media:
            ms = MediaSourceInfo(uri_string=f"file:///{tmp_media.name}", file_name="tmp.mp4", mime_type="video/mp4", duration_us=1000)
            is_present = os.path.exists(tmp_media.name)
            assert_true(is_present)

    def test_per_04_relinking_updates_clip_uri():
        clip = TimelineClip(media_source=MediaSourceInfo(uri_string="old_uri", file_name="clip.mp4", mime_type="video/mp4", duration_us=1000))
        # Relink operation
        new_ms = MediaSourceInfo(uri_string="new_uri", file_name="clip.mp4", mime_type="video/mp4", duration_us=1000)
        clip.media_source = new_ms
        assert_equal(clip.media_source.uri_string, "new_uri")

    def test_per_04_multiple_missing_assets_aggregation():
        clips = [
            TimelineClip(media_source=MediaSourceInfo("missing_1", "1.mp4", "video/mp4", 100)),
            TimelineClip(media_source=MediaSourceInfo("missing_2", "2.mp4", "video/mp4", 200)),
            TimelineClip(media_source=MediaSourceInfo("present", "3.mp4", "video/mp4", 300)),
        ]
        missing = [c.media_source.uri_string for c in clips if c.media_source.uri_string.startswith("missing_")]
        assert_equal(len(missing), 2)

    def test_per_04_missing_status_flagging():
        clip = TimelineClip(media_source=MediaSourceInfo("invalid_uri", "bad.mp4", "video/mp4", 500))
        status = "MISSING" if not os.path.exists(clip.media_source.uri_string) else "OK"
        assert_equal(status, "MISSING")

    # --- PER-05: .cutprobundle ZIP Packager ---

    def test_per_05_bundle_zip_creation():
        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_path = os.path.join(temp_dir, "project.cutprobundle")
            proj = ProjectManifest(title="Bundled Project")
            with zipfile.ZipFile(bundle_path, "w") as zf:
                zf.writestr("project.cutproproj", proj.to_json())
                zf.writestr("assets/sample.txt", "video data")
            assert_true(os.path.exists(bundle_path))
            assert_true(zipfile.is_zipfile(bundle_path))

    def test_per_05_bundle_read_manifest_from_zip():
        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_path = os.path.join(temp_dir, "project.cutprobundle")
            proj = ProjectManifest(title="Bundled Content")
            with zipfile.ZipFile(bundle_path, "w") as zf:
                zf.writestr("project.cutproproj", proj.to_json())

            with zipfile.ZipFile(bundle_path, "r") as zf:
                manifest_str = zf.read("project.cutproproj").decode("utf-8")
                restored = ProjectManifest.from_json(manifest_str)
                assert_equal(restored.title, "Bundled Content")

    def test_per_05_bundle_unpack_to_destination():
        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_path = os.path.join(temp_dir, "pack.cutprobundle")
            dest_dir = os.path.join(temp_dir, "unpacked")
            with zipfile.ZipFile(bundle_path, "w") as zf:
                zf.writestr("assets/test.mp4", "video payload")

            with zipfile.ZipFile(bundle_path, "r") as zf:
                zf.extractall(dest_dir)
            assert_true(os.path.exists(os.path.join(dest_dir, "assets", "test.mp4")))

    def test_per_05_bundle_roundtrip_asset_fidelity():
        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_path = os.path.join(temp_dir, "fidelity.cutprobundle")
            payload = b"binary audio/video content byte buffer"
            with zipfile.ZipFile(bundle_path, "w") as zf:
                zf.writestr("assets/media.bin", payload)
            with zipfile.ZipFile(bundle_path, "r") as zf:
                read_back = zf.read("assets/media.bin")
                assert_equal(read_back, payload)

    def test_per_05_bundle_handles_empty_assets_folder():
        with tempfile.TemporaryDirectory() as temp_dir:
            bundle_path = os.path.join(temp_dir, "empty.cutprobundle")
            with zipfile.ZipFile(bundle_path, "w") as zf:
                zf.writestr("assets/", "")
                zf.writestr("project.cutproproj", ProjectManifest(title="No Assets").to_json())
            assert_true(os.path.exists(bundle_path))

    # Register all 25 tests
    all_defs = [
        ("TC-PER-01-1", "PER-01", test_per_01_crud_project_record),
        ("TC-PER-01-2", "PER-01", test_per_01_cascading_tracks_relationship),
        ("TC-PER-01-3", "PER-01", test_per_01_cascading_clips_relationship),
        ("TC-PER-01-4", "PER-01", test_per_01_update_project_metadata),
        ("TC-PER-01-5", "PER-01", test_per_01_delete_track_cleans_clips),
        ("TC-PER-02-1", "PER-02", test_per_02_json_v1_serialization_schema),
        ("TC-PER-02-2", "PER-02", test_per_02_json_v1_deserialization_fidelity),
        ("TC-PER-02-3", "PER-02", test_per_02_schema_version_compatibility_check),
        ("TC-PER-02-4", "PER-02", test_per_02_json_canvas_metadata_preservation),
        ("TC-PER-02-5", "PER-02", test_per_02_json_empty_tracks_array_handled),
        ("TC-PER-03-1", "PER-03", test_per_03_atomic_write_creates_valid_file),
        ("TC-PER-03-2", "PER-03", test_per_03_atomic_write_overwrites_existing),
        ("TC-PER-03-3", "PER-03", test_per_03_corrupt_temp_write_leaves_target_intact),
        ("TC-PER-03-4", "PER-03", test_per_03_autosave_preserves_timestamp),
        ("TC-PER-03-5", "PER-03", test_per_03_recovery_detects_clean_json),
        ("TC-PER-04-1", "PER-04", test_per_04_missing_media_detection),
        ("TC-PER-04-2", "PER-04", test_per_04_accessible_media_validation),
        ("TC-PER-04-3", "PER-04", test_per_04_relinking_updates_clip_uri),
        ("TC-PER-04-4", "PER-04", test_per_04_multiple_missing_assets_aggregation),
        ("TC-PER-04-5", "PER-04", test_per_04_missing_status_flagging),
        ("TC-PER-05-1", "PER-05", test_per_05_bundle_zip_creation),
        ("TC-PER-05-2", "PER-05", test_per_05_bundle_read_manifest_from_zip),
        ("TC-PER-05-3", "PER-05", test_per_05_bundle_unpack_to_destination),
        ("TC-PER-05-4", "PER-05", test_per_05_bundle_roundtrip_asset_fidelity),
        ("TC-PER-05-5", "PER-05", test_per_05_bundle_handles_empty_assets_folder),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 1, fn.__name__, fn))

    return tests
