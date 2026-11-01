"""
CutPro Domain Models, Invariants, and Mathematical Engines
Implements specifications from PROJECT.md:
- Pure Domain Models: Project, Track, Clip, Transform, Timecode, RenderPlan
- Timeline Math: Microsecond trimming, splitting, speed, magnetic snapping
- Keyframe & Easing: Linear, Cubic Penner, Parametric Bezier, Closed-form Spring
- Audio Engine: Volume, Fade Envelopes (Linear, Log, Exp, SCurve), Constant-Power Panning, Ducking
- Subtitles: SRT & WebVTT Bidirectional Parsers/Exporters, Karaoke Timings
- Export & Security: RenderPlan Validator, Estimated Size, Zip-Slip Guard
"""

import math
import os
import re
import json
import uuid
from dataclasses import dataclass, field, asdict
from typing import List, Optional, Tuple, Dict, Any


# --- Aspect Ratio & Export Formats ---

class CanvasAspectRatio:
    RATIO_16_9 = ("16:9 Landscape", 16, 9)
    RATIO_9_16 = ("9:16 Vertical", 9, 16)
    RATIO_1_1 = ("1:1 Square", 1, 1)
    RATIO_4_5 = ("4:5 Portrait", 4, 5)
    RATIO_21_9 = ("21:9 Ultrawide", 21, 9)

    @staticmethod
    def calculate_dimensions(ratio_tuple: Tuple[str, int, int], target_height: int) -> Tuple[int, int]:
        _, w_ratio, h_ratio = ratio_tuple
        raw_width = int((target_height * w_ratio) / h_ratio)
        # Macroblock alignment (even integer)
        width = (raw_width // 2) * 2
        height = (target_height // 2) * 2
        return (width, height)


class ExportResolution:
    RES_720P = (1280, 720, "720p HD")
    RES_1080P = (1920, 1080, "1080p Full HD")


class ExportFrameRate:
    FPS_24 = 24
    FPS_30 = 30
    FPS_60 = 60


class VideoCodec:
    H264 = "video/avc"
    H265 = "video/hevc"


class ClipFitMode:
    FIT = "FIT"
    FILL = "FILL"
    STRETCH = "STRETCH"
    CUSTOM = "CUSTOM"


class TrackType:
    VIDEO = "VIDEO"
    AUDIO = "AUDIO"
    TEXT = "TEXT"
    OVERLAY = "OVERLAY"
    CAPTION = "CAPTION"


class FadeCurveType:
    LINEAR = "LINEAR"
    EXPONENTIAL = "EXPONENTIAL"
    LOGARITHMIC = "LOGARITHMIC"
    SCURVE = "SCURVE"


# --- Pure Domain Models ---

@dataclass
class ClipTransform:
    scale_x: float = 1.0
    scale_y: float = 1.0
    translation_x: float = 0.0
    translation_y: float = 0.0
    rotation_degrees: float = 0.0
    opacity: float = 1.0


@dataclass
class ClipAudioProperties:
    volume: float = 1.0
    is_muted: bool = false if False else False
    fade_in_duration_us: int = 0
    fade_in_curve: str = FadeCurveType.SCURVE
    fade_out_duration_us: int = 0
    fade_out_curve: str = FadeCurveType.SCURVE


@dataclass
class MediaSourceInfo:
    uri_string: str
    file_name: str
    mime_type: str
    duration_us: int
    width: int = 0
    height: int = 0
    rotation_degrees: int = 0
    frame_rate: float = 0.0
    audio_channels: int = 0
    audio_sample_rate: int = 0


@dataclass
class Keyframe:
    timestamp_us: int
    value: Any
    easing_type: str = "LINEAR"
    bezier_params: Optional[Tuple[float, float, float, float]] = None


@dataclass
class TimelineClip:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    track_id: str = ""
    media_source: MediaSourceInfo = field(default_factory=lambda: MediaSourceInfo("", "", "", 0))
    timeline_start_us: int = 0
    timeline_duration_us: int = 0
    source_in_us: int = 0
    source_out_us: int = 0
    speed_multiplier: float = 1.0
    fit_mode: str = ClipFitMode.FIT
    transform: ClipTransform = field(default_factory=ClipTransform)
    audio_properties: ClipAudioProperties = field(default_factory=ClipAudioProperties)
    custom_title: Optional[str] = None
    keyframes: List[Keyframe] = field(default_factory=list)

    @property
    def timeline_end_us(self) -> int:
        return self.timeline_start_us + self.timeline_duration_us

    @property
    def source_trim_duration_us(self) -> int:
        return self.source_out_us - self.source_in_us


@dataclass
class TimelineTrack:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    name: str = "Track"
    type: str = TrackType.VIDEO
    z_index: int = 0
    is_muted: bool = False
    is_locked: bool = False
    is_visible: bool = True
    clips: List[TimelineClip] = field(default_factory=list)


@dataclass
class ProjectManifest:
    id: str = field(default_factory=lambda: str(uuid.uuid4()))
    title: str = "Untitled Project"
    created_at: int = 0
    modified_at: int = 0
    aspect_ratio: Tuple[str, int, int] = CanvasAspectRatio.RATIO_16_9
    default_resolution: Tuple[int, int, str] = ExportResolution.RES_1080P
    default_frame_rate: int = ExportFrameRate.FPS_30
    duration_us: int = 0
    tracks: List[TimelineTrack] = field(default_factory=list)
    schema_version: int = 1

    def to_json(self) -> str:
        data = {
            "$schema": "https://cutpro.dev/schemas/cutproproj-v1.json",
            "version": self.schema_version,
            "projectId": self.id,
            "metadata": {
                "title": self.title,
                "createdAt": self.created_at,
                "updatedAt": self.modified_at,
                "appVersion": "1.0.0"
            },
            "canvas": {
                "width": self.default_resolution[0],
                "height": self.default_resolution[1],
                "fps": self.default_frame_rate,
                "sampleRate": 48000,
                "aspectRatio": self.aspect_ratio[0]
            },
            "tracks": [
                {
                    "id": t.id,
                    "name": t.name,
                    "type": t.type,
                    "zIndex": t.z_index,
                    "isMuted": t.is_muted,
                    "isLocked": t.is_locked,
                    "isVisible": t.is_visible,
                    "clips": [
                        {
                            "id": c.id,
                            "trackId": c.track_id,
                            "timelineStartUs": c.timeline_start_us,
                            "timelineDurationUs": c.timeline_duration_us,
                            "sourceInUs": c.source_in_us,
                            "sourceOutUs": c.source_out_us,
                            "speedMultiplier": c.speed_multiplier,
                            "fitMode": c.fit_mode,
                            "mediaSource": {
                                "uriString": c.media_source.uri_string,
                                "fileName": c.media_source.file_name,
                                "mimeType": c.media_source.mime_type,
                                "durationUs": c.media_source.duration_us,
                                "width": c.media_source.width,
                                "height": c.media_source.height
                            }
                        }
                        for c in t.clips
                    ]
                }
                for t in self.tracks
            ]
        }
        return json.dumps(data, indent=2)

    @classmethod
    def from_json(cls, json_str: str) -> "ProjectManifest":
        obj = json.loads(json_str)
        if obj.get("version", 1) > 1:
            raise ValueError(f"Unsupported schema version: {obj.get('version')}")
        ar_str = obj.get("canvas", {}).get("aspectRatio", "16:9 Landscape")
        ar_map = {
            "16:9 Landscape": CanvasAspectRatio.RATIO_16_9,
            "9:16 Vertical": CanvasAspectRatio.RATIO_9_16,
            "1:1 Square": CanvasAspectRatio.RATIO_1_1,
            "4:5 Portrait": CanvasAspectRatio.RATIO_4_5,
            "21:9 Ultrawide": CanvasAspectRatio.RATIO_21_9
        }
        aspect_ratio = ar_map.get(ar_str, CanvasAspectRatio.RATIO_16_9)
        proj = cls(
            id=obj.get("projectId", str(uuid.uuid4())),
            title=obj.get("metadata", {}).get("title", "Untitled Project"),
            created_at=obj.get("metadata", {}).get("createdAt", 0),
            modified_at=obj.get("metadata", {}).get("updatedAt", 0),
            aspect_ratio=aspect_ratio,
            schema_version=obj.get("version", 1)
        )
        tracks = []
        for t_data in obj.get("tracks", []):
            clips = []
            for c_data in t_data.get("clips", []):
                ms_data = c_data.get("mediaSource", {})
                ms = MediaSourceInfo(
                    uri_string=ms_data.get("uriString", ""),
                    file_name=ms_data.get("fileName", ""),
                    mime_type=ms_data.get("mimeType", ""),
                    duration_us=ms_data.get("durationUs", 0),
                    width=ms_data.get("width", 0),
                    height=ms_data.get("height", 0)
                )
                clip = TimelineClip(
                    id=c_data.get("id", str(uuid.uuid4())),
                    track_id=c_data.get("trackId", t_data.get("id")),
                    media_source=ms,
                    timeline_start_us=c_data.get("timelineStartUs", 0),
                    timeline_duration_us=c_data.get("timelineDurationUs", 0),
                    source_in_us=c_data.get("sourceInUs", 0),
                    source_out_us=c_data.get("sourceOutUs", 0),
                    speed_multiplier=c_data.get("speedMultiplier", 1.0),
                    fit_mode=c_data.get("fitMode", ClipFitMode.FIT)
                )
                clips.append(clip)
            track = TimelineTrack(
                id=t_data.get("id", str(uuid.uuid4())),
                name=t_data.get("name", "Track"),
                type=t_data.get("type", TrackType.VIDEO),
                z_index=t_data.get("zIndex", 0),
                is_muted=t_data.get("isMuted", False),
                is_locked=t_data.get("isLocked", False),
                is_visible=t_data.get("isVisible", True),
                clips=clips
            )
            tracks.append(track)
        proj.tracks = tracks
        return proj


# --- Timeline Mathematical Operations ---

def calculate_timeline_duration_us(source_in_us: int, source_out_us: int, speed_multiplier: float) -> int:
    if speed_multiplier <= 0.0:
        raise ValueError("Speed multiplier must be positive")
    source_trim = max(0, source_out_us - source_in_us)
    return int(math.floor(source_trim / speed_multiplier))


def trim_clip_in(clip: TimelineClip, requested_new_in_us: int, min_duration_us: int = 100_000) -> TimelineClip:
    min_source_duration = int(min_duration_us * clip.speed_multiplier)
    max_in = clip.source_out_us - min_source_duration
    if max_in < 0:
        raise ValueError("Clip duration is smaller than minimum duration")
    clamped_in = max(0, min(requested_new_in_us, max_in))
    delta_source_us = clamped_in - clip.source_in_us
    delta_timeline_us = int(delta_source_us / clip.speed_multiplier)

    new_timeline_start_us = clip.timeline_start_us + delta_timeline_us
    new_source_trim_us = clip.source_out_us - clamped_in
    new_timeline_duration_us = int(new_source_trim_us / clip.speed_multiplier)

    return TimelineClip(
        id=clip.id,
        track_id=clip.track_id,
        media_source=clip.media_source,
        timeline_start_us=new_timeline_start_us,
        timeline_duration_us=new_timeline_duration_us,
        source_in_us=clamped_in,
        source_out_us=clip.source_out_us,
        speed_multiplier=clip.speed_multiplier,
        fit_mode=clip.fit_mode,
        transform=clip.transform,
        audio_properties=clip.audio_properties,
        custom_title=clip.custom_title,
        keyframes=clip.keyframes
    )


def trim_clip_out(clip: TimelineClip, requested_new_out_us: int, min_duration_us: int = 100_000) -> TimelineClip:
    min_source_duration = int(min_duration_us * clip.speed_multiplier)
    min_out = clip.source_in_us + min_source_duration
    max_out = clip.media_source.duration_us if clip.media_source.duration_us > 0 else requested_new_out_us
    clamped_out = max(min_out, min(requested_new_out_us, max_out))

    new_source_trim_us = clamped_out - clip.source_in_us
    new_timeline_duration_us = int(new_source_trim_us / clip.speed_multiplier)

    return TimelineClip(
        id=clip.id,
        track_id=clip.track_id,
        media_source=clip.media_source,
        timeline_start_us=clip.timeline_start_us,
        timeline_duration_us=new_timeline_duration_us,
        source_in_us=clip.source_in_us,
        source_out_us=clamped_out,
        speed_multiplier=clip.speed_multiplier,
        fit_mode=clip.fit_mode,
        transform=clip.transform,
        audio_properties=clip.audio_properties,
        custom_title=clip.custom_title,
        keyframes=clip.keyframes
    )


def split_clip(clip: TimelineClip, split_playhead_us: int, min_duration_us: int = 100_000) -> Tuple[TimelineClip, TimelineClip]:
    if split_playhead_us < clip.timeline_start_us + min_duration_us:
        raise ValueError("Split point too close to clip start")
    if split_playhead_us > clip.timeline_end_us - min_duration_us:
        raise ValueError("Split point too close to clip end")

    timeline_offset_us = split_playhead_us - clip.timeline_start_us
    source_offset_us = int(timeline_offset_us * clip.speed_multiplier)
    split_source_us = clip.source_in_us + source_offset_us

    left_clip = TimelineClip(
        id=str(uuid.uuid4()),
        track_id=clip.track_id,
        media_source=clip.media_source,
        timeline_start_us=clip.timeline_start_us,
        timeline_duration_us=timeline_offset_us,
        source_in_us=clip.source_in_us,
        source_out_us=split_source_us,
        speed_multiplier=clip.speed_multiplier,
        fit_mode=clip.fit_mode,
        transform=clip.transform,
        audio_properties=clip.audio_properties,
        custom_title=clip.custom_title
    )

    right_timeline_duration_us = clip.timeline_duration_us - timeline_offset_us
    right_clip = TimelineClip(
        id=str(uuid.uuid4()),
        track_id=clip.track_id,
        media_source=clip.media_source,
        timeline_start_us=split_playhead_us,
        timeline_duration_us=right_timeline_duration_us,
        source_in_us=split_source_us,
        source_out_us=clip.source_out_us,
        speed_multiplier=clip.speed_multiplier,
        fit_mode=clip.fit_mode,
        transform=clip.transform,
        audio_properties=clip.audio_properties,
        custom_title=clip.custom_title
    )

    return left_clip, right_clip


def calculate_snap_point(
    moving_edge_us: int,
    candidate_points_us: List[int],
    pixels_per_second: float,
    density_dpi: float,
    snap_threshold_dp: float = 16.0
) -> Optional[int]:
    if pixels_per_second <= 0:
        return None
    dp_to_pixels = snap_threshold_dp * (density_dpi / 160.0)
    threshold_us = int((dp_to_pixels / pixels_per_second) * 1_000_000.0)

    closest_distance = float("inf")
    best_snap = None

    for candidate in candidate_points_us:
        dist = abs(moving_edge_us - candidate)
        if dist <= threshold_us and dist < closest_distance:
            closest_distance = dist
            best_snap = candidate

    return best_snap


# --- Keyframing & Easing Engines ---

def solve_cubic_bezier(x1: float, y1: float, x2: float, y2: float, target_x: float) -> float:
    """Newton-Raphson solver with binary bisection fallback."""
    if not (0.0 <= x1 <= 1.0 and 0.0 <= x2 <= 1.0):
        raise ValueError(f"Bezier control points x1 ({x1}) and x2 ({x2}) must be in [0, 1]")
    target_x = max(0.0, min(1.0, target_x))
    if target_x == 0.0 or target_x == 1.0:
        return target_x

    # u parameter
    u = target_x
    for _ in range(8):
        # f(u) = 3*(1-u)^2*u*x1 + 3*(1-u)*u^2*x2 + u^3
        cx = 3.0 * x1
        bx = 3.0 * (x2 - x1) - cx
        ax = 1.0 - cx - bx
        x_est = ((ax * u + bx) * u + cx) * u
        dx = (3.0 * ax * u + 2.0 * bx) * u + cx
        if abs(x_est - target_x) < 1e-5:
            break
        if abs(dx) < 1e-6:
            break
        u -= (x_est - target_x) / dx
        u = max(0.0, min(1.0, u))

    # Bisection fallback if not converged
    cx = 3.0 * x1
    bx = 3.0 * (x2 - x1) - cx
    ax = 1.0 - cx - bx
    x_est = ((ax * u + bx) * u + cx) * u
    if abs(x_est - target_x) >= 1e-4:
        low, high = 0.0, 1.0
        for _ in range(16):
            mid = (low + high) * 0.5
            mid_x = ((ax * mid + bx) * mid + cx) * mid
            if mid_x < target_x:
                low = mid
            else:
                high = mid
        u = (low + high) * 0.5

    # Compute Y
    cy = 3.0 * y1
    by = 3.0 * (y2 - y1) - cy
    ay = 1.0 - cy - by
    return ((ay * u + by) * u + cy) * u


def solve_spring_analytical(stiffness: float, damping_ratio: float, fraction: float, duration_sec: float = 1.0) -> float:
    """Closed-form analytical damped harmonic oscillator solution."""
    if stiffness <= 0 or damping_ratio < 0:
        raise ValueError("Stiffness and damping ratio must be positive")
    if duration_sec <= 0 or fraction <= 0:
        return 0.0
    if fraction >= 1.0:
        return 1.0

    t = fraction * duration_sec
    omega_0 = math.sqrt(stiffness)
    zeta = damping_ratio

    if abs(zeta - 1.0) < 1e-5:
        # Critically damped
        return 1.0 - math.exp(-omega_0 * t) * (1.0 + omega_0 * t)
    elif zeta < 1.0:
        # Underdamped
        omega_d = omega_0 * math.sqrt(1.0 - zeta * zeta)
        decay = math.exp(-zeta * omega_0 * t)
        sin_term = (zeta * omega_0 / omega_d) * math.sin(omega_d * t)
        cos_term = math.cos(omega_d * t)
        return 1.0 - decay * (cos_term + sin_term)
    else:
        # Overdamped
        gamma = omega_0 * math.sqrt(zeta * zeta - 1.0)
        c1 = (zeta * omega_0 + gamma) / (2.0 * gamma)
        c2 = (zeta * omega_0 - gamma) / (2.0 * gamma)
        r1 = -zeta * omega_0 + gamma
        r2 = -zeta * omega_0 - gamma
        return 1.0 - (c1 * math.exp(r1 * t) - c2 * math.exp(r2 * t))


def evaluate_easing(curve_type: str, fraction: float, params: Optional[Tuple[float, float, float, float]] = None) -> float:
    t = max(0.0, min(1.0, fraction))
    if curve_type == "LINEAR":
        return t
    elif curve_type == "EASE_IN":
        return t * t * t
    elif curve_type == "EASE_OUT":
        return 1.0 - (1.0 - t) ** 3
    elif curve_type == "EASE_IN_OUT":
        return 3.0 * (t ** 2) - 2.0 * (t ** 3)
    elif curve_type == "CUBIC_BEZIER":
        p = params or (0.25, 0.1, 0.25, 1.0)
        return solve_cubic_bezier(p[0], p[1], p[2], p[3], t)
    elif curve_type == "SPRING":
        p = params or (300.0, 0.7, 1.0, 1.0)
        return solve_spring_analytical(p[0], p[1], t, p[2])
    return t


def interpolate_color_linear_rgb(argb1: int, argb2: int, fraction: float) -> int:
    """Interpolate ARGB in linear RGB space to prevent dark-band interpolation artifacts."""
    t = max(0.0, min(1.0, fraction))

    a1 = ((argb1 >> 24) & 0xFF) / 255.0
    r1 = ((argb1 >> 16) & 0xFF) / 255.0
    g1 = ((argb1 >> 8) & 0xFF) / 255.0
    b1 = (argb1 & 0xFF) / 255.0

    a2 = ((argb2 >> 24) & 0xFF) / 255.0
    r2 = ((argb2 >> 16) & 0xFF) / 255.0
    g2 = ((argb2 >> 8) & 0xFF) / 255.0
    b2 = (argb2 & 0xFF) / 255.0

    # sRGB to Linear
    r1_lin = r1 ** 2.2
    g1_lin = g1 ** 2.2
    b1_lin = b1 ** 2.2

    r2_lin = r2 ** 2.2
    g2_lin = g2 ** 2.2
    b2_lin = b2 ** 2.2

    a_interp = a1 + (a2 - a1) * t
    r_interp = (r1_lin + (r2_lin - r1_lin) * t) ** (1.0 / 2.2)
    g_interp = (g1_lin + (g2_lin - g1_lin) * t) ** (1.0 / 2.2)
    b_interp = (b1_lin + (b2_lin - b1_lin) * t) ** (1.0 / 2.2)

    a_out = int(round(max(0.0, min(1.0, a_interp)) * 255.0))
    r_out = int(round(max(0.0, min(1.0, r_interp)) * 255.0))
    g_out = int(round(max(0.0, min(1.0, g_interp)) * 255.0))
    b_out = int(round(max(0.0, min(1.0, b_interp)) * 255.0))

    return (a_out << 24) | (r_out << 16) | (g_out << 8) | b_out


def slice_typewriter_text(full_text: str, speed_cps: float, timestamp_us: int) -> Tuple[str, bool]:
    """Deterministic microsecond typewriter slicing with blinking cursor."""
    if speed_cps <= 0:
        return full_text, False
    elapsed_sec = timestamp_us / 1_000_000.0
    chars_to_show = int(elapsed_sec * speed_cps)
    visible_chars = min(len(full_text), max(0, chars_to_show))
    sliced = full_text[:visible_chars]
    # Cursor blinks every 500ms
    cursor_visible = (int(elapsed_sec * 2.0) % 2 == 0) and (visible_chars < len(full_text))
    return sliced, cursor_visible


# --- Audio Processing Equations ---

def evaluate_fade_curve(t: float, curve: str) -> float:
    t = max(0.0, min(1.0, t))
    if curve == FadeCurveType.LINEAR:
        return t
    elif curve == FadeCurveType.EXPONENTIAL:
        return t * t
    elif curve == FadeCurveType.LOGARITHMIC:
        return math.log(1.0 + 9.0 * t) / math.log(10.0)
    elif curve == FadeCurveType.SCURVE:
        return 3.0 * (t ** 2) - 2.0 * (t ** 3)
    return t


def calculate_audio_gain(
    pts_us: int,
    clip_duration_us: int,
    volume: float = 1.0,
    fade_in_duration_us: int = 0,
    fade_in_curve: str = FadeCurveType.SCURVE,
    fade_out_duration_us: int = 0,
    fade_out_curve: str = FadeCurveType.SCURVE
) -> float:
    fade_multiplier = 1.0

    # Scale fades proportionally if sum exceeds duration
    total_fades = fade_in_duration_us + fade_out_duration_us
    effective_fade_in = fade_in_duration_us
    effective_fade_out = fade_out_duration_us
    if total_fades > clip_duration_us and total_fades > 0:
        scale = clip_duration_us / float(total_fades)
        effective_fade_in = int(fade_in_duration_us * scale)
        effective_fade_out = int(fade_out_duration_us * scale)

    if effective_fade_in > 0 and pts_us < effective_fade_in:
        prog = pts_us / float(effective_fade_in)
        fade_multiplier *= evaluate_fade_curve(prog, fade_in_curve)

    fade_out_start = clip_duration_us - effective_fade_out
    if effective_fade_out > 0 and pts_us >= fade_out_start:
        elapsed = pts_us - fade_out_start
        prog = 1.0 - (elapsed / float(effective_fade_out))
        fade_multiplier *= evaluate_fade_curve(prog, fade_out_curve)

    return max(0.0, volume * fade_multiplier)


def constant_power_stereo_pan(pan_position: float) -> Tuple[float, float]:
    """Constant-power stereo balance for pan in [-1.0 (Left), +1.0 (Right)]."""
    p = max(-1.0, min(1.0, pan_position))
    angle = (math.pi / 4.0) * (p + 1.0)
    left_gain = math.cos(angle)
    right_gain = math.sin(angle)
    return left_gain, right_gain


def compute_audio_ducking(
    pts_us: int,
    primary_voice_active: bool,
    ducking_depth_db: float = -12.0,
    base_music_volume: float = 1.0
) -> float:
    """Computes dynamic ducking gain multiplier on secondary audio."""
    if not primary_voice_active:
        return base_music_volume
    linear_attenuation = 10.0 ** (ducking_depth_db / 20.0)
    return base_music_volume * linear_attenuation



# --- Subtitles / Captions ---

@dataclass
class TimedWord:
    word: str
    start_offset_us: int
    end_offset_us: int


@dataclass
class CaptionCue:
    start_time_us: int
    end_time_us: int
    text: str
    words: List[TimedWord] = field(default_factory=list)


class SrtParser:
    TIME_PATTERN = re.compile(r"(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})")

    @classmethod
    def parse(cls, input_str: str) -> List[CaptionCue]:
        clean = input_str.replace("\ufeff", "").replace("\r\n", "\n").replace("\r", "\n")
        blocks = clean.strip().split("\n\n")
        cues = []
        for block in blocks:
            lines = [l.strip() for l in block.split("\n") if l.strip()]
            if not lines:
                continue
            time_idx = -1
            match = None
            for idx, line in enumerate(lines):
                match = cls.TIME_PATTERN.search(line)
                if match:
                    time_idx = idx
                    break
            if time_idx == -1 or not match:
                continue

            start_us = (int(match.group(1)) * 3600 + int(match.group(2)) * 60 + int(match.group(3))) * 1_000_000 + int(match.group(4)) * 1000
            end_us = (int(match.group(5)) * 3600 + int(match.group(6)) * 60 + int(match.group(7))) * 1_000_000 + int(match.group(8)) * 1000

            text_lines = lines[time_idx + 1:]
            raw_text = "\n".join(text_lines)
            clean_text = re.sub(r"<[^>]*>", "", raw_text).strip()
            if not clean_text:
                continue

            words = []
            tokens = clean_text.split()
            if tokens and end_us > start_us:
                slice_dur = (end_us - start_us) // len(tokens)
                for w_idx, tok in enumerate(tokens):
                    w_start = start_us + (w_idx * slice_dur)
                    w_end = end_us if w_idx == len(tokens) - 1 else start_us + ((w_idx + 1) * slice_dur)
                    words.append(TimedWord(tok, w_start, w_end))

            cues.append(CaptionCue(start_us, end_us, clean_text, words))
        return cues

    @staticmethod
    def export(cues: List[CaptionCue]) -> str:
        lines = []
        for idx, cue in enumerate(sorted(cues, key=lambda c: c.start_time_us)):
            lines.append(str(idx + 1))
            start_s = cue.start_time_us // 1_000_000
            start_ms = (cue.start_time_us % 1_000_000) // 1000
            end_s = cue.end_time_us // 1_000_000
            end_ms = (cue.end_time_us % 1_000_000) // 1000
            start_timecode = f"{start_s // 3600:02d}:{(start_s % 3600) // 60:02d}:{start_s % 60:02d},{start_ms:03d}"
            end_timecode = f"{end_s // 3600:02d}:{(end_s % 3600) // 60:02d}:{end_s % 60:02d},{end_ms:03d}"
            lines.append(f"{start_timecode} --> {end_timecode}")
            lines.append(cue.text)
            lines.append("")
        return "\n".join(lines)


class WebVttParser:
    TIME_PATTERN = re.compile(r"(?:(\d{2}):)?(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(?:(\d{2}):)?(\d{2}):(\d{2})\.(\d{3})")

    @classmethod
    def parse(cls, input_str: str) -> List[CaptionCue]:
        clean = input_str.replace("\ufeff", "").replace("\r\n", "\n").replace("\r", "\n")
        lines = clean.split("\n")
        cues = []
        i = 0
        while i < len(lines):
            line = lines[i].strip()
            match = cls.TIME_PATTERN.search(line)
            if match:
                s_h = int(match.group(1)) if match.group(1) else 0
                s_m = int(match.group(2))
                s_s = int(match.group(3))
                s_ms = int(match.group(4))
                start_us = (s_h * 3600 + s_m * 60 + s_s) * 1_000_000 + s_ms * 1000

                e_h = int(match.group(5)) if match.group(5) else 0
                e_m = int(match.group(6))
                e_s = int(match.group(7))
                e_ms = int(match.group(8))
                end_us = (e_h * 3600 + e_m * 60 + e_s) * 1_000_000 + e_ms * 1000

                i += 1
                text_lines = []
                while i < len(lines) and lines[i].strip():
                    text_lines.append(lines[i].strip())
                    i += 1
                raw_text = "\n".join(text_lines)
                clean_text = re.sub(r"<[^>]*>", "", raw_text).strip()
                if clean_text:
                    cues.append(CaptionCue(start_us, end_us, clean_text))
            i += 1
        return cues

    @staticmethod
    def export(cues: List[CaptionCue]) -> str:
        lines = ["WEBVTT", ""]
        for cue in sorted(cues, key=lambda c: c.start_time_us):
            start_s = cue.start_time_us // 1_000_000
            start_ms = (cue.start_time_us % 1_000_000) // 1000
            end_s = cue.end_time_us // 1_000_000
            end_ms = (cue.end_time_us % 1_000_000) // 1000
            start_timecode = f"{start_s // 3600:02d}:{(start_s % 3600) // 60:02d}:{start_s % 60:02d}.{start_ms:03d}"
            end_timecode = f"{end_s // 3600:02d}:{(end_s % 3600) // 60:02d}:{end_s % 60:02d}.{end_ms:03d}"
            lines.append(f"{start_timecode} --> {end_timecode}")
            lines.append(cue.text)
            lines.append("")
        return "\n".join(lines)


# --- Render Plan & Storage Validator ---

@dataclass
class RenderPlan:
    resolution: Tuple[int, int, str] = ExportResolution.RES_1080P
    frame_rate: int = ExportFrameRate.FPS_30
    aspect_ratio: Tuple[str, int, int] = CanvasAspectRatio.RATIO_16_9
    video_codec: str = VideoCodec.H264
    video_bitrate_bps: int = 12_000_000
    audio_bitrate_bps: int = 192_000
    duration_us: int = 0

    def calculate_estimated_output_size_bytes(self) -> int:
        duration_sec = self.duration_us / 1_000_000.0
        total_bits_per_sec = self.video_bitrate_bps + self.audio_bitrate_bps
        raw_bytes = (total_bits_per_sec / 8.0) * duration_sec
        # 15% container overhead + 50 MB safety buffer
        return int(raw_bytes * 1.15) + (50 * 1024 * 1024)

    def estimate_file_size_bytes(self) -> int:
        return self.calculate_estimated_output_size_bytes()


def validate_render_plan(project: ProjectManifest, plan: RenderPlan, available_bytes: int) -> Tuple[bool, str]:
    has_clips = any(len(t.clips) > 0 for t in project.tracks)
    if not has_clips or plan.duration_us <= 0:
        return False, "Empty timeline or zero duration"

    required_bytes = plan.calculate_estimated_output_size_bytes()
    if available_bytes < required_bytes:
        return False, f"Insufficient storage: needed {required_bytes} bytes, available {available_bytes} bytes"

    return True, "Valid"


# --- Zip-Slip Path Traversal Protection ---

def is_safe_zip_path(entry_name: str) -> bool:
    normalized = entry_name.replace("\\", "/")
    if normalized.startswith("/") or normalized.startswith("\\"):
        return False
    parts = normalized.split("/")
    if ".." in parts:
        return False
    return True


def validate_zip_entry_path(destination_dir: str, entry_name: str) -> str:
    """Strict canonical path check preventing Zip-Slip directory traversal."""
    if '\x00' in entry_name:
        raise ValueError(f"Null byte in entry name: {entry_name}")
    normalized_entry = entry_name.replace('\\', '/')
    if re.match(r'^[a-zA-Z]:', normalized_entry):
        raise PermissionError(f"Zip-Slip vulnerability detected! Absolute drive in entry '{entry_name}'.")
    canonical_dest = os.path.realpath(os.path.abspath(destination_dir))
    target_path = os.path.realpath(os.path.abspath(os.path.join(canonical_dest, normalized_entry)))
    # Ensure target path starts with canonical destination + sep
    dest_prefix = canonical_dest if canonical_dest.endswith(os.sep) else canonical_dest + os.sep
    if not target_path.startswith(dest_prefix) and target_path != canonical_dest:
        raise PermissionError(f"Zip-Slip vulnerability detected! Entry '{entry_name}' traverses outside destination.")
    return target_path


# --- AI Subsystem Models & Invariants ---

@dataclass
class EditAction:
    action_type: str
    start_us: int
    end_us: int
    description: str
    approved: bool = False


@dataclass
class EditPlan:
    name: str
    actions: List[EditAction] = field(default_factory=list)


def detect_silence_intervals(
    audio_profile: List[Tuple[int, int, float]],
    threshold_db: float = -40.0,
    min_duration_us: int = 500_000
) -> List[Tuple[int, int]]:
    silences = []
    for start_us, end_us, db in audio_profile:
        if db <= threshold_db and (end_us - start_us) >= min_duration_us:
            silences.append((start_us, end_us))
    return silences


def analyze_speech_pacing(
    total_duration_us: int,
    silence_duration_us: int,
    spoken_words_count: int
) -> Dict[str, float]:
    active_speech_sec = max(1.0, (total_duration_us - silence_duration_us) / 1_000_000.0)
    wpm = (spoken_words_count / active_speech_sec) * 60.0
    return {
        "words_per_minute": wpm,
        "active_duration_sec": active_speech_sec,
        "pause_ratio": silence_duration_us / total_duration_us if total_duration_us > 0 else 0.0
    }

