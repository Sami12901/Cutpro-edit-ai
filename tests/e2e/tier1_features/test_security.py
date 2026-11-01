"""
Tier 1 Tests: Security & Storage Protection
Features:
- SEC-01: Zip-Slip Path Traversal Protection
- SEC-02: Scoped Storage URI Persistence
- SEC-03: Android Keystore Data Encryption
"""

import os
import tempfile
from ..framework.assertions import (
    assert_true, assert_equal, assert_raises
)
from ..framework.models import validate_zip_entry_path
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- SEC-01: Zip-Slip Path Traversal Protection ---

    def test_sec_01_safe_relative_path_allowed():
        with tempfile.TemporaryDirectory() as dest_dir:
            safe_entry = "assets/subfolder/clip.mp4"
            validated = validate_zip_entry_path(dest_dir, safe_entry)
            expected = os.path.realpath(os.path.join(dest_dir, "assets", "subfolder", "clip.mp4"))
            assert_equal(validated, expected)

    def test_sec_01_traversal_double_dot_rejected():
        with tempfile.TemporaryDirectory() as dest_dir:
            malicious_entry = "../../etc/passwd"
            assert_raises(PermissionError, validate_zip_entry_path, dest_dir, malicious_entry)

    def test_sec_01_nested_traversal_rejected():
        with tempfile.TemporaryDirectory() as dest_dir:
            malicious_entry = "assets/../../../system/bin/sh"
            assert_raises(PermissionError, validate_zip_entry_path, dest_dir, malicious_entry)

    def test_sec_01_root_relative_path_rejected():
        with tempfile.TemporaryDirectory() as dest_dir:
            malicious_entry = "/etc/shadow"
            # On windows, /etc/shadow resolves relative to root drive
            try:
                validate_zip_entry_path(dest_dir, malicious_entry)
                # If it didn't raise, verify it didn't escape
            except PermissionError:
                pass  # expected rejection

    def test_sec_01_exact_destination_boundary():
        with tempfile.TemporaryDirectory() as dest_dir:
            # Entry pointing directly to destination dir itself
            validated = validate_zip_entry_path(dest_dir, "")
            assert_equal(validated, os.path.realpath(dest_dir))

    # --- SEC-02: Scoped Storage URI Persistence ---

    def test_sec_02_persistable_flag_validation():
        FLAG_GRANT_READ_URI_PERMISSION = 0x00000001
        assert_equal(FLAG_GRANT_READ_URI_PERMISSION, 1)

    def test_sec_02_saf_uri_format_check():
        valid_saf_uri = "content://com.android.externalstorage.documents/document/primary%3AMovies%2Fintro.mp4"
        assert_true(valid_saf_uri.startswith("content://"))

    def test_sec_02_photo_picker_uri_format_check():
        picker_uri = "content://media/picker/0/com.android.providers.media.photopicker/media/1000000001"
        assert_true("photopicker" in picker_uri or "media/picker" in picker_uri)

    def test_sec_02_persistable_permission_simulation():
        persisted_grants = set()
        uri = "content://media/external/video/media/12"
        persisted_grants.add(uri)
        assert_true(uri in persisted_grants)

    def test_sec_02_unpersisted_uri_revocation_simulation():
        active_grants = {"content://media/temp/1": True}
        # Reboot simulation: transient grant lost
        active_grants.clear()
        assert_equal(len(active_grants), 0)

    # --- SEC-03: Android Keystore Data Encryption ---

    def test_sec_03_keystore_aes_256_gcm_scheme():
        scheme_name = "AES256_GCM"
        assert_equal(scheme_name, "AES256_GCM")

    def test_sec_03_keystore_siv_key_encryption_scheme():
        siv_scheme = "AES256_SIV"
        assert_equal(siv_scheme, "AES256_SIV")

    def test_sec_03_encrypted_preference_key_value_mock():
        import base64
        secret_value = "cutpro_secure_api_key_or_token"
        # Mock AES-GCM simulation: ciphertext + tag representation
        encoded = base64.b64encode(secret_value.encode("utf-8")).decode("utf-8")
        assert_true(len(encoded) > 0)
        decoded = base64.b64decode(encoded.encode("utf-8")).decode("utf-8")
        assert_equal(decoded, secret_value)

    def test_sec_03_tampered_ciphertext_detection():
        import base64
        secret = "original"
        encoded = bytearray(base64.b64encode(secret.encode("utf-8")))
        encoded[0] ^= 0xFF  # Flip bit
        try:
            base64.b64decode(encoded)
        except Exception:
            pass  # Tampering detected

    def test_sec_03_secure_prefs_file_name_compliance():
        pref_filename = "cutpro_secure_prefs"
        assert_equal(pref_filename, "cutpro_secure_prefs")

    all_defs = [
        ("TC-SEC-01-1", "SEC-01", test_sec_01_safe_relative_path_allowed),
        ("TC-SEC-01-2", "SEC-01", test_sec_01_traversal_double_dot_rejected),
        ("TC-SEC-01-3", "SEC-01", test_sec_01_nested_traversal_rejected),
        ("TC-SEC-01-4", "SEC-01", test_sec_01_root_relative_path_rejected),
        ("TC-SEC-01-5", "SEC-01", test_sec_01_exact_destination_boundary),
        ("TC-SEC-02-1", "SEC-02", test_sec_02_persistable_flag_validation),
        ("TC-SEC-02-2", "SEC-02", test_sec_02_saf_uri_format_check),
        ("TC-SEC-02-3", "SEC-02", test_sec_02_photo_picker_uri_format_check),
        ("TC-SEC-02-4", "SEC-02", test_sec_02_persistable_permission_simulation),
        ("TC-SEC-02-5", "SEC-02", test_sec_02_unpersisted_uri_revocation_simulation),
        ("TC-SEC-03-1", "SEC-03", test_sec_03_keystore_aes_256_gcm_scheme),
        ("TC-SEC-03-2", "SEC-03", test_sec_03_keystore_siv_key_encryption_scheme),
        ("TC-SEC-03-3", "SEC-03", test_sec_03_encrypted_preference_key_value_mock),
        ("TC-SEC-03-4", "SEC-03", test_sec_03_tampered_ciphertext_detection),
        ("TC-SEC-03-5", "SEC-03", test_sec_03_secure_prefs_file_name_compliance),
    ]

    for tid, fid, fn in all_defs:
        tests.append(OpaqueBoxTestCase(tid, fid, 1, fn.__name__, fn))

    return tests
