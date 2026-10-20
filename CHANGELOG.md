# Changelog

All notable changes to AttendEase. Versions follow `MAJOR.MINOR.PATCH`; the Android
`versionCode` is `MAJOR × 10000 + MINOR × 100 + PATCH`.

## [1.0.0] - 2026-10-20

First public release.

### Upload and figures
- Reads the SAP student attendance report PDF, with a review screen before anything is saved.
- Per-subject attendance with theory and practical merged, and a semester absence budget ("you can miss N more").
- Re-uploading is safe: the same PDF changes nothing, a newer one updates what changed.
- Any student, programme or semester; each PDF is filed under its own semester.
- Subjects and lectures can be added, renamed and merged by hand.

### Medical leave
- Leave requests covering chosen lectures, with a range shown until the college decides.
- Approved, rejected or partial outcomes, also per lecture by long-press.

### Devices and accessibility
- Android 5.0 and newer: phones, tablets, foldables and Chromebooks.
- Two panes on wide windows. Keyboard shortcuts, right-click, and dropping a PDF on the dashboard.
- Large text, TalkBack labels and headings, right-to-left layouts, dark mode, optional wallpaper colours (Android 12+).
- All text in string resources, ready for translation.

### Privacy and safety
- No permissions at all: no internet, no notifications, no accounts, no analytics.
- Guest mode for a friend's PDF, kept apart and erased afterwards (or after six hours unused).
- Backup rules keep your attendance across phones but never guest data.
- Large or damaged PDFs are refused with a clear message.
- A local crash report the student can copy; nothing is ever sent.
- About screen with an unofficial disclaimer, privacy summary and licences; one-time welcome screen.

### Notes
- Builds before 1.0.0 used a temporary package name and don't update to 1.0.0: export a CSV, uninstall, then install 1.0.0.

[1.0.0]: https://github.com/fortunatechick/AttendEase/releases/tag/v1.0.0
