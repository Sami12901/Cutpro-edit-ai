# Project: CutPro Native Android Video Editor & Motion Graphics

## Architecture
CutPro is designed strictly as an offline-first native Android phone and tablet application using Kotlin, Jetpack Compose, Material 3, Room, DataStore, and AndroidX Media3 (ExoPlayer & Transformer). Zero emojis anywhere in the application interface. No web, desktop, Electron, React, Vite, or browser wrappers.

### Module Architecture
```
cutpro/
├── app/                          # Application entry, Application class, MainActivity, Navigation root
├── core/
│   ├── common/                   # Coroutine dispatchers, Result<T>, Timecode math, AppError
│   ├── model/                    # Pure Kotlin domain models (Project, Track, Clip, Transform, Timecode, RenderPlan) - ZERO Android dependencies
│   ├── data/                     # Room SQLite DB, DAOs, .cutproproj JSON v1, .cutprobundle ZIP packager, SAF URI management
│   ├── timeline/                 # Timeline state engine, Trimming, Splitting, Snapping, Undo/Redo stack
│   ├── playback/                 # ExoPlayer preview engine, Playhead sync coordinator, Surface renderers
│   ├── audio/                    # VolumeAndFadeAudioProcessor, Audio ducking, Channel mixing matrix
│   ├── subtitles/                # Resilient SRT & WebVTT parsers/exporters, Styling model, Karaoke highlighting
│   ├── motiongraphics/           # Deterministic keyframing engine, Analytical spring & Bezier solvers, 21 offline templates
│   ├── ai/                       # Modular AI assistant, Deterministic DSP silence/pacing analyzers, Extensible STT contracts
│   ├── export/                   # Media3 Transformer export pipeline, Render plan validation, ExportService
│   ├── security/                 # Scoped storage & Keystore AES-256-GCM encrypted preferences
│   └── designsystem/             # Material 3 Dark/Light theme, Typography, Accessible touch targets, Vector iconography
└── feature/
    ├── dashboard/                # Project dashboard, Recent projects, Template gallery, Settings
    ├── editor/                   # Editor screen: TopToolbar, Canvas preview, Multitrack timeline, Inspector
    └── export/                   # Export dialog, Progress sheet, Share intent
```

### Data Flow & Preview/Export Parity
```
[User Touch / SAF Input] ──► [EditorViewModel (UDF)] ──► [TimelineEngine / UndoRedoStack]
                                        │
                         ┌──────────────┴──────────────┐
                         ▼                             ▼
              [ExoPlayer Preview]            [Media3 Transformer Export]
           (Master Playhead Controller)      (Off-main-thread Foreground Service)
                         │                             │
                         └──────────────┬──────────────┘
                                        ▼
                   [Deterministic MotionGraphicsCanvasRenderer]
                   Evaluates at microsecond presentation timestamp:
                   f(timestampUs) -> EvaluatedLayers & CanvasOverlay
```

---

