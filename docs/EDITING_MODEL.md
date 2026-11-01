# CutPro Non-Linear Editing Domain Model

CutPro models editing sessions as immutable, relational domain trees defined in pure Kotlin with zero Android SDK dependencies.

---

## 1. Domain Entities Hierarchy

```text
ProjectManifest
├── id: String (UUID v4)
├── title: String
├── createdAtUs: Long
├── modifiedAtUs: Long
├── canvasAspectRatio: CanvasAspectRatio (16:9, 9:16, 1:1, 4:5, 21:9)
├── exportResolution: ExportResolution (720p, 1080p, 4K)
├── frameRate: ExportFrameRate (24fps, 30fps, 60fps)
└── tracks: List<Track>
    ├── id: String
    ├── type: TrackType (VIDEO, AUDIO, TEXT, OVERLAY, CAPTION)
    ├── order: Int
    ├── isMuted: Boolean
    ├── isLocked: Boolean
    └── clips: List<TimelineClip>
        ├── id: String
        ├── mediaUri: String
        ├── timelineStartUs: Long
        ├── sourceInUs: Long
        ├── sourceOutUs: Long
        ├── playbackSpeed: Float (0.1x to 10.0x)
        ├── volume: Float (0.0 to 2.0)
        ├── pan: Float (-1.0 to 1.0)
        ├── transform: ClipTransform (Scale, Rotate, Offset)
        └── keyframes: List<TransformKeyframe>
```

---

## 2. Microsecond Presentation Timestamp (PTS)

All temporal coordinates in CutPro are modeled as 64-bit signed integers in microseconds (`Long` representing $\mu\text{s}$):
- `1_000_000L` $\mu\text{s} = 1.0\text{ second}$.
- Sub-millisecond precision avoids rounding drift over long timelines and variable frame rate media.
- SMPTE timecode conversions (`TimecodeHelper`) use midpoint rounding to ensure exact round-trip frame fidelity at 24, 25, 30, and 60 fps.

---

## 3. Non-Destructive Timeline Math

Editing operations never modify source media files on disk:
- **Trimming**: Modifies `sourceInUs` and `sourceOutUs` boundaries, clamping `timelineStartUs >= 0`.
- **Splitting**: Splits a clip at playhead $t_{\mu\text{s}}$ into two adjacent segments $[t_0, t_{\text{split}}]$ and $[t_{\text{split}}, t_1]$ with identical speed and transform parameters.
- **Snapping**: Magnetic snapping evaluates distances against adjacent clip edges and playhead with a configurable threshold ($150\text{ms}$).
- **Undo / Redo**: Executed via the Command Pattern (`UndoRedoManager`) using immutable project snapshot commands (`SnapshotTimelineCommand`), allowing instantaneous rollback without timeline coordinate corruption.
