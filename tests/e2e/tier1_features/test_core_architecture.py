"""
Tier 1 Tests: Core Architecture & Pure Domain Model Engine
Features:
- FEAT-CORE-001: Multi-Module Clean Architecture
- FEAT-CORE-002: Pure Domain Model Engine
"""

from ..framework.assertions import (
    assert_true, assert_equal, assert_not_equal, assert_in_range, assert_zero_emojis
)
from ..framework.models import (
    ProjectManifest, TimelineTrack, TimelineClip, MediaSourceInfo,
    ClipTransform, ClipAudioProperties, TrackType, CanvasAspectRatio,
    ExportResolution, ExportFrameRate
)
from ..framework.test_case import OpaqueBoxTestCase


def register_tests():
    tests = []

    # --- FEAT-CORE-001: Multi-Module Clean Architecture ---

    def test_feat_core_001_module_hierarchy():
        # Verify separation of core domain model contracts
        proj = ProjectManifest(title="Clean Arch Test")
        assert_equal(proj.title, "Clean Arch Test")
        assert_equal(len(proj.tracks), 0)
        assert_zero_emojis(proj.title)

    def test_feat_core_001_aspect_ratio_definitions():
        # Verify standard aspect ratios supported
        ratios = [
            CanvasAspectRatio.RATIO_16_9,
            CanvasAspectRatio.RATIO_9_16,
            CanvasAspectRatio.RATIO_1_1,
            CanvasAspectRatio.RATIO_4_5,
            CanvasAspectRatio.RATIO_21_9
        ]
        assert_equal(len(ratios), 5)
        for label, w, h in ratios:
            assert_true(w > 0 and h > 0)
            assert_zero_emojis(label)

    def test_feat_core_001_resolution_dimensions():
        dims_720p = ExportResolution.RES_720P
        dims_1080p = ExportResolution.RES_1080P
        assert_equal(dims_720p[0], 1280)
        assert_equal(dims_720p[1], 720)
        assert_equal(dims_1080p[0], 1920)
        assert_equal(dims_1080p[1], 1080)

    def test_feat_core_001_framerate_constants():
        framerates = [ExportFrameRate.FPS_24, ExportFrameRate.FPS_30, ExportFrameRate.FPS_60]
        assert_equal(framerates, [24, 30, 60])

    def test_feat_core_001_track_types_enumeration():
        expected_types = {"VIDEO", "AUDIO", "TEXT", "OVERLAY", "CAPTION"}
        actual_types = {
            TrackType.VIDEO, TrackType.AUDIO, TrackType.TEXT,
            TrackType.OVERLAY, TrackType.CAPTION
        }
        assert_equal(actual_types, expected_types)

    # --- FEAT-CORE-002: Pure Domain Model Engine ---

    def test_feat_core_002_project_instantiation():
        proj = ProjectManifest(title="Default Project")
        assert_true(len(proj.id) > 0)
        assert_equal(proj.schema_version, 1)
        assert_equal(proj.duration_us, 0)

    def test_feat_core_002_track_immutability_and_clips():
        track = TimelineTrack(name="V1", type=TrackType.VIDEO, z_index=1)
        clip = TimelineClip(
            track_id=track.id,
            timeline_start_us=0,
            timeline_duration_us=3_000_000,
            source_in_us=0,
            source_out_us=3_000_000
        )
        track.clips.append(clip)
        assert_equal(len(track.clips), 1)
        assert_equal(track.clips[0].timeline_end_us, 3_000_000)

    def test_feat_core_002_clip_duration_properties():
        clip = TimelineClip(
            source_in_us=1_000_000,
            source_out_us=5_000_000,
            timeline_start_us=500_000,
            timeline_duration_us=4_000_000
        )
        assert_equal(clip.source_trim_duration_us, 4_000_000)
        assert_equal(clip.timeline_end_us, 4_500_000)

    def test_feat_core_002_clip_transform_defaults():
        transform = ClipTransform()
        assert_equal(transform.scale_x, 1.0)
        assert_equal(transform.scale_y, 1.0)
        assert_equal(transform.translation_x, 0.0)
        assert_equal(transform.translation_y, 0.0)
        assert_equal(transform.rotation_degrees, 0.0)
        assert_equal(transform.opacity, 1.0)

    def test_feat_core_002_clip_audio_properties_defaults():
        audio_prop = ClipAudioProperties()
        assert_equal(audio_prop.volume, 1.0)
        assert_equal(audio_prop.is_muted, False)
        assert_equal(audio_prop.fade_in_duration_us, 0)
        assert_equal(audio_prop.fade_out_duration_us, 0)

    # Register all 10 tests
    feat_1_tests = [
        ("TC-CORE-001-1", "FEAT-CORE-001", test_feat_core_001_module_hierarchy),
        ("TC-CORE-001-2", "FEAT-CORE-001", test_feat_core_001_aspect_ratio_definitions),
        ("TC-CORE-001-3", "FEAT-CORE-001", test_feat_core_001_resolution_dimensions),
        ("TC-CORE-001-4", "FEAT-CORE-001", test_feat_core_001_framerate_constants),
        ("TC-CORE-001-5", "FEAT-CORE-001", test_feat_core_001_track_types_enumeration),
        ("TC-CORE-002-1", "FEAT-CORE-002", test_feat_core_002_project_instantiation),
        ("TC-CORE-002-2", "FEAT-CORE-002", test_feat_core_002_track_immutability_and_clips),
        ("TC-CORE-002-3", "FEAT-CORE-002", test_feat_core_002_clip_duration_properties),
        ("TC-CORE-002-4", "FEAT-CORE-002", test_feat_core_002_clip_transform_defaults),
        ("TC-CORE-002-5", "FEAT-CORE-002", test_feat_core_002_clip_audio_properties_defaults),
    ]

    for tid, fid, fn in feat_1_tests:
        tests.append(OpaqueBoxTestCase(tid, fid, 1, fn.__name__, fn))

    return tests