## Code Layout
- Root: `d:\Cutpro edit ai\`
- App Module: `app/src/main/java/com/cutpro/`
- Core Modules: `core/<module>/src/main/java/com/cutpro/core/<module>/`
- Feature Modules: `feature/<module>/src/main/java/com/cutpro/feature/<module>/`
- Test Suites:
  - JVM Unit Tests: `core/<module>/src/test/java/com/cutpro/core/<module>/`
  - Integration Tests: `core/<module>/src/androidTest/java/com/cutpro/core/<module>/`
  - E2E Tests: `tests/e2e/`
- Documentation: `README.md`, `ARCHITECTURE.md`, `CONTRIBUTING.md`, `LICENSE`
- CI/CD: `.github/workflows/android.yml`

---

## Feature Inventory
Every single feature discovered during Phase 0 Survey is cataloged below with its designated Milestone.

| # | Feature ID | Feature Name | Description | Milestone | Source |
|---|---|---|---|---|---|
| 1 | FEAT-CORE-001 | Multi-Module Clean Architecture | Establish Gradle hierarchy separating pure Kotlin domain models from Android framework | M1 | Survey (R1) |
| 2 | FEAT-CORE-002 | Pure Domain Model Engine | Immutable models for Project, Track, Clip, Transform, Timecode, AspectRatio, Resolution | M1 | Survey (R1) |
| 3 | PER-01 | Room SQLite Database Engine | Full CRUD database for projects, tracks, clips, and keyframes with cascading constraints | M1 | Survey (R5) |
| 4 | PER-02 | Versioned .cutproproj JSON v1 Schema | Portable, human-readable manifest serialization with backward-compatibility checks | M1 | Survey (R5) |
| 5 | PER-03 | Non-Corrupting Atomic Autosave | Autosaves project state every 10s via temp-file write, fsync, and atomic rename | M1 | Survey (R5) |
| 6 | PER-04 | Missing-Media Detection & Relinking | Identifies deleted/inaccessible assets on open, displays timeline warning, and provides relinking | M1 | Survey (R5) |
| 7 | PER-05 | .cutprobundle ZIP Packager | Exports/imports self-contained project archives bundling media assets and manifest | M1 | Survey (R5) |
| 8 | SEC-01 | Zip-Slip Path Traversal Protection | Strict canonicalization preventing directory traversal during .cutprobundle extraction | M1 | Survey (R5) |
| 9 | SEC-02 | Scoped Storage URI Persistence | Obtains persistable read grants across reboots via takePersistableUriPermission | M1 | Survey (R5) |
| 10 | SEC-03 | Android Keystore Data Encryption | Encrypts sensitive configuration using AES-256-GCM EncryptedSharedPreferences | M1 | Survey (R5) |
| 11 | FEAT-IMP-001 | Android Photo Picker Integration | Import video and image files via standard system Photo Picker without broad permissions | M1 | Survey (R3) |
| 12 | FEAT-IMP-002 | Storage Access Framework (SAF) Import | Import audio files (MP3, WAV, M4A) and visual media with persistent read access | M1 | Survey (R3) |
| 13 | FEAT-IMP-003 | Media Metadata Extraction & Validation | Extract duration, dimensions, rotation, frame rate, audio channels, and verify file integrity | M1 | Survey (R3) |
| 14 | FEAT-IMP-004 | Thumbnail Proxy Strip Generator | Background asynchronous generation of 96x54 preview thumbnails for timeline clips | M1 | Survey (R3) |
| 15 | FEAT-UI-001 | Material 3 Dark/Light Theme Shell | High-contrast Dark theme (default) and Light theme adhering strictly to Material 3 | M2 | Survey (R1) |
| 16 | FEAT-UI-002 | Zero-Emoji Strict Iconography | Comprehensive vector iconography suite replacing all emojis with Material Symbols | M2 | Survey (R1) |
| 17 | FEAT-UI-003 | Accessible Touch Targets (48dp) | Ensure all buttons, trim handles, scrubber heads, and sliders satisfy >= 48dp touch bounds | M2 | Survey (R1) |
| 18 | FEAT-UI-004 | Project Dashboard & Recent Grid | Dashboard displaying recent projects, creation button, duration, and project management | M2 | Survey (R1) |
| 19 | FEAT-UI-005 | Top Toolbar Action Controls | Toolbar providing Undo, Redo, Project Title editing, Aspect Ratio switch, and Export action | M2 | Survey (R1) |
| 20 | FEAT-UI-006 | Multitrack Timeline Canvas UI | Scrollable, zoomable multitrack timeline displaying video, audio, text, and overlay tracks | M2 | Survey (R1) |
| 21 | FEAT-UI-007 | Draggable Playhead Scrubber | Interactive playhead with timecode display, draggable scrubber head, and haptic ticks | M2 | Survey (R1) |
| 22 | FEAT-UI-008 | Contextual Properties Inspector | Dynamic bottom sheet/panel updating controls based on selected clip type | M2 | Survey (R1) |
| 23 | FEAT-TL-001 | Timeline Clip Repositioning & Drag | Drag and drop clips along timeline track with collision detection and snapping | M2 | Survey (R3) |
| 24 | FEAT-TL-002 | In-Point & Out-Point Trimming Engine | Drag left or right handles of clip to trim media boundaries in microseconds | M2 | Survey (R3) |
| 25 | FEAT-TL-003 | Clip Splitting at Playhead | Split active clip at exact playhead position into two independent non-overlapping segments | M2 | Survey (R3) |
| 26 | FEAT-TL-004 | Magnetic Snapping Engine | Snap clip boundaries to playhead, adjacent clips, and project boundaries with haptic feedback | M2 | Survey (R3) |
| 27 | FEAT-TL-005 | Undo / Redo Command Stack | Command pattern recording atomic timeline mutations (Move, Trim, Split, Delete, Add) | M2 | Survey (R1) |
| 28 | FEAT-PLAY-001 | ExoPlayer Preview Surface Engine | Compose AndroidView embedding ExoPlayer surface configured for low-latency playback | M3 | Survey (R1) |
| 29 | FEAT-PLAY-002 | Master Playhead Synchronization Loop | Sync 60fps Compose UI timeline playhead with ExoPlayer position clock | M3 | Survey (R1) |
| 30 | FEAT-PLAY-003 | Adaptive Scrubbing Performance | Fast keyframe seek (SEEK_CLOSEST_SYNC) during drag, exact seek (SEEK_EXACT) on release | M3 | Survey (R1) |
| 31 | FEAT-PLAY-004 | Aspect Ratio Canvas Framing & Guides | Dynamic letterboxing/pillarboxing container with safe zone guide lines (16:9, 9:16, 1:1, etc.) | M3 | Survey (R1) |
| 32 | FEAT-TL-006 | Clip Speed Adjustment | Adjust clip playback speed from 0.1x to 10.0x with automatic timeline duration recalculation | M3 | Survey (R3) |
| 33 | FEAT-TL-007 | Clip Transform & Fit Mode | Configure Fit, Fill, Stretch, and custom Scale X/Y, Rotation, and Translation per clip | M3 | Survey (R3) |
| 34 | AUD-01 | Per-Clip Volume & Gain | Linear and decibel-based gain scaling per clip with soft-clipping saturation protection | M3 | Survey (R4) |
| 35 | AUD-02 | Fade-In Envelope | Sample-accurate fade-in gain ramping (Linear, Log, Exp, SCurve) | M3 | Survey (R4) |
| 36 | AUD-03 | Fade-Out Envelope | Sample-accurate fade-out gain ramping (Linear, Log, Exp, SCurve) | M3 | Survey (R4) |
| 37 | AUD-04 | Track Mute & Solo | Track-level audio silencing and solo isolation | M3 | Survey (R4) |
| 38 | AUD-05 | Dynamic Audio Ducking | Lowers BGM volume when voice track dialogue is present (150ms attack, 300ms hold, 400ms release) | M3 | Survey (R4) |
| 39 | AUD-06 | Constant-Power Stereo Panning | Acoustically balanced stereo position adjustment across stereo soundstage | M3 | Survey (R4) |
| 40 | FEAT-MG-001 | Keyframe Creation & Storage | Immutable Keyframe<T> at presentation timestamp with value and easing curve | M4 | Survey (R2) |
| 41 | FEAT-MG-002 | Keyframe Modification | Updates an existing keyframe's value or easing curve while maintaining temporal ordering | M4 | Survey (R2) |
| 42 | FEAT-MG-003 | Keyframe Deletion | Deletes keyframe at specific timestamp or index safely | M4 | Survey (R2) |
| 43 | FEAT-MG-004 | Linear Easing Evaluator | Pure mathematical evaluation of linear progress tau in [0, 1] | M4 | Survey (R2) |
| 44 | FEAT-MG-005 | Standard Cubic Curves Evaluator | Pure mathematical evaluation of EaseIn, EaseOut, EaseInOut Penner curves | M4 | Survey (R2) |
| 45 | FEAT-MG-006 | Parametric Cubic Bezier Solver | Newton-Raphson solver with binary bisection fallback for cubic Bezier control points | M4 | Survey (R2) |
| 46 | FEAT-MG-007 | Analytical Spring Solver | Closed-form solution of damped harmonic oscillator (underdamped, critically damped, overdamped) | M4 | Survey (R2) |
| 47 | FEAT-MG-008 | Position 2D Interpolation | Evaluates normalized 2D offset (X, Y) across timeline | M4 | Survey (R2) |
| 48 | FEAT-MG-009 | Scale 2D Interpolation | Evaluates independent horizontal and vertical scale factors | M4 | Survey (R2) |
| 49 | FEAT-MG-010 | Rotation Interpolation | Interpolates 2D rotation angle in degrees with wrap handling | M4 | Survey (R2) |
| 50 | FEAT-MG-011 | Opacity Interpolation | Evaluates layer alpha multiplier across timeline coerced in [0.0, 1.0] | M4 | Survey (R2) |
| 51 | FEAT-MG-012 | Color/Tint Linear RGB Interpolation | Interpolates ARGB colors in linear RGB space to avoid dark-band interpolation artifacts | M4 | Survey (R2) |
| 52 | FEAT-MG-013 | Typography Text Layout Engine | Renders animated text with microsecond font size, letter spacing, and line height adjustments | M4 | Survey (R2) |
| 53 | FEAT-MG-014 | Typewriter String Slicing | Deterministically slices string content based on typing speed and presentation timestamp | M4 | Survey (R2) |
| 54 | FEAT-MG-015 | Shape Geometry Morphing & Stroking | Evaluates animated rectangle bounds, pill corner radius, stroke width, and clip paths | M4 | Survey (R2) |
| 55 | FEAT-TPL-001 | Opening Titles Templates (3) | Built-in offline title templates: Minimal Modern, Cinematic Split Fade, Bold Glitch | M4 | Survey (R2) |
| 56 | FEAT-TPL-002 | Kinetic Typography Templates (3) | Built-in kinetic text templates: Pop Bounce, Typewriter Terminal, Word-by-Word Slide | M4 | Survey (R2) |
| 57 | FEAT-TPL-003 | Lower Thirds Templates (3) | Built-in lower third templates: News Broadcast Bar, Social Creator Duo, Gradient Accent Pill | M4 | Survey (R2) |
| 58 | FEAT-TPL-004 | Animated Subtitles Templates (3) | Built-in subtitle templates: Karaoke Highlight, Pop Bubble Caption, High-Contrast Box | M4 | Survey (R2) |
| 59 | FEAT-TPL-005 | Animated Counters Templates (3) | Built-in metric templates: Numeric Tally Metric, Milestone Badge, Smooth Percentage Gauge | M4 | Survey (R2) |
| 60 | FEAT-TPL-006 | Call-to-Actions Templates (3) | Built-in CTA templates: Subscribe Bell, Clean Follow Handle, Swipe/Tap Indicator | M4 | Survey (R2) |
| 61 | FEAT-TPL-007 | Logo Reveals Templates (3) | Built-in logo reveal templates: Circular Iris Mask, Scale & Vignette, Double Horizontal Split | M4 | Survey (R2) |
| 62 | FEAT-SYNC-001 | Compose Canvas Preview Renderer | Renders evaluated motion layers directly inside Jetpack Compose Canvas at 60fps | M4 | Survey (R2) |
| 63 | FEAT-SER-001 | Template Manifest JSON Serialization | Serializes/deserializes template parameters and keyframes to versioned .cutproproj JSON | M4 | Survey (R5) |
| 64 | SUB-01 | SRT Bidirectional Parser & Exporter | Reads and writes standard SubRip subtitle files with resilient error recovery | M4 | Survey (R4) |
| 65 | SUB-02 | WebVTT Bidirectional Parser & Exporter | Reads and writes WebVTT subtitle files with timecode normalization | M4 | Survey (R4) |
| 66 | SUB-03 | Caption Styling Model | Configures typography, fill, outline, shadow, and background box | M4 | Survey (R4) |
| 67 | SUB-04 | Word-by-Word Karaoke Highlighting | Highlights active spoken words based on playhead presentation timestamp | M4 | Survey (R4) |
| 68 | AI-01 | Deterministic Silence Detector | Scans audio RMS energy and flags dead air for automatic cut suggestions | M5 | Survey (R4) |
| 69 | AI-02 | Jump Cut Optimizer | Suggests crisp cuts between speech pauses without clipping speech transients | M5 | Survey (R4) |
| 70 | AI-03 | Talking-Head Pacing Analyzer | Flags long unbroken shots (>8s) and suggests visual variations | M5 | Survey (R4) |
| 71 | AI-04 | Structured EditPlan Approval Gate | Interactive checklist UI for user inspection of AI plans with full atomic undo/redo | M5 | Survey (R4) |
| 72 | AI-05 | Pluggable Speech-to-Text Contract | Extensible offline STT interface for English and Bengali with memory check and fallback | M5 | Survey (R4) |
| 73 | FEAT-EXP-001 | Media3 Transformer Composition Builder | Assemble multitrack Composition with EditedMediaItemSequence for video and audio | M6 | Survey (R3) |
| 74 | FEAT-EXP-002 | Video Effects & Transform Pipeline | Apply Presentation, ScaleAndRotateTransformation, and GlEffect inside Transformer | M6 | Survey (R3) |
| 75 | FEAT-EXP-003 | Export Configuration Dialog | Dialog allowing selection of Resolution (720p/1080p), FPS (24/30/60), Codec (H.264), Aspect Ratio | M6 | Survey (R3) |
| 76 | FEAT-EXP-004 | Pre-Export Storage & Plan Validator | Verify disk storage space (> estimatedBytes + 50MB) and validate all media file paths | M6 | Survey (R3) |
| 77 | FEAT-EXP-005 | Foreground Export Service & Notification | Execute Transformer export in Android Foreground Service with live progress notification | M6 | Survey (R3) |
| 78 | FEAT-EXP-006 | Real-Time Export Progress Reporting | Poll transformer.getProgress() every 250ms and expose state to UI progress sheet | M6 | Survey (R3) |
| 79 | FEAT-EXP-007 | Export Cancellation & File Cleanup | Allow user to cancel ongoing export, abort Transformer, and delete partial output file | M6 | Survey (R3) |
| 80 | FEAT-EXP-008 | MP4 Video Share & SAF Export Intent | Export completed MP4 to user's Movies/Downloads directory via SAF or trigger Share Sheet | M6 | Survey (R3) |
| 81 | FEAT-SYNC-002 | Media3 Transformer Overlay Sync | Media3 CanvasOverlay / GlEffect invoking identical drawing pipeline per export frame | M6 | Survey (R2, R3) |
| 82 | SUB-05 | Media3 Subtitle Burn-In Overlay | Burns styled captions into MP4 video frames during export | M6 | Survey (R4) |
| 83 | PKG-01 | Open-Source Documentation Suite | Comprehensive README.md, ARCHITECTURE.md, CONTRIBUTING.md, and MIT LICENSE | M7 | Survey (R5) |
| 84 | PKG-02 | GitHub Actions CI Pipeline | Automated continuous integration workflow (.github/workflows/android.yml) | M7 | Survey (R5) |
| 85 | PKG-03 | Release Build & Signing Scripts | Gradle configuration for reproducible signed release and debug APK compilation | M7 | Survey (R5) |

---

## Milestones

| # | Name | Scope | Dependencies | Status |
|---|---|---|---|---|
| M1 | Foundation, Project Manifest, Scoped Storage & Domain Core | Multi-module Gradle setup, pure Kotlin domain models (`:core:model`), Room database, `.cutproproj` JSON v1 schema, atomic autosave & recovery, `.cutprobundle` ZIP packaging with Zip-Slip defense, Photo Picker & SAF import with persistable URI permissions, media metadata extraction, and Keystore encryption. | none | DONE |
| M2 | UI Shell, Navigation, Material 3 Dark Canvas & Multitrack Timeline UI | Material 3 Dark/Light themes, zero-emoji vector iconography, $\ge 48\text{dp}$ touch targets, Navigation Compose, Project Dashboard, Top Toolbar, Multitrack Timeline UI (ruler, tracks, draggable playhead scrubber, zoom 10%-500%), Contextual Properties Inspector, and pure timeline math (trimming, splitting, dragging, snapping, undo/redo stack). | M1 | DONE |
| M3 | ExoPlayer Playback Engine, Preview Canvas & Multitrack Audio Tools | Coordinated `PreviewPlaybackCoordinator`, single-instance ExoPlayer hardware decoder for V1, frame-accurate seeking (`SEEK_CLOSEST_SYNC` vs `SEEK_EXACT`), 60fps playhead sync loop, aspect ratio framing (16:9, 9:16, 1:1, etc.) with safe-zone guides and pinch gestures, clip speed scaling (0.1x-10.0x), clip transforms, `VolumeAndFadeAudioProcessor` with sample-accurate PTS calculation, linear/log/exp/s-curve fade envelopes, soft-clipping saturation protection, dynamic audio ducking, and constant-power stereo panning. | M2 | DONE |
| M4 | Declarative Motion Graphics Engine & 21 Offline Built-in Templates | Timestamp-deterministic keyframing (`Keyframe<T>`, `PropertyTimeline<T>`) at microsecond PTS, analytical spring solver (closed-form damped harmonic oscillator), hybrid Newton-Raphson/Bisection cubic Bezier solver, linear RGB color interpolation, shared `MotionGraphicsCanvasRenderer`, 21 offline built-in templates across 7 categories with parameter validation schemas (zero emojis), resilient bidirectional SRT & WebVTT parsers/exporters, caption styling model, and karaoke word highlighting. | M2, M3 | DONE |
| M5 | Modular AI Editing Subsystem & Extensible STT | Deterministic rule-based DSP analyzers (silence detection via RMS audio energy, jump cut optimization, pacing analysis), structured `AiAssistant`, `EditPlan`, and `ApprovalGate` interactive checklist UI with diff preview and compound undo/redo, extensible `SpeechToTextProvider` supporting English and Bengali with low-RAM device protection and offline fallback. | M3, M4 | DONE |
| M6 | Android Media3 Transformer Off-Main-Thread MP4 Video Export Engine | Off-main-thread video rendering and MP4 export via Media3 Transformer (`Composition`, `EditedMediaItemSequence`, `Effects`, `AudioProcessor`), H.264 video, AAC audio, 720p/1080p, 24/30/60 fps, 16:9/9:16/1:1, `CanvasOverlay` / `GlEffect` rendering identical motion graphics and burned-in subtitles, render plan validation, storage pre-check ($> \text{estimatedBytes} + 50\text{MB}$), Android Foreground `ExportService` (`mediaProcessing`) with `WakeLock`, 250ms progress polling, persistent notification, clean cancellation with partial file cleanup, and SAF/Share Sheet export intent. | M3, M4 | DONE |
| M7 | Final Milestone: 100% E2E Pass, Adversarial Coverage Hardening & Release Packaging | Phase 1: Pass 100% of the opaque-box E2E Test Suite (Tiers 1-4). Phase 2: Tier 5 Adversarial Coverage Hardening (white-box challenger gap analysis and fuzzing). Complete GitHub open-source release packaging under MIT license (`README.md`, `ARCHITECTURE.md`, `CONTRIBUTING.md`, `LICENSE`, `.github/workflows/android.yml`, APK build scripts). | M1, M2, M3, M4, M5, M6, E2E Test Suite Ready | DONE |

---

## Interface Contracts

### 1. `:core:model` ↔ `:core:timeline` (Domain Model & Timeline Operations)
```kotlin
package com.cutpro.core.model

