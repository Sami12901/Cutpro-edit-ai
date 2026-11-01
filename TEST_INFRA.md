# CutPro End-to-End Test Infrastructure Specification

Document Version: 1.0.0
Author: teamwork_test_writer
Integrity Mode: Development / Production-Grade
Scope: Headless Opaque-Box E2E Testing for CutPro Native Android Video Editor

---

## 1. Test Philosophy & Principles

### 1.1 Opaque-Box Testing
CutPro E2E tests are strictly opaque-box (black-box). The test harnesses and assertions interact solely with:
1. Public interface contracts (as defined in PROJECT.md).
2. Data schemas and serialized project manifests (.cutproproj JSON v1, .cutprobundle archives, SRT, WebVTT).
3. Mathematical and physical invariants (presentation timestamp determinism, microsecond timecode math, closed-form easing curves, sample-accurate audio gain curves).
4. System boundaries and security constraints (storage space pre-checks, Zip-Slip path traversal defense, URI persistence contracts).

No test depends on private implementation details, internal class state, or reflection hacks. This guarantees that implementation changes within architectural modules do not break tests as long as behavioral contracts and invariants hold.

### 1.2 Zero-Emoji Mandate
In strict accordance with ORIGINAL_REQUEST.md (Requirements R1, R5 and Acceptance Criteria), zero emojis are permitted anywhere in the CutPro codebase, user interface, templates, error messages, log outputs, test specifications, test code, or test reporting. All console and file reports must use professional plain text formatting, ASCII indicators ([PASS], [FAIL], [SKIP]), and structured JSON/XML metrics.

### 1.3 Determinism and Reproducibility
Video editing requires absolute synchronization between real-time playback and offline rendering. Every animation, easing curve, timecode split, audio fade, and subtitle cue calculation must be a pure, deterministic function of microsecond Presentation Time Stamps (PTS):
$$f(PTS_{us}) \to EvaluatedState$$
Tests must execute identically and yield bit-for-bit identical results regardless of wall-clock execution time, execution speed, thread scheduling, or test execution order.

---

## 2. Test Design Methodology

CutPro E2E test suites are engineered using four formal software testing methodologies:

### 2.1 Category-Partition Testing
For each feature identified in PROJECT.md:
1. Identify environmental characteristics, operational modes, and input parameters.
2. Partition the input domain into disjoint equivalence classes (e.g., valid media formats, speed multiplier tiers [sub-1x slow-motion, 1x normal, super-1x fast-motion], aspect ratio dimensions).
3. Formulate representative test cases covering every partition.

### 2.2 Boundary Value Analysis (BVA)
Defects cluster at domain boundaries. For all numerical and temporal parameters, tests evaluate:
- Minimum valid value ($x_{min}$)
- Just above minimum ($x_{min}^+$)
- Nominal value ($x_{nominal}$)
- Just below maximum ($x_{max}^-$)
- Maximum valid value ($x_{max}$)
- Invalid boundary extremes ($x_{min}^-$, $x_{max}^+$, $0$, negative, NaN, $\infty$)

Key boundaries include:
- Timecode: $0\,\mu s$, $1\,\mu s$, $100\,000\,\mu s$ (minimum clip trim threshold), $2^{63}-1\,\mu s$.
- Speed: $0.1x$ (minimum), $1.0x$ (nominal), $10.0x$ (maximum), $\le 0.0x$ (rejected).
- Audio Gain: $0.0$ (mute), $1.0$ (unity), $2.0$ (maximum boost / $+6\text{ dB}$), with soft clipping at $\pm 32767$.
- Bezier Control Points: $x \in [0.0, 1.0]$.
- Spring Physics: $\zeta < 1.0$ (underdamped), $\zeta = 1.0$ (critically damped limit), $\zeta > 1.0$ (overdamped).

### 2.3 Pairwise Combinatorial Testing
Multitrack editing involves multi-dimensional feature interactions. Pairwise combinatorial testing ensures every two-way combination of interacting parameters is evaluated:
- Trimming (In/Out) $\times$ Playback Speed ($0.5x, 1.0x, 2.0x$) $\times$ Timeline Splitting.
- Audio Volume Gain $\times$ Fade-In/Out Envelopes $\times$ Dynamic Ducking.
- Aspect Ratio ($16:9, 9:16, 1:1$) $\times$ Fit Mode (Fit, Fill, Stretch) $\times$ Export Resolution ($720p, 1080p$).
- Subtitle Format (SRT, WebVTT) $\times$ Word Timings $\times$ Media3 Burn-In Overlay.
- Project Manifest Schema $\times$ Autosave Atomic Rename $\times$ Bundle Packaging.

