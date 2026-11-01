# CutPro Video Rendering & Export Pipeline

CutPro integrates AndroidX Media3 Transformer to deliver hardware-accelerated, off-main-thread video rendering and MP4 export.

---

## 1. Export Pipeline Overview

```text
ProjectManifest ──► [ProjectRenderPlanner]
                           │
                           ▼
            Media3 Composition Builder
        ┌──────────────────┴──────────────────┐
        ▼                                     ▼
EditedMediaItemSequence (Video)      EditedMediaItemSequence (Audio)
  - Video Trimming                     - Audio Trimming
  - Aspect Ratio Framing               - Volume & Fade Processors
  - Scale & Rotation Transforms        - Dynamic Ducking Engine
  - CompositionCanvasOverlay           - Constant-Power Panning
    ├── Motion Graphics Templates
    └── Burned-in Subtitles
                           │
                           ▼
                [Media3 Transformer Engine]
                  - Hardware Codec H.264
                  - Hardware Codec AAC
                  - Multiplexer: MP4
                           │
                           ▼
                ExportForegroundService
                  - CPU WakeLock Management
                  - Ongoing Progress Notification
                  - MediaStore IS_PENDING Scoped Storage
```

---

## 2. Deterministic Presentation Timestamp (PTS) Rendering

In CutPro, visual overlays (titles, lower thirds, call-to-actions, and subtitles) are rendered at export time using `androidx.media3.effect.CanvasOverlay` and `androidx.media3.effect.BitmapOverlay`.

Each frame request provides presentation timestamp $t_{\mu\text{s}}$:
1. `MotionGraphicsCanvasOverlay` queries active templates overlapping $t_{\mu\text{s}}$.
2. Animated parameters (position, scale, rotation, opacity, color) are evaluated at $t_{\mu\text{s}}$ using closed-form analytical equations.
3. Canvas draw operations render vector shapes and typography to an OpenGL texture.
4. `SubtitleCanvasOverlay` renders active subtitle cues and karaoke highlights.
5. Transformer multiplexes the rendered texture into the output video stream.

---

## 3. Macroblock Alignment

Hardware video encoders (Qualcomm Snapdragon, MediaTek, Google Tensor, Samsung Exynos) require frame width and height to be integer multiples of the macroblock size (typically 16 pixels, or at least 2 pixels):
- `CanvasAspectRatio.calculateMacroblockDimensions(height, macroblockSize = 16)` guarantees exact dimension alignment.
- Eliminates green lines, encoder crashes, and illegal dimension errors during export.

---

## 4. Foreground Service & Scoped Storage

- **Background Execution**: Export runs inside `ExportForegroundService` with notification channel `export_channel` and ongoing progress reporting (0% to 100%).
- **Wake Lock**: `PowerManager.PARTIAL_WAKE_LOCK` prevents CPU throttling when the screen turns off.
- **Cancellation**: Cancelling an ongoing export terminates Transformer, unlinks partial temp files, and releases wake locks immediately.
- **Publishing**: Completed videos are published to `Movies/CutPro` via Android MediaStore Scoped Storage with `IS_PENDING = 1` during write and `0` on completion.