data class TimelineClip(
    val id: String,
    val trackId: String,
    val mediaSource: MediaSourceInfo,
    val timelineStartUs: Long,
    val timelineDurationUs: Long,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val speedMultiplier: Float = 1.0f,
    val fitMode: ClipFitMode = ClipFitMode.FIT,
    val transform: ClipTransform = ClipTransform(),
    val audioProperties: ClipAudioProperties = ClipAudioProperties(),
    val customTitle: String? = null
)

interface TimelineEngine {
    fun trimIn(clip: TimelineClip, newInUs: Long): TimelineClip
    fun trimOut(clip: TimelineClip, newOutUs: Long): TimelineClip
    fun splitAt(clip: TimelineClip, splitPlayheadUs: Long): Pair<TimelineClip, TimelineClip>
    fun adjustSpeed(clip: TimelineClip, speedMultiplier: Float): TimelineClip
    fun reposition(clip: TimelineClip, newTimelineStartUs: Long): TimelineClip
    fun findSnapPoint(movingEdgeUs: Long, candidatePointsUs: List<Long>, pixelsPerSec: Float, densityDpi: Float): Long?
}
```

### 2. `:core:model` ↔ `:core:playback` (ExoPlayer Preview Coordinator)
```kotlin
package com.cutpro.core.playback

