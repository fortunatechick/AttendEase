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
| 31 | 12 | System splash screen API | Splash shows icon on brand blue |
| 33 | 13 | Themed (monochrome) icon; per-app language entry | Enable themed icons; Settings > Apps > Language lists English |
| 34 | 14 | Predictive back (`enableOnBackInvokedCallback`) | Back gesture from every screen |
| 35/36 | 15/16 | Forced edge-to-edge; keyboard insets | Text fields stay above the keyboard; nothing under the status bar |

## How to reproduce

```bash
./gradlew testDebugUnitTest lintDebug
./gradlew connectedReleaseAndroidTest -PtestBuildType=release   # needs keystore.properties and a running device
```
