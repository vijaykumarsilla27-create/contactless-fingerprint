# Deviations & Open Questions — Read Before the Walkthrough Call

This documents every point where the reference guide's own materials
disagreed with each other, plus every deliberate choice made in this
implementation. Nothing below was resolved silently — each item states
what was chosen and why, so it can be defended or changed on request.

## 1. Spec prose vs. example code conflicts (the two that matter most)

These are not implementation bugs — they're places where the reference
guide's **written requirements** and its **own example code** point in
different directions. Both are preserved rather than picking one silently:

### 1a. Perspective correction (Step 4)
- **Prose says:** "Perform a perspective warp to square off non-orthogonal
  planar angles."
- **Snippet C's code does:** rotation only (`warpAffine` via `minAreaRect`),
  no `warpPerspective` call anywhere.
- **Resolution:** `ImageRectifier.alignFinger()` matches Snippet C exactly
  (rotation only, this is the default path). `applyPerspectiveCorrection()`
  is added as a separate, opt-in method — implements the prose requirement,
  but is not called by default. One line to wire in if the answer is
  "yes we want it."

### 1b. FIR header fields (Step 5)
- **Prose says:** header must include Capture Device ID, Resolution/DPI,
  and Impression Type (`0x09` = Contactless Unconstrained).
- **Snippet D's code does:** magic, version, record length, width, height,
  bit depth only — none of the three prose-required fields are present.
- **Resolution:** `FirEncoder.createFirRecord()` matches Snippet D exactly
  (default path, matches the example). `createExtendedFirRecord()` adds
  device ID / DPI / impression type per the prose, opt-in, not called by
  default.

## 2. A bug fixed in the reference example (flagged, not silent)

`FirEncoder`'s Snippet D computes `totalLength = 32 + jp2Data.size`, but
the header the same code actually writes is 17 bytes (4 magic + 4 version
+ 4 length + 2 width + 2 height + 1 depth), not 32. Implemented literally,
every record's length field would be wrong by 15 bytes, breaking any
parser that trusts it to seek to the payload. Fixed to the correct value
in `createFirRecord()`. Worth asking whether "32" was meant to reserve
space for the fields dropped from the simplified example (exactly the
ones `createExtendedFirRecord()` adds).

## 3. A possible color-space inconsistency, not resolved either way

Snippet C's `processAndEnhance()` uses `COLOR_RGB2GRAY`. `ImageUtils`
converts camera frames YUV → BGR (OpenCV's conventional order). If the
Mat reaching `ImageRectifier` is genuinely BGR, `RGB2GRAY` would apply
the R/B luminance weights swapped from what's intended. Left exactly as
Snippet C specifies rather than silently "fixing" it — worth confirming
which conversion is actually upstream in the intended architecture before
touching this line.

## 4. Architectural fix required to make Section 6's log output possible

Snippet B's `FrameAnalyzer` owns a **private** `BenchmarkLogger` and only
ever logs the Detection stage. But Section 6's expected verification log
shows **all four stages in one combined summary**. A private-per-analyzer
logger can't produce that — it can only ever report on Detection. The
`BenchmarkLogger` is now an **injected, shared** constructor parameter,
owned once by `MainActivity` and passed to both `FrameAnalyzer` and the
new `CaptureProcessor` (not present in the reference snippets, but
required for a place to run the remaining three stages, since Snippet B's
`FrameAnalyzer` explicitly just gates and hands off the raw `ImageProxy`).

## 5. Full method/API alignment vs. earlier prototype (already corrected)

| Area | Now matches |
|---|---|
| `BenchmarkLogger` | `startStage(name)` / `stopStage(name)` / `printSummary()`, tag `"PipelineTimer"`, stage names `"Detection"`, `"Segmentation"`, `"Rectification"`, `"FIR Encoding"` (with the space) |
| `FrameAnalyzer` | Exact constructor shape, `detector.detectAndCheckQuality(imageProxy): Boolean`, hands off `ImageProxy` uncosed on trigger, closes it itself on no-trigger |
| `ImageRectifier` | Two-method split (`alignFinger`, `processAndEnhance`), `RGB2GRAY`, CLAHE clip `2.0`, min-max normalize step |
| `FirEncoder` | `createFirRecord(jp2Data, width, height)` signature and field order match Snippet D exactly |

## 6. Still open / outside this submission's scope

- No real contactless fingerprint captures were used for testing —
  see `report.pdf` in the Python prototype submission for the synthetic-data
  caveat, which still applies here.
- JPEG2000 encoding depends on OpenCV-Android being built with OpenJPEG
  support, which stock OpenCV-Android AARs typically do not include —
  `CaptureProcessor.encodeToJp2()` falls back to PNG bytes if `.jp2`
  encoding throws, and this should be confirmed against whichever OpenCV
  distribution the project actually links.
- This project has not been compiled or run — there was no Android
  SDK/Gradle toolchain available in the environment this was written in.
  Everything here is structurally correct Kotlin matching the package
  layout and dependencies specified, but treat it as unverified until it's
  opened in Android Studio.
