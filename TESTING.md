# Testing Guide

This project has not been compiled or run in the environment it was
written in (no Android SDK/Gradle toolchain available there) — see
`DEVIATIONS.md` item 6. Treat everything below as the intended test plan,
not a report of tests that have already passed.

## 1. Two kinds of tests here, and why they're split

| Type | Location | Needs a device/emulator? | What it can test |
|---|---|---|---|
| **JVM unit tests** | `app/src/test/` | No | Pure logic with no OpenCV/Android framework calls |
| **Instrumented tests** | `app/src/androidTest/` | Yes | Anything touching OpenCV `Mat` or Android APIs |

This split exists because OpenCV's Java bindings load a native `.so`
library — that only works on an actual device or emulator, not a plain
JVM unit test. `FirEncoder` is the one pipeline class with zero OpenCV/
Android dependency (it only touches `ByteArray`), so it's the one class
fully covered by fast JVM tests; everything else needs `androidTest`.

## 2. Setup (do this first)

1. Open the project root in Android Studio (Ladybug or newer).
2. Import the OpenCV Android SDK as a module: **File → New → Import
   Module**, point it at the `sdk` folder inside the downloaded
   `OpenCV-android-sdk` (v4.8.0+, per the spec). This creates the
   `:opencv` module that `settings.gradle.kts` and `build.gradle.kts`
   reference — uncomment the `include(":opencv")` line in
   `settings.gradle.kts` once it's imported.
3. Let Gradle sync. Fix any version mismatches Android Studio flags
   between the OpenCV module's compileSdk and this project's (34).
4. Connect a physical device (recommended) or start an emulator with
   camera support enabled (**AVD Manager → Edit → Camera → Front/Back
   set to "Webcam0" or "VirtualScene"** — a plain emulator's simulated
   camera won't produce a real hand, so a physical device is strongly
   preferred for anything past "does it launch."

## 3. Run the automated tests

**JVM unit tests** (fast, no device needed):
```bash
./gradlew testDebugUnitTest
```
Or right-click `app/src/test/` in Android Studio → Run Tests.
Covers: `FirEncoderTest` (header byte layout, the record-length bug fix,
extended-record fields) and `BenchmarkLoggerTest` (via Robolectric, so
`android.util.Log` calls work without a real device).

**Instrumented tests** (needs a connected device/emulator):
```bash
./gradlew connectedDebugAndroidTest
```
Covers: `PipelineInstrumentedTest` — segmentation on a synthetic
skin-tone shape, rectification output size/channel checks, and a
full-chain timing assertion against the 5000ms budget. Uses synthetic
ellipses, not real finger photos (see DEVIATIONS.md) — this validates
pipeline mechanics, not biometric image quality.

## 4. Manual on-device verification

Once it launches on a real device, walk through these — each maps to a
specific requirement in the reference guide:

| Test | Steps | Expected result | Validates |
|---|---|---|---|
| **Permission flow** | Fresh install, launch app | System camera permission dialog appears; denying shows the "Camera permission is required" toast and no crash | Manifest + `MainActivity` permission handling |
| **Live preview** | Launch with permission granted | Camera feed visible in `PreviewView`, green ROI box overlaid, "Hold finger inside the box" status | Step 1 (CameraX integration) |
| **No false trigger on empty scene** | Point camera at an empty desk/wall for 10s | Status text never changes to "Finger detected" | Detection's area-ratio + stability gate |
| **No false trigger on a quick pass-through** | Wave your hand quickly through the ROI box, don't hold still | No trigger fires | The 3-consecutive-frame stability requirement specifically |
| **Blur rejection** | Hold finger in the box but move it slightly (motion blur) | No trigger, or trigger takes noticeably longer than a steady hold | Laplacian-variance blur check |
| **Successful capture** | Hold finger steady inside the box for ~1 second | Status changes to "Finger detected — processing...", then to "Capture complete (N bytes). Total: X ms" | Full detection → segmentation → rectification → FIR encoding chain |
| **Latency budget** | Filter Logcat by tag `PipelineTimer`, repeat a capture 10+ times | Every `Total Latency:` line should read under 5000; watch which stage dominates | Section 6's expected log format, and the core `<=5000ms` constraint |
| **Output file written** | After a successful capture, pull the file: `adb pull /sdcard/Android/data/com.biometrics.contactless/files/` | A `capture_<timestamp>.iso` file exists and is nonzero size | `MainActivity.saveFirRecord()` |
| **Repeat capture without restart** | After one successful capture, hold finger in the box again | A second capture cycle runs and completes independently | The `isProcessingCapture` re-arm logic in `FrameAnalyzer` |
| **Failure path** | Hard to trigger deliberately, but: try an oddly-shaped or partially-visible skin object that passes detection but might fail segmentation | Status text shows "Capture failed at: \<stage\>" instead of crashing | Error handling in `CaptureProcessor` |

## 5. Reading the Logcat output

Filter by tag in Android Studio's Logcat panel: `tag:PipelineTimer`.
Expected shape (exact format from Section 6 of the reference guide):

```
D/PipelineTimer: Stage [Detection] completed in: <N> ms
D/PipelineTimer: Stage [Segmentation] completed in: <N> ms
D/PipelineTimer: Stage [Rectification] completed in: <N> ms
D/PipelineTimer: Stage [FIR Encoding] completed in: <N> ms
I/PipelineTimer: === PIPELINE PERFORMANCE SUMMARY ===
I/PipelineTimer:  -> Detection: <N> ms
I/PipelineTimer:  -> Segmentation: <N> ms
I/PipelineTimer:  -> Rectification: <N> ms
I/PipelineTimer:  -> FIR Encoding: <N> ms
I/PipelineTimer: Total Latency: <N> ms
```

If `Segmentation` is consistently the largest number (expected, based on
both the Python prototype's profiling and the spec's own budget
allocation giving it the largest individual allowance), that's the stage
to profile first if the total ever creeps toward the 5000ms ceiling —
see the native C++/JNI stub in `app/src/main/cpp/` as the escape hatch
if it does.

## 6. What still can't be tested without more than this codebase

- Real fingerprint image quality (ridge clarity, minutiae usability) —
  needs actual finger captures, not synthetic test shapes.
- True JPEG2000 output — depends on whether the linked OpenCV-Android
  build includes OpenJPEG support; confirm with a quick check of
  whether `CaptureProcessor.encodeToJp2()` is hitting the `.jp2` path or
  silently falling back to PNG (add a temporary log line if unsure).
- Performance on the specific hardware baseline named in the spec
  (Snapdragon 680/778G, Dimensity 700) — timing will vary meaningfully
  by device; test on the actual target class of hardware before treating
  any single device's numbers as representative.
