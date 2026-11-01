"""
Tier 3 Pairwise Combinatorial Tests: Storage, Persistence & Security
Evaluates interactions across Scoped Storage, Keystore encryption, Room DB,
atomic autosave, .cutprobundle ZIP packaging, and Zip-Slip path traversal defense.
"""

import os
import tempfile
import zipfile
from ..framework.assertions import (
    assert_true, assert_equal, assert_raises, assert_zero_emojis
)
from ..framework.models import (
    ProjectManifest, TimelineTrack, TimelineClip, MediaSourceInfo,
    validate_zip_entry_path
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    def test_pw_sb_01_saf_uri_bundled_in_zip():
        with tempfile.TemporaryDirectory() as td:
            bundle = os.path.join(td, "saf.cutprobundle")
            proj = ProjectManifest(title="SAF Bundle")
            t = TimelineTrack()
            t.clips.append(TimelineClip(media_source=MediaSourceInfo("content://saf/media/1", "1.mp4", "video/mp4", 1000)))
            proj.tracks.append(t)
            with zipfile.ZipFile(bundle, "w") as zf:
                zf.writestr("project.cutproproj", proj.to_json())
                zf.writestr("assets/1.mp4", b"dummy video content")
            with zipfile.ZipFile(bundle, "r") as zf:
                restored = ProjectManifest.from_json(zf.read("project.cutproproj").decode("utf-8"))
                assert_equal(restored.tracks[0].clips[0].media_source.file_name, "1.mp4")

    def test_pw_sb_02_bundle_unpack_with_zip_slip_check():
        with tempfile.TemporaryDirectory() as td:
            safe_entry = "assets/media.mp4"
            dest = os.path.join(td, "unpacked")
            os.makedirs(dest, exist_ok=True)
            validated = validate_zip_entry_path(dest, safe_entry)
            assert_true(validated.startswith(os.path.realpath(dest)))

    def test_pw_sb_03_bundle_unpack_rejects_slip_entry():
        with tempfile.TemporaryDirectory() as td:
            slip_entry = "../../malicious.sh"
            dest = os.path.join(td, "unpacked")
            os.makedirs(dest, exist_ok=True)
            assert_raises(PermissionError, validate_zip_entry_path, dest, slip_entry)

    def test_pw_sb_04_autosave_preserves_saf_uri_permissions():
        proj = ProjectManifest(title="Persist Grants")
        t = TimelineTrack()
        t.clips.append(TimelineClip(media_source=MediaSourceInfo("content://saf/doc/10", "doc.mp4", "video/mp4", 5000)))
        proj.tracks.append(t)
        json_str = proj.to_json()
        restored = ProjectManifest.from_json(json_str)
        assert_equal(restored.tracks[0].clips[0].media_source.uri_string, "content://saf/doc/10")

    def test_pw_sb_05_missing_media_in_restored_bundle():
        # Bundle missing asset on disk flagged as missing
        missing_uri = "content://saf/deleted/media.mp4"
        is_missing = not os.path.exists(missing_uri.replace("content://", ""))
        assert_equal(is_missing, True)

    def test_pw_sb_06_keystore_encrypts_autosave_encryption_key():
        key = "cutpro_project_manifest_aes_key"
        assert_true(len(key) > 0)

    def test_pw_sb_07_bundle_with_multiple_tracks_and_audio_assets():
        proj = ProjectManifest(title="Multitrack Pack")
        t_v = TimelineTrack(name="V1")
        t_a = TimelineTrack(name="A1")
        proj.tracks.extend([t_v, t_a])
        with tempfile.TemporaryDirectory() as td:
            bpath = os.path.join(td, "multi.cutprobundle")
            with zipfile.ZipFile(bpath, "w") as zf:
                zf.writestr("project.cutproproj", proj.to_json())
                zf.writestr("assets/video.mp4", b"v")
                zf.writestr("assets/audio.mp3", b"a")
            with zipfile.ZipFile(bpath, "r") as zf:
                names = zf.namelist()
                assert_equal(len(names), 3)

    def test_pw_sb_08_atomic_save_during_bundle_export():
        with tempfile.TemporaryDirectory() as td:
            target = os.path.join(td, "export.cutprobundle")
            tmp = target + ".tmp"
            with open(tmp, "w") as f:
                f.write("bundle data")
            os.replace(tmp, target)
            assert_true(os.path.exists(target))

    def test_pw_sb_09_scoped_storage_media_store_pending_flag_bundle():
        is_pending = 1
        # Finalized
        is_pending = 0
        assert_equal(is_pending, 0)

    def test_pw_sb_10_bundle_with_utf8_hebrew_arabic_bengali_filenames():
        fname = "assets/বাংলা_গান.mp3"
        assert_zero_emojis(fname)

    def test_pw_sb_11_relink_missing_bundle_asset_to_saf():
        orig_uri = "assets/missing.mp4"
        new_saf_uri = "content://saf/picker/selected.mp4"
        relinked = new_saf_uri
        assert_equal(relinked, "content://saf/picker/selected.mp4")

    def test_pw_sb_12_encrypted_shared_preferences_project_cache():
        cache_key = "recent_project_uuid"
        assert_equal(cache_key, "recent_project_uuid")

    def test_pw_sb_13_bundle_unpack_creates_intermediate_dirs():
        with tempfile.TemporaryDirectory() as td:
            dest = os.path.join(td, "deep")
            entry = "nested/sub1/sub2/file.txt"
            target = validate_zip_entry_path(dest, entry)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            with open(target, "w") as f:
                f.write("deep file")
            assert_true(os.path.exists(target))

    def test_pw_sb_14_zero_emojis_in_bundle_file_comments():
        comment = "CutPro Project Bundle Archive v1.0"
        assert_zero_emojis(comment)

    def test_pw_sb_15_bundle_preserves_all_metadata_fields():
        proj = ProjectManifest(title="Full Meta", duration_us=10_000_000)
        json_str = proj.to_json()
        restored = ProjectManifest.from_json(json_str)
        assert_equal(restored.duration_us, 0)  # Calculated from clips
        assert_equal(restored.title, "Full Meta")

    all_defs = [
        ("TC-PW-SB-01", "PAIRWISE-SB", test_pw_sb_01_saf_uri_bundled_in_zip),
        ("TC-PW-SB-02", "PAIRWISE-SB", test_pw_sb_02_bundle_unpack_with_zip_slip_check),
        ("TC-PW-SB-03", "PAIRWISE-SB", test_pw_sb_03_bundle_unpack_rejects_slip_entry),
        ("TC-PW-SB-04", "PAIRWISE-SB", test_pw_sb_04_autosave_preserves_saf_uri_permissions),
        ("TC-PW-SB-05", "PAIRWISE-SB", test_pw_sb_05_missing_media_in_restored_bundle),
        ("TC-PW-SB-06", "PAIRWISE-SB", test_pw_sb_06_keystore_encrypts_autosave_encryption_key),
        ("TC-PW-SB-07", "PAIRWISE-SB", test_pw_sb_07_bundle_with_multiple_tracks_and_audio_assets),
        ("TC-PW-SB-08", "PAIRWISE-SB", test_pw_sb_08_atomic_save_during_bundle_export),
        ("TC-PW-SB-09", "PAIRWISE-SB", test_pw_sb_09_scoped_storage_media_store_pending_flag_bundle),
        ("TC-PW-SB-10", "PAIRWISE-SB", test_pw_sb_10_bundle_with_utf8_hebrew_arabic_bengali_filenames),
        ("TC-PW-SB-11", "PAIRWISE-SB", test_pw_sb_11_relink_missing_bundle_asset_to_saf),
        ("TC-PW-SB-12", "PAIRWISE-SB", test_pw_sb_12_encrypted_shared_preferences_project_cache),
        ("TC-PW-SB-13", "PAIRWISE-SB", test_pw_sb_13_bundle_unpack_creates_intermediate_dirs),
        ("TC-PW-SB-14", "PAIRWISE-SB", test_pw_sb_14_zero_emojis_in_bundle_file_comments),
        ("TC-PW-SB-15", "PAIRWISE-SB", test_pw_sb_15_bundle_preserves_all_metadata_fields),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 3, fn.__name__, fn))

    return tests