### 2.4 Real-World Workload Testing (Tier 4 Scenarios)
Synthetic isolated tests are complemented by realistic, end-to-end multi-track video editing production workflows matching professional creator use cases:
1. Scenario 1: YouTube Tech Review / Talking-Head Video (16:9 landscape, multi-clip V1, B-Roll overlay, dialogue audio with BGM ducking, lower-third titles, SRT captions, 1080p 30fps export).
2. Scenario 2: Short-Form Mobile Reel (9:16 vertical portrait, fast jump cuts, kinetic typography pop bounce, karaoke word-highlight captions, 60fps export).
3. Scenario 3: Cinematic Film Scene (16:9, 24fps cinematic, 0.5x slow-motion clip, cinematic split title, S-curve fade-in/fade-out, constant-power stereo panning).
4. Scenario 4: Social Product Promo (1:1 square canvas, logo reveal animation, animated numeric metric counter, call-to-action button, high-bitrate export).
5. Scenario 5: Long-Form Podcast / Interview (multitrack dialogue, automated silence detection, pacing analyzer recommendations, structured edit plan approval/rollback, export plan verification).

---

## 3. Complete Feature Inventory & Test Target Allocation

Every feature cataloged in PROJECT.md is assigned strict minimum test targets across Tier 1 (Feature Coverage) and Tier 2 (Boundary & Corner Cases):

