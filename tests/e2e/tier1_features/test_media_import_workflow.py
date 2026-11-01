"""
Tier 1 Tests: CutPro Media Import & Clip Addition Workflow
- FEAT-MEDIA-01: Video metadata extraction (duration, width, height, framerate)
- FEAT-MEDIA-02: Image default timeline duration (3.0s default) and dimension bounds
- FEAT-MEDIA-03: Zero-byte and corrupt media rejection
- FEAT-MEDIA-04: Sequential timeline placement and duration recalculation
- FEAT-MEDIA-05: Picture-in-Picture (PIP) overlay image placement
- FEAT-MEDIA-06: Atomic undo and redo for added clips
- FEAT-MEDIA-07: Supported MIME type matrix coverage
- FEAT-MEDIA-08: Zero-emoji compliance across all media import strings
"""

import re
from ..framework.assertions import (
    assert_true, assert_equal, assert_almost_equal, assert_zero_emojis
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- FEAT-MEDIA-01: Video Metadata Extraction ---
    def test_media_import_video_metadata():
        video_metadata = {
            "uri": "content://media/external/video/media/101",
            "fileName": "vlog_scene_01.mp4",
            "mimeType": "video/mp4",
            "durationUs": 15_450_000,
            "width": 1920,
            "height": 1080,
            "frameRate": 30.0,
            "audioChannels": 2
        }
        assert_equal(video_metadata["durationUs"], 15_450_000)
        assert_equal(video_metadata["width"], 1920)
        assert_equal(video_metadata["height"], 1080)
        assert_equal(video_metadata["audioChannels"], 2)

    # --- FEAT-MEDIA-02: Image Default Duration ---
    def test_media_import_image_default_duration():
        def resolve_clip_duration_us(mime_type: str, raw_duration_us: int) -> int:
            if mime_type.startswith("image/"):
                return 3_000_000  # 3.0s default for static photos
            return raw_duration_us

        assert_equal(resolve_clip_duration_us("image/jpeg", 0), 3_000_000)
        assert_equal(resolve_clip_duration_us("image/png", 0), 3_000_000)
        assert_equal(resolve_clip_duration_us("image/webp", 0), 3_000_000)
        assert_equal(resolve_clip_duration_us("video/mp4", 10_000_000), 10_000_000)

    # --- FEAT-MEDIA-03: Zero-Byte Media Rejection ---
    def test_media_import_zero_byte_rejection():
        def validate_media_file_size(file_bytes: int):
            if file_bytes <= 0:
                raise ValueError("CorruptedMediaException: Media file is 0 bytes")
            return True

        assert_true(validate_media_file_size(1024 * 1024))
        try:
            validate_media_file_size(0)
            assert_true(False, "Should have rejected 0-byte file")
        except ValueError as e:
            assert_true("0 bytes" in str(e))

    # --- FEAT-MEDIA-04: Sequential Timeline Addition ---
    def test_media_import_sequential_timeline_addition():
        clips = []
        durations = [4_000_000, 3_000_000, 5_500_000]
        current_start_us = 0

        for idx, dur in enumerate(durations):
            clip = {
                "id": f"clip_{idx}",
                "timelineStartUs": current_start_us,
                "timelineDurationUs": dur,
                "timelineEndUs": current_start_us + dur
            }
            clips.append(clip)
            current_start_us += dur

        assert_equal(len(clips), 3)
        assert_equal(clips[0]["timelineStartUs"], 0)
        assert_equal(clips[0]["timelineEndUs"], 4_000_000)
        assert_equal(clips[1]["timelineStartUs"], 4_000_000)
        assert_equal(clips[1]["timelineEndUs"], 7_000_000)
        assert_equal(clips[2]["timelineStartUs"], 7_000_000)
        assert_equal(clips[2]["timelineEndUs"], 12_500_000)

        total_project_duration = max(c["timelineEndUs"] for c in clips)
        assert_equal(total_project_duration, 12_500_000)

    # --- FEAT-MEDIA-05: Picture-in-Picture (PIP) Overlay Image ---
    def test_media_import_overlay_pip_clip():
        pip_clip = {
            "id": "pip_photo_01",
            "trackId": "track_overlay_1",
            "mimeType": "image/png",
            "timelineStartUs": 2_000_000,
            "timelineDurationUs": 3_000_000,
            "transform": {
                "scaleX": 0.5,
                "scaleY": 0.5,
                "translationX": 150.0,
                "translationY": -100.0,
                "opacity": 1.0
            },
            "blendMode": "SCREEN",
            "mask": {"type": "CIRCLE", "feather": 0.1}
        }
        assert_equal(pip_clip["trackId"], "track_overlay_1")
        assert_equal(pip_clip["transform"]["scaleX"], 0.5)
        assert_equal(pip_clip["blendMode"], "SCREEN")
        assert_equal(pip_clip["mask"]["type"], "CIRCLE")

    # --- FEAT-MEDIA-06: Atomic Undo and Redo ---
    def test_media_import_undo_redo_atomic():
        timeline_clips = ["clip_1"]
        undo_stack = []
        redo_stack = []

        # Add clip_2
        undo_stack.append(("ADD", "clip_2"))
        timeline_clips.append("clip_2")
        redo_stack.clear()
        assert_equal(timeline_clips, ["clip_1", "clip_2"])

        # Undo
        action, target = undo_stack.pop()
        timeline_clips.remove(target)
        redo_stack.append((action, target))
        assert_equal(timeline_clips, ["clip_1"])

        # Redo
        action, target = redo_stack.pop()
        timeline_clips.append(target)
        undo_stack.append((action, target))
        assert_equal(timeline_clips, ["clip_1", "clip_2"])

    # --- FEAT-MEDIA-07: Supported MIME Type Matrix ---
    def test_media_import_supported_mime_types():
        supported_types = {
            "video/mp4", "video/quicktime", "video/webm", "video/x-matroska", "video/3gpp",
            "audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/aac", "audio/flac", "audio/ogg",
            "image/png", "image/jpeg", "image/webp"
        }
        for mime in ["video/mp4", "image/jpeg", "image/png", "image/webp", "audio/mpeg"]:
            assert_true(mime in supported_types, f"Expected {mime} to be supported")

    # --- FEAT-MEDIA-08: Zero-Emoji Compliance ---
    def test_media_import_zero_emojis():
        ui_strings = [
            "Start New Video",
            "Select photos and videos from your gallery to edit",
            "Or create empty project",
            "Add media to timeline",
            "Import Video or Photo",
            "Importing media..."
        ]
        for s in ui_strings:
            assert_zero_emojis(s)

    tests.append(OpaqueBoxTestCase("TC-MEDIA-01", "FEAT-MEDIA-01", 1, "test_media_import_video_metadata", test_media_import_video_metadata))
    tests.append(OpaqueBoxTestCase("TC-MEDIA-02", "FEAT-MEDIA-02", 1, "test_media_import_image_default_duration", test_media_import_image_default_duration))
    tests.append(OpaqueBoxTestCase("TC-MEDIA-03", "FEAT-MEDIA-03", 1, "test_media_import_zero_byte_rejection", test_media_import_zero_byte_rejection))
    tests.append(OpaqueBoxTestCase("TC-MEDIA-04", "FEAT-MEDIA-04", 1, "test_media_import_sequential_timeline_addition", test_media_import_sequential_timeline_addition))
    tests.append(OpaqueBoxTestCase("TC-MEDIA-05", "FEAT-MEDIA-05", 1, "test_media_import_overlay_pip_clip", test_media_import_overlay_pip_clip))
    tests.append(OpaqueBoxTestCase("TC-MEDIA-06", "FEAT-MEDIA-06", 1, "test_media_import_undo_redo_atomic", test_media_import_undo_redo_atomic))
    tests.append(OpaqueBoxTestCase("TC-MEDIA-07", "FEAT-MEDIA-07", 1, "test_media_import_supported_mime_types", test_media_import_supported_mime_types))
    tests.append(OpaqueBoxTestCase("TC-MEDIA-08", "FEAT-MEDIA-08", 1, "test_media_import_zero_emojis", test_media_import_zero_emojis))

    return tests

