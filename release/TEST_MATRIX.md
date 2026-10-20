# Test matrix

AttendEase supports **Android 5.0 (API 21) through Android 16 (API 36)**, one universal APK
(no native code, so every CPU architecture works).

## What was run, and where

| Check | Where | Result |
|---|---|---|
| Unit tests (`gradlew testDebugUnitTest`, 98 tests) | JVM | Pass |
| Lint (`gradlew lintDebug`), `NewApi` at minSdk 21 | JVM | 0 errors, no suppressions |
| Instrumented tests on the **minified release** (`connectedReleaseAndroidTest -PtestBuildType=release`): real PdfBox extraction of a placeholder PDF, plus a corrupt-file case | Emulator, API 37.1 (x86_64, 16 KB pages) | 2/2 pass |
| Manual: install over a previous build, launch, dashboard renders, no crash in `logcat -b crash` | Same emulator | Pass |
| APK signature (`apksigner verify --min-sdk-version 21`) | Host | v1 + v2 + v3 verified |
| Tablet, 1280×800 dp landscape (`wm size 2560x1600`, density 320): dashboard and placeholder side by side; tapping a subject opens it in the right pane with no back arrow | Same emulator, resized | Pass (`docs/devices/tablet-two-pane.png`) |
| Live sync between panes: marking a lecture Present on the right updates the overall % and the subject card on the left without a tap | Same | Pass (80.0% → 90.0%) |
| Upload from the left pane opens the import in the right pane; the dashboard refreshes while saving | Same | Pass |
| Rotate tablet to portrait (aspect above 1.4): single pane, back arrow returns, chosen filter chip kept, content centred at 720 dp | Same | Pass |
| Unfolded-foldable size, 841×700 dp (`wm size 2208x1840`, density 420): two panes | Same | Pass (`docs/devices/foldable-unfolded.png`) |
| Keyboard: Esc goes back from a subject; Ctrl+O opens the PDF picker | Same, phone size | Pass |
| Font size 2.0×: dashboard, cards and upload dialog wrap cleanly; count labels shrink to one line | Same | Pass (labels broke mid-word before the fix) |
| Dark mode: status chips switch to dark text on the lighter colours | Same | Pass |
| Right-to-left (app language set to Arabic): layout mirrors, dates localised, card titles sit at the start edge | Same | Pass (after aligning titles to `viewStart`) |
| "Open with" AttendEase from Files: review → save | Same | Pass |
| Wording check (`WordingInstrumentedTest`): resource sentences match calc's English text | Same | Pass |

| Damaged, empty, missing and oversized (25 MB+) PDFs on the minified release: each ends with a clear reason, no crash (`PdfImportInstrumentedTest`, 6 cases incl. 40 random corruptions) | Emulator, API 37.1 | Pass |
| Parser fuzzing: random text, 2000 mangled and every truncated report, 3000-row input (`ParserFuzzTest`) | JVM | Pass |
| Crash report: `am crash`, relaunch shows *AttendEase closed unexpectedly* with *Copy crash report* | Emulator | Pass |
| Another app sends `file:///data/data/<app>/databases/attendease.db` to the upload screen: refused, normal picker opens instead | Emulator | Pass |
| First launch after clearing data: welcome dialog with the disclaimer; About screen | Emulator | Pass |
| **CI device matrix found a real bug:** on Android 5.0 and 6.0 every upload failed (PdfBox-Android's system-font scan overflows the stack). Fixed with `PdfFonts` (bundled font on Android < 8.0); `bundledFontMapperExtractsTheSameText` proves the text is identical | CI emulators API 21/23; local API 37.1 | Fixed; re-run in CI |

## Not run locally (owner / CI to cover)

This development machine could only boot one system image (API 37.1). Older images weren't
installed and the system drive had no space for them. Run these before a public release, either
in Android Studio's Device Manager or in CI with an emulator matrix:

| API | Android | Why it matters | What to check |
|---|---|---|---|
| 21 | 5.0 | minSdk; v1 signature only; no `TimePicker.setHour` (the app falls back to `setCurrentHour`) | Install, upload a PDF, add a lecture with the time picker |
| 23 | 6.0 | Last version without v2-only behaviour | Install and update over an older build |
| 26 | 8.0 | First adaptive icon | Launcher icon shape |
| 29 | 10 | Scoped storage; CSV export through the share sheet | Export CSV |
| 31–32 | 12 / 12L | System splash screen API; two panes (CI runs API 32, as the API 31 image does not boot on GitHub runners) | Splash shows icon on brand blue |
| 33 | 13 | Themed (monochrome) icon; per-app language entry | Enable themed icons; Settings > Apps > Language lists English |
| 34 | 14 | Predictive back (`enableOnBackInvokedCallback`) | Back gesture from every screen |
| 35/36 | 15/16 | Forced edge-to-edge; keyboard insets | Text fields stay above the keyboard; nothing under the status bar |

Not run here, for the owner or CI to check:
- **Separating hinge:** a real book-style foldable or dual-screen device, where the split should sit on the hinge (window extensions level 2+).
- **Dropping a PDF:** drag a PDF from Files onto the dashboard in desktop windowing or on a Chromebook. `adb` can't simulate this.
- **Right-click:** right-click a lecture with a mouse or trackpad (Android 6+).

## How to reproduce

```bash
./gradlew testDebugUnitTest lintDebug
./gradlew connectedReleaseAndroidTest -PtestBuildType=release   # needs keystore.properties and a running device
```