| Feature ID | Feature Name | Milestone | Tier 1 Target | Tier 2 Target | Total Unit Targets |
|---|---|---|---|---|---|
| FEAT-CORE-001 | Multi-Module Clean Architecture | M1 | >= 5 | >= 5 | >= 10 |
| FEAT-CORE-002 | Pure Domain Model Engine | M1 | >= 5 | >= 5 | >= 10 |
| PER-01 | Room SQLite Database Engine | M1 | >= 5 | >= 5 | >= 10 |
| PER-02 | Versioned .cutproproj JSON v1 Schema | M1 | >= 5 | >= 5 | >= 10 |
| PER-03 | Non-Corrupting Atomic Autosave | M1 | >= 5 | >= 5 | >= 10 |
| PER-04 | Missing-Media Detection & Relinking | M1 | >= 5 | >= 5 | >= 10 |
| PER-05 | .cutprobundle ZIP Packager | M1 | >= 5 | >= 5 | >= 10 |
| SEC-01 | Zip-Slip Path Traversal Protection | M1 | >= 5 | >= 5 | >= 10 |
| SEC-02 | Scoped Storage URI Persistence | M1 | >= 5 | >= 5 | >= 10 |
| SEC-03 | Android Keystore Data Encryption | M1 | >= 5 | >= 5 | >= 10 |
| FEAT-IMP-001 | Android Photo Picker Integration | M1 | >= 5 | >= 5 | >= 10 |
| FEAT-IMP-002 | Storage Access Framework (SAF) Import | M1 | >= 5 | >= 5 | >= 10 |
| FEAT-IMP-003 | Media Metadata Extraction & Validation | M1 | >= 5 | >= 5 | >= 10 |
| FEAT-IMP-004 | Thumbnail Proxy Strip Generator | M1 | >= 5 | >= 5 | >= 10 |
| FEAT-UI-001 | Material 3 Dark/Light Theme Shell | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-UI-002 | Zero-Emoji Strict Iconography | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-UI-003 | Accessible Touch Targets (48dp) | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-UI-004 | Project Dashboard & Recent Grid | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-UI-005 | Top Toolbar Action Controls | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-UI-006 | Multitrack Timeline Canvas UI | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-UI-007 | Draggable Playhead Scrubber | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-UI-008 | Contextual Properties Inspector | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-TL-001 | Timeline Clip Repositioning & Drag | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-TL-002 | In-Point & Out-Point Trimming Engine | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-TL-003 | Clip Splitting at Playhead | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-TL-004 | Magnetic Snapping Engine | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-TL-005 | Undo / Redo Command Stack | M2 | >= 5 | >= 5 | >= 10 |
| FEAT-PLAY-001 | ExoPlayer Preview Surface Engine | M3 | >= 5 | >= 5 | >= 10 |
| FEAT-PLAY-002 | Master Playhead Synchronization Loop | M3 | >= 5 | >= 5 | >= 10 |
| FEAT-PLAY-003 | Adaptive Scrubbing Performance | M3 | >= 5 | >= 5 | >= 10 |
| FEAT-PLAY-004 | Aspect Ratio Canvas Framing & Guides | M3 | >= 5 | >= 5 | >= 10 |
| FEAT-TL-006 | Clip Speed Adjustment | M3 | >= 5 | >= 5 | >= 10 |
| FEAT-TL-007 | Clip Transform & Fit Mode | M3 | >= 5 | >= 5 | >= 10 |
| AUD-01 | Per-Clip Volume & Gain | M3 | >= 5 | >= 5 | >= 10 |
| AUD-02 | Fade-In Envelope | M3 | >= 5 | >= 5 | >= 10 |
| AUD-03 | Fade-Out Envelope | M3 | >= 5 | >= 5 | >= 10 |
| AUD-04 | Track Mute & Solo | M3 | >= 5 | >= 5 | >= 10 |
| AUD-05 | Dynamic Audio Ducking | M3 | >= 5 | >= 5 | >= 10 |
| AUD-06 | Constant-Power Stereo Panning | M3 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-001 | Keyframe Creation & Storage | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-002 | Keyframe Modification | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-003 | Keyframe Deletion | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-004 | Linear Easing Evaluator | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-005 | Standard Cubic Curves Evaluator | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-006 | Parametric Cubic Bezier Solver | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-007 | Analytical Spring Solver | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-008 | Position 2D Interpolation | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-009 | Scale 2D Interpolation | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-010 | Rotation Interpolation | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-011 | Opacity Interpolation | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-012 | Color/Tint Linear RGB Interpolation | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-013 | Typography Text Layout Engine | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-014 | Typewriter String Slicing | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-MG-015 | Shape Geometry Morphing & Stroking | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-TPL-001 | Opening Titles Templates (3) | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-TPL-002 | Kinetic Typography Templates (3) | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-TPL-003 | Lower Thirds Templates (3) | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-TPL-004 | Animated Subtitles Templates (3) | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-TPL-005 | Animated Counters Templates (3) | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-TPL-006 | Call-to-Actions Templates (3) | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-TPL-007 | Logo Reveals Templates (3) | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-SYNC-001 | Compose Canvas Preview Renderer | M4 | >= 5 | >= 5 | >= 10 |
| FEAT-SER-001 | Template Manifest JSON Serialization | M4 | >= 5 | >= 5 | >= 10 |
| SUB-01 | SRT Bidirectional Parser & Exporter | M4 | >= 5 | >= 5 | >= 10 |
| SUB-02 | WebVTT Bidirectional Parser & Exporter | M4 | >= 5 | >= 5 | >= 10 |
| SUB-03 | Caption Styling Model | M4 | >= 5 | >= 5 | >= 10 |
| SUB-04 | Word-by-Word Karaoke Highlighting | M4 | >= 5 | >= 5 | >= 10 |
| AI-01 | Deterministic Silence Detector | M5 | >= 5 | >= 5 | >= 10 |
| AI-02 | Jump Cut Optimizer | M5 | >= 5 | >= 5 | >= 10 |
| AI-03 | Talking-Head Pacing Analyzer | M5 | >= 5 | >= 5 | >= 10 |
| AI-04 | Structured EditPlan Approval Gate | M5 | >= 5 | >= 5 | >= 10 |
| AI-05 | Pluggable Speech-to-Text Contract | M5 | >= 5 | >= 5 | >= 10 |
| FEAT-EXP-001 | Media3 Transformer Composition Builder | M6 | >= 5 | >= 5 | >= 10 |
| FEAT-EXP-002 | Video Effects & Transform Pipeline | M6 | >= 5 | >= 5 | >= 10 |
| FEAT-EXP-003 | Export Configuration Dialog | M6 | >= 5 | >= 5 | >= 10 |
| FEAT-EXP-004 | Pre-Export Storage & Plan Validator | M6 | >= 5 | >= 5 | >= 10 |
| FEAT-EXP-005 | Foreground Export Service & Notification | M6 | >= 5 | >= 5 | >= 10 |
| FEAT-EXP-006 | Real-Time Export Progress Reporting | M6 | >= 5 | >= 5 | >= 10 |
| FEAT-EXP-007 | Export Cancellation & File Cleanup | M6 | >= 5 | >= 5 | >= 10 |
| FEAT-EXP-008 | MP4 Video Share & SAF Export Intent | M6 | >= 5 | >= 5 | >= 10 |
| FEAT-SYNC-002 | Media3 Transformer Overlay Sync | M6 | >= 5 | >= 5 | >= 10 |
| SUB-05 | Media3 Subtitle Burn-In Overlay | M6 | >= 5 | >= 5 | >= 10 |
| PKG-01 | Open-Source Documentation Suite | M7 | >= 5 | >= 5 | >= 10 |
| PKG-02 | GitHub Actions CI Pipeline | M7 | >= 5 | >= 5 | >= 10 |
| PKG-03 | Release Build & Signing Scripts | M7 | >= 5 | >= 5 | >= 10 |
| **TOTALS** | **85 Inventoried Features** | -- | **>= 425** | **>= 425** | **>= 850** |