import kotlinx.coroutines.flow.StateFlow

interface PlaybackCoordinator {
    val playheadUs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    val durationUs: StateFlow<Long>
    
    fun play()
    fun pause()
    fun seekTo(targetUs: Long, isScrubbing: Boolean)
    fun setTimeline(project: ProjectManifest)
    fun release()
}
```

### 3. `:core:motiongraphics` ↔ `:core:playback` / `:core:export` (Unified Canvas Renderer)
```kotlin
package com.cutpro.core.motiongraphics

import android.graphics.Canvas

interface MotionGraphicsRenderer {
    fun renderAt(
        canvas: Canvas,
        viewportWidth: Int,
        viewportHeight: Int,
        presentationTimestampUs: Long,
        templateInstance: TemplateInstance
    )
}
```

### 4. `:core:audio` ↔ `:core:playback` / `:core:export` (Volume & Fade Audio Processor)
```kotlin
package com.cutpro.core.audio

import androidx.media3.common.audio.AudioProcessor

enum class FadeCurveType { LINEAR, EXPONENTIAL, LOGARITHMIC, SCURVE }

interface VolumeAndFadeProcessorProvider {
    fun createProcessor(
        clipStartUs: Long,
        clipDurationUs: Long,
        volume: Float,
        fadeInUs: Long,
        fadeInCurve: FadeCurveType,
        fadeOutUs: Long,
        fadeOutCurve: FadeCurveType
    ): AudioProcessor
}
```

### 5. `:core:ai` ↔ `:core:timeline` (Modular AI Assistant & Approval Gate)
```kotlin
package com.cutpro.core.ai

