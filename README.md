# CutPro

**Professional Video Editing. Creative Motion Graphics.**

CutPro is an offline-first, native Android video editing and motion graphics studio built entirely with Kotlin and Jetpack Compose. Inspired by modern desktop non-linear editing suites, CutPro delivers multitrack timeline editing, deterministic motion graphics animation, audio DSP mixing, AI-assisted smart tools, and genuine Media3 Transformer MP4 video export directly on Android phones and tablets.

---

## Key Capabilities

- **Native Android Architecture**: Engineered strictly for Android using Kotlin, Jetpack Compose, Material 3, Room, DataStore, and AndroidX Media3 (ExoPlayer & Transformer). Zero webviews, Electron, React, or browser wrappers.
- **Multitrack Non-Linear Timeline**: Supports video, audio, text, overlay, and caption tracks. Frame-accurate microsecond playhead scrubbing, magnetic snapping, in/out trimming, splitting, speed scaling (0.1x to 10.0x), and full command-pattern undo/redo.
- **Deterministic Motion Graphics Engine**: Presentation timestamp (PTS) synchronized animation engine featuring closed-form analytical damped spring physics, hybrid Newton-Raphson/bisection cubic Bezier curves, and gamma-corrected linear RGB color interpolation.
- **21 Offline Built-in Templates**: Configurable templates across 7 categories (Opening Titles, Kinetic Typography, Lower Thirds, Animated Subtitles, Metric Counters, Call-to-Actions, and Logo Reveals) rendered via unified canvas drawing routines.
- **Media3 Transformer MP4 Export**: Off-main-thread video rendering with H.264/AAC encoding, 720p/1080p resolutions, 24/30/60 fps, and 16:9/9:16/1:1 aspect ratios. Includes Android Foreground Service integration with live progress notifications, CPU wake locks, cancellation cleanup, and MediaStore Scoped Storage publishing.
- **Audio DSP Suite**: Sample-accurate volume curves, logarithmic/exponential/S-curve fade envelopes, soft-clipping saturation limiters, dynamic 4-state audio ducking, and constant-power stereo panning.
- **Subtitles & Karaoke Engine**: Bidirectional SRT and WebVTT parsers/exporters with BOM handling and tag sanitization, caption styling, line-aware word karaoke highlighting, and Media3 video burn-in overlays.
- **Modular AI Assistant**: Deterministic DSP silence detection (20ms frames, -40dBFS threshold, 3dB hysteresis), jump-cut optimization with 80ms safety padding, talking-head pacing analysis, structured user approval gates with reverse-chronological ripple execution, and offline English/Bengali Speech-to-Text contracts with low-RAM guards (<= 500MB).
- **Privacy & Storage Hardening**: Offline-first operation with zero mandatory cloud accounts, zero hidden network analytics, Zip-Slip/Zip-Bomb protected `.cutprobundle` archives, and Android Keystore AES-256-GCM encryption.
- **Strict Zero-Emoji UI**: Adheres to a strict zero-emoji design system featuring professional vector icons and Material 3 Dark Slate aesthetics.

---

## Technology Stack

| Layer | Technologies |
|---|---|
| Language | Kotlin (Coroutines, StateFlow, Serialization) |
| UI Framework | Jetpack Compose, Material 3, Navigation Compose |
| Audio & Video Preview | AndroidX Media3 ExoPlayer |
| Media Export & Effects | AndroidX Media3 Transformer, Media3 Effect, CanvasOverlay |
| Persistence & Storage | Room SQLite, AndroidX DataStore, Scoped Storage (SAF / MediaStore) |
| Security | Android Keystore (AES-256-GCM, EncryptedSharedPreferences) |
| Build System | Gradle with Kotlin DSL, Version Catalog (`libs.versions.toml`) |
| Testing | JUnit 4, Robolectric, AndroidX Test, Custom Opaque-Box E2E Suite (915 tests) |

---

## Project Structure

```text
cutpro/
├── app/                          # Application entry, MainActivity, Navigation Host
├── core/
│   ├── common/                   # Timecode math, Result<T>, Dispatchers, AppError
│   ├── model/                    # Pure Kotlin domain entities (Project, Track, Clip, Timecode)
│   ├── data/                     # Room DB, DAOs, .cutproproj JSON v1, .cutprobundle ZIP packaging
│   ├── timeline/                 # Timeline state engine, Trim, Split, Snapping, Undo/Redo stack
│   ├── playback/                 # ExoPlayer coordinator, Adaptive seeking, Canvas framing guides
│   ├── audio/                    # DSP processors, Volume/fade curves, Stereo panning, Ducking
│   ├── motiongraphics/           # Keyframing engine, Analytical spring/Bezier solvers, 21 templates
│   ├── subtitles/                # SRT & WebVTT parsers/exporters, Karaoke word engine, Overlays
│   ├── ai/                       # Silence detector, Jump-cut optimizer, Pacing analyzer, STT contracts
│   ├── export/                   # Media3 Transformer engine, Composition builder, Overlays
│   ├── security/                 # Scoped storage URI persistence, Keystore AES-256-GCM
│   └── designsystem/             # Material 3 Dark theme tokens, Touch boundaries, Vector icons
├── feature/
│   ├── dashboard/                # Project dashboard, Recent grid, Template gallery, Settings
│   ├── editor/                   # TopToolbar, PreviewCanvasArea, MultitrackTimeline, Inspector
│   └── export/                   # ExportForegroundService, PreExportValidator, Dialog, Progress
├── docs/                         # In-depth architectural, rendering, and setup specifications
├── tests/e2e/                    # Comprehensive 915-test opaque-box verification test suite
└── .github/workflows/            # GitHub Actions CI pipeline
```

---

## Build and Run Instructions

### Prerequisites
- Android Studio Ladybug or newer.
- JDK 17 (recommended: Microsoft OpenJDK 17 or Eclipse Temurin 17).
- Android SDK Platform 34 and Build-Tools 34.0.0.

### Building via Command Line

1. Set the Java environment path:
   ```bash
   export JAVA_HOME="/path/to/jdk-17"
   # On Windows PowerShell:
   # $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
   ```

2. Compile the Debug APK:
   ```bash
   ./gradlew assembleDebug
   # On Windows:
   # .\gradlew.bat :app:assembleDebug
   ```
   The compiled APK will be output to:
   `app/build/outputs/apk/debug/app-debug.apk`

3. Run Unit and Integration Tests:
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. Run the 915-Test Opaque-Box E2E Suite:
   ```bash
   python tests/e2e/runner.py
   ```

---

## Architectural Highlights

### Presentation Timestamp (PTS) Parity
A critical design requirement of CutPro is that animated overlays, subtitle cues, and visual transforms look identical in both interactive ExoPlayer preview and final Media3 Transformer MP4 exports. This is achieved by evaluating all animations as stateless functions of presentation time:
$$\text{LayerState} = f(\text{presentationTimestampUs}, \text{TemplateParameters})$$
Neither wall-clock time nor frame indices are relied upon, eliminating drift across variable frame rates or export speeds.

### Memory & Low-Spec Optimization
- Audio processing uses zero-drift 64-bit microsecond tracking across segmented PCM buffers.
- Offline STT features enforce a strict memory ceiling ($\le 500\text{MB}$ RAM threshold check) before initialization.
- Pre-export storage verification ensures that render target partitions maintain safety headroom ($> \text{estimatedOutputSize} + 50\text{MB}$).

---

## License

CutPro is distributed under the [MIT License](LICENSE).
Third-party notices and licenses are documented in [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md).
