# CutPro Architecture Specification

CutPro is structured as a layered, modular Android application adhering to Clean Architecture principles, unidirectional data flow (UDF), and strict separation between pure Kotlin domain models and Android framework dependencies.

---

## 1. High-Level Architectural Layers

```text
┌────────────────────────────────────────────────────────────────────────┐
│                                UI Layer                                │
│   :feature:dashboard, :feature:editor, :feature:export, :app           │
│   Jetpack Compose | Material 3 Dark Theme | ViewModel | StateFlow      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Observes State / Emits Intents
┌───────────────────────────────────▼────────────────────────────────────┐
│                              Domain Layer                              │
│   :core:model, :core:common                                            │
│   ProjectManifest | Track | TimelineClip | Timecode | Keyframe         │
│   ZERO Android dependencies (Pure Kotlin)                              │
└───────┬───────────────────────────┬────────────────────────────┬───────┘
        │                           │                            │
┌───────▼──────────────┐  ┌─────────▼──────────────┐  ┌──────────▼───────────────┐
│     Data Layer       │  │ Media & Playback Layer │  │     Processing Layer     │
│ :core:data           │  │ :core:playback         │  │ :core:audio, :core:motion│
│ :core:security       │  │ ExoPlayer Surface      │  │ :core:subtitles, :core:ai│
│ Room SQLite DB       │  │ Playhead Coordinator   │  │ :core:export (Media3)    │
│ .cutproproj JSON     │  │ Adaptive Scrub Engine  │  │ Analytical Solvers       │
│ .cutprobundle ZIP    │  │ Canvas Framing Guides  │  │ DSP Audio Processors     │
└──────────────────────┘  └────────────────────────┘  └──────────────────────────┘
```

---

## 2. Core Modules Breakdown

### `:core:common`
Contains foundational primitives shared across all modules:
- Microsecond timecode arithmetic and conversion (`Timecode`, `TimecodeHelper`).
- Structured functional result wrapper (`Result<T>`, `AppError`).
- Coroutine dispatchers provider (`DispatchersProvider`).

### `:core:model`
Pure Kotlin domain models with zero dependencies on Android SDK:
- `ProjectManifest`: Immutable root aggregate storing project metadata, canvas aspect ratio, resolution, and tracks.
- `Track`: Represents Video, Audio, Text, Overlay, or Caption tracks with sequential ordering and z-index.
- `TimelineClip`: Media clip model capturing in/out trim points, timeline offsets, transformations, and playback speed.
- `TransformKeyframe`: Keyframe definition capturing 2D position, scale, rotation, opacity, and easing curve.
- `CanvasAspectRatio` & `ExportResolution`: Macroblock-aligned video dimensions (multiple of 16 for hardware encoders).

### `:core:data` & `:core:security`
Persistence and storage security layer:
- `CutproDatabase`: Room database holding project, track, clip, and keyframe tables with cascading deletes.
- `ProjectJsonSerializer`: Versioned JSON v1 schema serializer with backward-compatibility checks.
- `AtomicProjectSaver`: Two-phase atomic file saver (temporary file write, fsync, atomic rename).
- `CutproBundlePackager` & `CutproBundleUnpackager`: Self-contained project zip bundle packager protected against Zip-Slip path traversal and streaming zip bombs.
- `EncryptedPreferences`: Android Keystore AES-256-GCM encrypted configuration storage.

### `:core:timeline`
Pure non-linear timeline calculation and editing engine:
- `TimelineEngine`: Pure functional algorithms for clip trimming (`trimIn`, `trimOut`), splitting at playhead timestamp, dragging, and magnetic snapping.
- `UndoRedoManager`: Command pattern undo/redo stack recording atomic timeline mutations (`SnapshotTimelineCommand`).

### `:core:playback`
Media preview and player synchronization engine:
- `PreviewPlaybackCoordinator`: Single-instance ExoPlayer hardware decoder host streaming playback state through `StateFlow`.
- `AdaptiveSeekDispatcher`: Conflated scrubbing pipeline utilizing `SeekParameters.CLOSEST_SYNC` during active drag and `SeekParameters.EXACT` upon touch release.
- `PlayheadSyncLoop`: 60fps presentation clock synchronizing Compose UI playhead with audio-visual presentation time.
- `CanvasFramingCalculator`: Aspect ratio letterbox/pillarbox container with macroblock alignment and sub-pixel height safety bounds.

### `:core:motiongraphics`
Deterministic keyframing and declarative animation engine:
- `PropertyTimeline<T>`: Microsecond presentation timestamp interpolation engine supporting `ONCE`, `LOOP`, and `PING_PONG` cycles.
- `AnalyticalSpringSolver`: Closed-form solution for damped harmonic oscillator physics across underdamped, critically damped, and overdamped regimes with zero accumulative drift.
- `CubicBezierSolver`: Hybrid Newton-Raphson root finder with binary bisection fallback.
- `LinearRgbColorInterpolator`: Gamma 2.2 corrected color interpolation preventing dark-band artifacts.
- `21 Built-in Offline Templates`: Opening Titles, Kinetic Typography, Lower Thirds, Subtitles, Counters, CTAs, and Logo Reveals.
- `MotionGraphicsCanvasRenderer`: Android Canvas drawing engine shared between interactive preview and export.

### `:core:subtitles`
Subtitles, captions, and lyric highlighting:
- `SrtParser` & `WebVttParser`: Resilient subtitle parsers handling UTF-8 BOM, CRLF/LF line endings, and HTML tag sanitization.
- `KaraokeEngine`: Word-level karaoke highlighting synchronized to presentation timestamps.
- `SubtitleCanvasRenderer`: Dynamic multiline text wrapping and line-aware spoken word highlights.

### `:core:ai`
Offline modular editing intelligence:
- `AudioRmsSilenceDetector`: 20ms frame evaluation, 3dB hysteresis, -40dBFS threshold silence detection.
- `JumpCutOptimizer`: Automated non-destructive jump cuts with 80ms audio crossfade safety buffers.
- `TalkingHeadPacingAnalyzer`: Flags single-take shots exceeding 8 seconds and suggests zoom push-ins or cut cadences.
- `StandardApprovalGate`: Structured user inspection checklist with reverse-chronological back-to-front execution preventing timeline coordinate drift.
- `SpeechToTextProvider`: Extensible offline STT contracts for English and Bengali with a 500MB low-RAM memory ceiling.

### `:core:export` & `:feature:export`
Video export and transformation subsystem:
- `Media3VideoExportEngine`: Off-main-thread video rendering via Android Media3 Transformer.
- `Media3CompositionBuilder`: Assembles multi-clip sequences into `EditedMediaItemSequence` with audio DSP mixing.
- `CompositionCanvasOverlay`: Renders motion graphics templates and burned-in subtitles onto video frames.
- `ExportForegroundService`: Foreground service lifecycle management with ongoing notifications, CPU wake locks, and cancellation cleanup.
- `MediaStoreVideoSaver`: Publishes completed MP4 files using Scoped Storage (`IS_PENDING = 1` during write, `0` on publish).
- `PreExportValidator`: Disk storage headroom verification ($> \text{estimatedOutputBytes} + 50\text{MB}$) and asset accessibility checks.

---

## 3. Unidirectional Data Flow (UDF)

CutPro implements a strict single source of truth architecture:
1. User gestures on the preview canvas or timeline emit actions to the active `ViewModel`.
2. The `ViewModel` invokes domain commands on `TimelineEngine` or persistence repositories.
3. Updated immutable `ProjectManifest` states are emitted via `StateFlow`.
4. Compose UI and ExoPlayer preview observe the updated state and update their rendering deterministically.
