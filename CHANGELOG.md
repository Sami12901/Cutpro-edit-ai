# Changelog

All notable changes to CutPro are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-11-01

### Added
- **Core Architecture & Persistence (Milestone 1)**:
  - Multi-module Gradle Kotlin DSL architecture with version catalogs (`libs.versions.toml`).
  - Pure Kotlin domain model engine (`ProjectManifest`, `Track`, `TimelineClip`, `Timecode`, `CanvasAspectRatio`).
  - Room SQLite persistence with cascading constraints and relational schema.
  - Portable `.cutproproj` JSON v1 serialization schema with backward-compatibility checks.
  - Two-phase non-corrupting atomic project autosave and recovery.
  - `.cutprobundle` ZIP packaging with strict canonicalization against Zip-Slip attacks.
  - Android Keystore AES-256-GCM encrypted configuration storage.
- **UI Shell & Multitrack Timeline (Milestone 2)**:
  - Material 3 Dark Slate theme with high-contrast accessibility controls meeting or exceeding 48dp touch targets.
  - Zero-emoji design system featuring pure vector iconography (`CutProIcons`).
  - Navigation Compose architecture (`DashboardScreen`, `EditorScreen`).
  - Project Dashboard with recent projects, project creation, and template gallery.
  - Interactive multitrack timeline supporting video, audio, text, and caption tracks.
  - Frame-accurate playhead scrubber with SMPTE timecode display.
  - Command-pattern atomic undo/redo manager (`UndoRedoManager`).
  - Magnetic snapping engine, in/out trimming handles, and clip splitting.
  - Contextual properties inspector dynamically tailoring controls to selected clip types.
- **Playback Engine & Audio DSP (Milestone 3)**:
  - Single-instance AndroidX Media3 ExoPlayer hardware-accelerated preview coordinator.
  - Adaptive scrubber switching dynamically between fast keyframe seek (`SEEK_CLOSEST_SYNC`) and exact frame seek (`SEEK_EXACT`).
  - 60fps playhead synchronization loop.
  - Dynamic aspect ratio canvas framing (16:9, 9:16, 1:1, 4:5, 21:9) with safe-zone guides.
  - DSP audio processing: sample-accurate volume curves, soft-clipping saturation limiter, dynamic 4-state audio ducking, and constant-power stereo panning.
- **Motion Graphics & Subtitles (Milestone 4)**:
  - Timestamp-deterministic keyframe interpolation engine (`Keyframe<T>`, `PropertyTimeline<T>`).
  - Closed-form analytical spring solver across underdamped, critically damped, and overdamped regimes.
  - Hybrid Newton-Raphson / bisection cubic Bezier curve solver.
  - Linear RGB gamma 2.2 color interpolation.
  - 21 offline built-in templates across 7 categories (Opening Titles, Kinetic Typography, Lower Thirds, Animated Subtitles, Metric Counters, CTAs, Logo Reveals).
  - Resilient bidirectional SRT and WebVTT parsers/exporters.
  - Word-by-word karaoke highlight engine with line-aware multiline text wrapping.
- **Modular AI Subsystem (Milestone 5)**:
  - Deterministic DSP audio silence detector (20ms frames, -40dBFS threshold, 3dB hysteresis).
  - Jump-cut optimizer with 80ms audio crossfade safety padding.
  - Talking-head pacing analyzer detecting static footage (>8s) and suggesting visual variations.
  - Structured user approval gate (`StandardApprovalGate`) with reverse-chronological ripple execution and atomic rollback.
  - Offline pluggable Speech-to-Text contracts for English and Bengali with a 500MB low-RAM memory ceiling and `BengaliScriptHelper`.
- **Media3 Transformer MP4 Video Export (Milestone 6)**:
  - Off-main-thread video rendering engine via AndroidX Media3 Transformer.
  - Hardware H.264 video and AAC audio encoding (720p, 1080p, 24/30/60 fps, 16:9, 9:16, 1:1).
  - Layered `CompositionCanvasOverlay` for deterministic motion graphics and burned-in subtitles.
  - Android Foreground Service (`ExportForegroundService`) with live notifications and CPU wake locks.
  - Real-time pre-export storage validation (> estimatedBytes + 50MB) and MediaStore Scoped Storage publishing.
  - Clean export cancellation with automatic partial file deletion.
- **Open-Source Release & Quality Assurance (Milestone 7)**:
  - Complete documentation suite (`ARCHITECTURE.md`, `BUILD_ANDROID.md`, `EDITING_MODEL.md`, `RENDERING.md`, `PRIVACY.md`, `THIRD_PARTY_NOTICES.md`).
  - GitHub Actions CI workflow for automated builds and testing.
  - 915-test opaque-box E2E regression verification suite.
