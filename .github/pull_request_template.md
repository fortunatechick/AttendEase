## What and why

<!-- One topic per pull request. Link the issue if there is one. -->

## Checklist

- [ ] `./gradlew testDebugUnitTest lintDebug assembleDebug` passes
- [ ] No new permissions; still offline and still no notifications
- [ ] Changes to `parser/`, `calc/`, `ImportPlanner`, `SubjectResolver` or `LeaveRules` were discussed in an issue first and come with tests
- [ ] Any schema change bumps `AttendEaseDb.VERSION` with an `onUpgrade` step and a test
- [ ] New user-facing text is in `strings.xml`
- [ ] Only placeholder data in tests, screenshots and docs: no real report, name or roll number