interface AiAssistant {
    suspend fun analyzeTimeline(timeline: Timeline, options: AnalysisOptions): Result<List<EditSuggestion>>
    suspend fun generateEditPlan(prompt: String, timeline: Timeline): Result<EditPlan>
}

interface ApprovalGate {
    fun calculateDiff(plan: EditPlan, currentTimeline: Timeline): PlanDiff
    suspend fun applyPlan(plan: EditPlan, approvedItemIds: Set<String>): Result<Unit>
    suspend fun rollbackPlan(planId: String): Result<Unit>
}
```

### 6. `:core:model` ↔ `:core:export` (Media3 Transformer Composition Builder)
```kotlin
package com.cutpro.core.export

import androidx.media3.transformer.Composition
import java.io.File

data class RenderPlan(
    val resolution: ExportResolution,
    val frameRate: ExportFrameRate,
    val aspectRatio: CanvasAspectRatio,
    val videoCodec: VideoCodec,
    val videoBitrateBps: Int,
    val audioBitrateBps: Int,
    val durationUs: Long
)

interface ExportPipeline {
    fun validatePlan(project: ProjectManifest, plan: RenderPlan, outputDir: File): ValidationResult
    fun buildComposition(project: ProjectManifest, plan: RenderPlan): Composition
    fun startExport(composition: Composition, outputFile: File, listener: ExportProgressListener)
    fun cancelExport()
}
```
