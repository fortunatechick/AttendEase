# Contributing to AttendEase

Thanks for helping. AttendEase is a small, offline student app, and these rules keep it that way.

## Before you open an issue

- **Never attach a real attendance report**, a screenshot of one, or its text. It contains a name, roll number, programme and every lecture. To show a parsing problem, copy one or two rows and **replace the subject, batch and division with made-up values** (for example `Data Structures and AlgorithmsT1 BT IT A`).
- For crashes, use *Copy crash report* when the app offers it after restarting. Check it holds nothing personal, then paste it into the issue.
- Security problems: follow [SECURITY.md](SECURITY.md) instead of opening a public issue.

## Ground rules for changes

1. **Offline only.** No `INTERNET` permission, analytics, ads, crash SDKs or network libraries. Links open in the user's browser.
2. **No notifications.** The app never posts one and never asks for the permission.
3. **Don't change the maths without a discussion.** The classes in `parser/` and `calc/`, and `ImportPlanner`, `SubjectResolver` and `LeaveRules`, decide the numbers students rely on. A change there needs an issue first, plus tests showing old and new behaviour.
4. **User data survives upgrades.** A schema change bumps `AttendEaseDb.VERSION`, adds an `onUpgrade` step that keeps existing rows, and comes with a test.
5. **Translatable text.** All user-facing text goes in `res/values/strings.xml`, with `plurals` for counts.
6. **No new API calls without guards.** `minSdk` is 21. `./gradlew lintDebug` must report no `NewApi` errors, and suppressions aren't accepted.
7. **Placeholder data only** in tests, fixtures, screenshots and docs.

## Building and testing

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Device tests (an emulator or phone connected):

```bash
./gradlew connectedDebugAndroidTest
```

Code style: follow the surrounding code (Java, 4-space indent; see `.editorconfig`). Keep
comments short and about *why*.

## Pull requests

- One topic per pull request, with a short description of what and why.
- Tests for behaviour changes; screenshots (placeholder data) for UI changes.
- CI must pass: unit tests, lint, and a debug build.

By contributing you agree that your work is released under the [MIT License](LICENSE).