---

## 4. Test Tier Architecture & Coverage Thresholds

### Tier 1: Feature Coverage (Target: >= 425 tests)
- Minimum 5 primary happy-path and contract verification test cases per feature.
- Validates that standard operational parameters produce correct models, state transitions, and computations.
- Exercises data formats, getters/setters, default values, and standard calculations.

### Tier 2: Boundary & Corner Cases (Target: >= 425 tests)
- Minimum 5 edge-case, boundary-value, or invalid-input test cases per feature.
- Tests $0$, negative durations, null/empty collections, max bounds ($2^{63}-1$), division by zero, floating point edge cases (NaN, $\pm\infty$), corrupt strings, invalid color hex, and file traversal attempts.
- Verifies graceful error recovery, input clamping, exception throwing, and zero crashes.

### Tier 3: Cross-Feature Combinations (Target: >= 50 tests)
- Evaluates pairwise combinatorial interactions across subsystem boundaries:
  - Timeline Trimming + Speed Multipliers + Clip Splitting.
  - Multitrack Audio Mixing + Gain Scaling + S-Curve Fades + Dynamic Ducking.
  - Keyframe Interpolation + Motion Graphics Templates + Canvas Resolution Scaling.
  - SRT/WebVTT Subtitles + Word Karaoke Highlighting + Media3 Burn-In Overlay.
  - Project Manifest Serialization + Autosave Two-Phase Rename + Bundle Archive Packaging with Zip-Slip Validation.

### Tier 4: Real-World Application Scenarios (Target: >= 5 production workflows)
- Complete, multi-step creator projects executed from blank canvas to export validation:
  1. `test_scenario_youtube_review`: 16:9 Landscape, 1080p 30fps Tech Review project with V1 talking head, V2 B-roll overlay, A1 dialogue, A2 background music with ducking, Lower-Third template, SRT subtitles, and export plan validation.
  2. `test_scenario_tiktok_reels`: 9:16 Vertical, 1080x1920 60fps Short-Form Reel with high-speed jump cuts, kinetic typography bounce template, karaoke word highlighting, audio fade-outs, and render plan validation.
  3. `test_scenario_cinematic_doc`: 16:9 Cinematic, 1080p 24fps Documentary Scene with 0.5x slow-motion video, cinematic title reveal, constant-power audio panning, exponential fade envelopes, and pre-export storage check.
  4. `test_scenario_product_promo`: 1:1 Square, 1080x1080 30fps Product Promo with logo reveal animation, animated counter from 0 to 100K, call-to-action button, and render plan validation.
  5. `test_scenario_podcast_workflow`: Multitrack audio interview with automated silence detection, pacing analyzer suggestions, structured EditPlan approval and rollback, and project bundle packaging.

---

## 5. Test Infrastructure Directory Layout

All E2E test files are organized cleanly under `tests/e2e/`:

