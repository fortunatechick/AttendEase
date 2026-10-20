# Release audit (Phase 0)

Baseline: `gradlew clean testDebugUnitTest lintDebug assembleDebug assembleRelease` passes.
98 JVM tests pass, lint reports 0 errors and 169 warnings, and the release APK is 13.6 MB.
No code was changed in this phase.

Severity: **Blocker** = must be fixed before the repo or APK goes public.
**Should fix** = needed for the "every device" goal. **Nice to have** = polish.

## Blocker

| # | Finding | Where | Confirmed? |
|---|---|---|---|
| B1 | **Package name is `com.example.<roll number>`.** `com.example` is treated as a throwaway by installers and update trackers, and the second part is the author's roll number. It's in 60 files and in every past commit. | `app/build.gradle:6,10`, every `package` line, manifest, FileProvider authority | Confirmed |
| B2 | **Release APK is signed with the debug key.** Anyone can sign a fake "update" with the public debug key, and each developer machine has a different one, so updates fail with "App not installed". | `app/build.gradle:24` (`signingConfig signingConfigs.debug` in `release`) | Confirmed |
| B3 | **Personal data in git history and on GitHub Releases.** The roll number is in the package name of every commit, in the screenshots (next to a placeholder name), and in the v1.0–v1.7 release APKs. Commit `aa14f54`'s message contains the author's name; the in-app "Made by" credit is the only place the name is meant to appear. No student number, email, private report text or keystore has ever been committed (`git log --all -p` scanned; `gitleaks`/`trufflehog` aren't installed). | history, `docs/screenshots/*`, Releases v1.0–v1.7 | Confirmed. Owner chose a fresh start: one clean root commit, old releases and tags deleted |
| B4 | **Backup has no rules.** `allowBackup="true"` with no `fullBackupContent` or `dataExtractionRules`, so the guest database, guest prefs and export cache can end up in cloud or device-transfer backup. | `AndroidManifest.xml:7` | Confirmed |
| B5 | **No "unofficial" disclaimer.** NMIMS/SAP wording and a hard-coded portal URL, but nothing says the app isn't affiliated with SVKM's NMIMS or SAP. | `SapPortal.java:24`, `activity_settings.xml`, README | Confirmed |
| B6 | **No licence or notices.** No `LICENSE`, and no `NOTICE` for PdfBox-Android (Apache-2.0) and its bundled resources. | repo root | Confirmed |

## Should fix

| # | Finding | Where | Confirmed? |
|---|---|---|---|
| S1 | **No shrinking.** `minifyEnabled false`, no `shrinkResources`. The APK carries all of PdfBox, Bouncy Castle and AndroidX: 2 dex files, 13.6 MB. | `app/build.gradle:21` | Confirmed |
| S2 | **Launcher icon is a single vector drawable.** No adaptive, monochrome (themed), round or legacy PNG mipmaps. | `drawable/ic_launcher.xml`, manifest `android:icon` | Confirmed |
| S3 | **Hard-coded strings.** Lint: 91 `HardcodedText` (layouts and menus) and 53 `SetTextI18n` (Java), plus about 60 more literal strings in dialogs and toasts. No `plurals`, so the app can't be translated or switched to RTL. | lint report; most activities | Confirmed (the brief's estimate of about 74 + 26 undercounts) |
| S4 | **Accessibility.** There are 0 `contentDescription`s. Status chips use colour plus text (good), but progress bars have no state description, there are no headings, and long-press actions have no visible or TalkBack-custom-action equivalent. | all layouts | Confirmed |
| S5 | **No adaptive layouts.** Only `layout/`, `values/` and `values-night/` exist. There are no `-w600dp`/`-sw600dp` resources, no window size classes and no two-pane mode, so wide windows show a stretched phone UI. | `res/` | Confirmed |
| S6 | **State loss on recreation.** Only the import flow (a `ViewModel`) and the new-leave form survive rotation or fold. Filter chips, scroll position, open dialogs and the leave-detail note aren't saved. | `SubjectDetailActivity`, `LeaveDetailActivity`, dialogs | Confirmed by code reading; to be verified on the device |
| S7 | **minSdk 24 can go lower.** Dependency minimums: AppCompat 1.7.1, Material 1.13.0, RecyclerView 1.4.0 and Activity 1.10.1 are 21; Core 1.13.0 is 19; PdfBox-Android 2.0.27.0 is 14. So **21** is the floor. Code above 21 that needs guards or desugaring: `java.util.function.Consumer` (Background, adapters), `TimePicker.setHour` (API 23), `Context.getColor` (API 23). | `app/build.gradle:11` | Confirmed (AAR manifests read from the Gradle cache) |
| S8 | **No predictive back, splash screen or per-app language.** `enableOnBackInvokedCallback` isn't set (one screen already uses `OnBackPressedDispatcher`), there's no splash screen API and no `locales_config`. | manifest | Confirmed |
| S9 | **Large-PDF safety.** `PDDocument.load(InputStream)` buffers in memory, with no size cap. Only `OutOfMemoryError` is caught. | `PdfTextExtractor.java` | Confirmed |
| S10 | **No crash log or StrictMode.** A crash leaves nothing a user can report. | — | Confirmed |
| S11 | **Exported `ImportActivity`.** It accepts `VIEW`/`SEND` for `application/pdf`. It rejects non-content `VIEW` through the intent filter, but `SEND`'s `EXTRA_STREAM` scheme isn't checked, multiple streams aren't handled, and there's no size cap. | `ImportActivity.java` | Confirmed |
| S12 | **Lint `ApplySharedPref` (5).** `commit()` is used deliberately before switching databases in guest mode; that needs a justification comment or `apply()` where safe. | `GuestMode.java`, `Prefs.java` | Confirmed (intentional in GuestMode) |
| S13 | **No CI, release automation or dependency verification.** There's also no Gradle wrapper validation. | `.github/` absent | Confirmed |
| S14 | **Hard-coded colours in one layout.** `#FFFFFF` on the guest banner. | `activity_main.xml:31,40` | Confirmed |

## Nice to have

| # | Finding | Where |
|---|---|---|
| N1 | `NotifyDataSetChanged` (5): use `DiffUtil` or ranged notifies. | adapters |
| N2 | `DefaultLocale` (2): `String.format` without a locale. | `AddLectureActivity.java:147` |
| N3 | `Overdraw` (2), `DisableBaselineAlignment` (2). | `item_lecture.xml`, `dialog_weekly_pattern.xml` |
| N4 | `AlwaysShowAction` (2) on the theme toggle and the add-lecture "+": intended, but should fall back to `ifRoom` on very narrow windows. | `menu/main.xml:9` |
| N5 | `GradleDependency` / `AndroidGradlePluginVersion` / `OldTargetApi`: newer versions exist. Versions stay pinned as they are and will only be raised deliberately. | `app/build.gradle` |
| N6 | `TrustAllX509TrustManager` (3) comes from Bouncy Castle inside PdfBox-Android (used only for encrypted PDFs). The app has no network permission, so it's unreachable; noted, not suppressed. | dependency jar |

## Personal-data inventory (rule 4)

| Item | Where | Action |
|---|---|---|
| Author name | `activity_settings.xml:247` (Settings credit) | **Keep.** This is the one allowed place |
| Author name | commit message `aa14f54` | Removed by the fresh-start history |
| Author's roll number | the old `com.example.*` package name (60 files), README, old screenshots, Releases v1.0–v1.7 | Package renamed in Phase 1; screenshots replaced; history and releases dropped in the fresh start |
| College or class detail ("MPSTME … B.Tech CE, Semester V") | `README.md` intro | Replaced with neutral wording in Phase 7 |
| Placeholder identities `DEMO STUDENT`, `TEST STUDENT A045`, `X000` | test fixtures and screenshots | Fine (fake), but screenshots are regenerated with a non-roll placeholder |
| Student number, email, phone, address | — | Never present |
| Private report text (`sap_report_*.txt`, `pdfbox_dump.txt`) | git-ignored local files only | Never committed. Kept out |

## Facts recorded for later phases

- **The APK is pure Java/Kotlin:** no `.so` files, so it runs on every ABI and needs no 16 KB alignment work.
- **PdfBox assets loaded by name** (they must survive shrinking): `assets/com/tom_roush/fontbox/resources/cmap` (92), `…/unicode`, `…/pdfbox/resources/afm` (14), `…/glyphlist` (3), `…/text`, `…/ttf`.
- **Exported components:** `MainActivity` (launcher) and `ImportActivity` (open-with). Every other component is `exported="false"`, and there are no services or receivers of our own.
- **Manifest locks:** no `screenOrientation`, aspect-ratio or `resizeableActivity` limits, and no `configChanges`.
- **Notifications:** none. The owner asked for none, so the brief's `POST_NOTIFICATIONS` items are skipped and the app stays with no runtime permissions.
- **Test environment:** drive C: (the Android SDK, images and Gradle caches) is full, with 1.8 GB free. The installed images are API 34 (google_apis) and API 37.1. Images for API 21–33, 35 and 36 can't be downloaded here, so those matrix rows are listed as not run. New emulators are stored on D:.