```
tests/e2e/
├── runner.py                          # Automated test execution engine & CLI
├── run_tests.py                       # User-friendly test runner entry point
├── run_e2e.ps1                        # PowerShell runner script
├── run_e2e.sh                         # Unix/Linux shell script
├── framework/
│   ├── __init__.py
│   ├── assertions.py                  # Assertion utilities (PTS tolerance, microsecond math)
│   ├── models.py                      # Pure domain simulation models matching :core:model
│   ├── reporter.py                    # ASCII console, JSON, and JUnit XML reporters (0 emojis)
│   └── test_case.py                   # Base OpaqueBoxTestCase with lifecycle hooks
├── tier1_features/
│   ├── __init__.py
│   ├── test_core_architecture.py      # FEAT-CORE-001, FEAT-CORE-002
│   ├── test_persistence.py            # PER-01, PER-02, PER-03, PER-04, PER-05
│   ├── test_security.py               # SEC-01, SEC-02, SEC-03
│   ├── test_media_import.py           # FEAT-IMP-001 .. FEAT-IMP-004
│   ├── test_ui_spec.py                # FEAT-UI-001 .. FEAT-UI-008
│   ├── test_timeline_engine.py        # FEAT-TL-001 .. FEAT-TL-007
│   ├── test_playback_engine.py        # FEAT-PLAY-001 .. FEAT-PLAY-004
│   ├── test_audio_engine.py           # AUD-01 .. AUD-06
│   ├── test_motion_graphics.py        # FEAT-MG-001 .. FEAT-MG-015
│   ├── test_templates.py              # FEAT-TPL-001 .. FEAT-TPL-007
│   ├── test_sync_engine.py            # FEAT-SYNC-001, FEAT-SYNC-002, FEAT-SER-001
│   ├── test_subtitles.py              # SUB-01 .. SUB-05
│   ├── test_ai_subsystem.py           # AI-01 .. AI-05
│   ├── test_export_pipeline.py        # FEAT-EXP-001 .. FEAT-EXP-008
│   └── test_release_packaging.py      # PKG-01, PKG-02, PKG-03
├── tier2_boundaries/
│   ├── __init__.py
│   ├── test_boundary_core.py          # Boundary cases for core & persistence
│   ├── test_boundary_security.py      # Boundary cases for security & import
│   ├── test_boundary_ui.py            # Boundary cases for UI & controls
│   ├── test_boundary_timeline.py      # Boundary cases for timeline & playback
│   ├── test_boundary_audio.py         # Boundary cases for audio tools
│   ├── test_boundary_motion.py        # Boundary cases for motion graphics & templates
│   ├── test_boundary_subtitles.py     # Boundary cases for subtitles & AI
│   ├── test_boundary_export.py        # Boundary cases for export pipeline
│   └── test_boundary_packaging.py     # Boundary cases for release packaging
├── tier3_combinations/
│   ├── __init__.py
│   ├── test_pairwise_timeline_audio.py    # Pairwise: Trimming, Speed, Fades, Volume
│   ├── test_pairwise_motion_export.py     # Pairwise: Keyframes, Templates, RenderPlan
│   ├── test_pairwise_subtitles_ai.py      # Pairwise: Silence Removal, SRT/VTT, Burn-In
│   └── test_pairwise_storage_bundle.py    # Pairwise: Scoped Storage, Bundle, Zip-Slip
├── tier4_scenarios/
│   ├── __init__.py
│   ├── test_scenario_youtube_review.py    # Production Workflow 1
│   ├── test_scenario_tiktok_reels.py      # Production Workflow 2
│   ├── test_scenario_cinematic_doc.py     # Production Workflow 3
│   ├── test_scenario_product_promo.py     # Production Workflow 4
│   └── test_scenario_podcast_workflow.py  # Production Workflow 5
└── reports/                           # Output directory for structured test run results
    ├── e2e_results.json               # Machine-readable JSON summary
    └── junit_report.xml               # Standard JUnit XML report for CI/CD
```

---

## 6. Execution Command & Pass/Fail Semantics

### 6.1 Invocation Commands
The test suite can be executed cleanly on any development machine or CI agent:

- **Run all tiers**:
  ```powershell
  python tests/e2e/run_tests.py --all
  ```
  or via PowerShell wrapper:
  ```powershell
  .\tests\e2e\run_e2e.ps1 -Tier all
  ```

- **Run specific tier**:
  ```powershell
  python tests/e2e/run_tests.py --tier 1
  python tests/e2e/run_tests.py --tier 2
  python tests/e2e/run_tests.py --tier 3
  python tests/e2e/run_tests.py --tier 4
  ```

- **Filter by feature or module**:
  ```powershell
  python tests/e2e/run_tests.py --filter "AUD-"
  python tests/e2e/run_tests.py --filter "FEAT-MG-"
  ```

### 6.2 Pass/Fail Semantics & Exit Codes
- **Exit Code 0**: All executed test cases passed with zero errors, zero assertion failures, and zero unhandled exceptions.
- **Exit Code 1**: One or more test cases failed assertions or raised unexpected exceptions.
- **Exit Code 2**: Configuration error or missing test files.

Every test run outputs:
1. ASCII console log showing each test case result `[PASS]` or `[FAIL]` with execution duration.
2. Formatted summary table displaying:
   - Total Tests Executed
   - Passed Tests
   - Failed Tests
   - Skipped Tests
   - Total Execution Time (seconds)
3. Structured output artifacts generated in `tests/e2e/reports/`:
   - `e2e_results.json`: Complete JSON test log including tier counts, timestamps, and error details if any.
   - `junit_report.xml`: Standard XML report ingested by GitHub Actions CI pipeline.
