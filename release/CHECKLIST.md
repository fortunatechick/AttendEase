# Release checklist (owner only)

Everything that needs the owner's accounts, keys or decisions. Do the sections in order. The
code side of 1.0.0 is finished: tests, lint, device tests and the CI and release workflows are in place.

## 1. Before the repository goes public

- [x] **Rewrite the git history once more** (done on 20 October 2026, just before the v1.0.0 release). Earlier commits on `main` still contain:
  - real course rows from your report (division and batch codes such as `CE C` / `C2`, plus the timetable) in `AttendanceParserTest`;
  - the old README's per-subject results table.

  The current tree is clean. To remove the history, squash `main` into one new root commit and force-push, the same way as the earlier fresh start:
  ```bash
  git checkout --orphan clean main
  git commit -m "AttendEase 1.0.0"
  git branch -M clean main
  git push --force origin main
  ```
  Then close or delete any open Dependabot branches, which were made from the old history.
- [ ] **Ask GitHub to purge cached objects (recommended).** The repo went public on 20 October 2026 with old CI runs deleted, but the four closed Dependabot PRs still link to pre-squash commits (test fixtures with real division/batch codes and timetable). Ask GitHub Support to remove those PRs and unreachable objects. GitHub can keep unreachable commits from the old history (including the v1.0–v1.7 era with the old package name) for a while, and they stay reachable by commit hash. Contact GitHub Support ("remove sensitive data", <https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository>), or create a brand-new repository and push the clean history there.
- [x] **Turn on private vulnerability reporting:** *Settings → Code security → Private vulnerability reporting → Enable*. SECURITY.md relies on it.
- [x] **Make the repository public:** *Settings → General → Danger zone → Change visibility*.
- [x] **Turn on GitHub Pages** from the `/docs` folder on `main` (*Settings → Pages*). The app's *Read the privacy policy* button opens `https://fortunatechick.github.io/AttendEase/privacy.html`. Open it once to confirm.

## 2. The release signing key (once, then never again)

Follow [SIGNING.md](SIGNING.md):

- [x] **Release key created on 20 October 2026** in `D:\AttendEase-keys\`:
  - `attendease-release.jks`: `CN=AttendEase`, RSA 4096, valid 30 years.
  - Your local `keystore.properties` points at it, and a copy holding the password sits in the same folder.
  - Certificate SHA-256 `757dceee847f9ed0a3a5ea85e5ac594c49c65c23d6e958f2a5c24fc0466aaa79` (also in the README).
- [ ] **Back up the whole `D:\AttendEase-keys\` folder now.** Keep two offline copies stored apart, e.g. an encrypted USB drive plus a password manager. Test a restore with `keytool -list -keystore <copy>`. If the key is lost, no installed copy can ever be updated.
- [ ] Delete the old throwaway test key (`test-only-release.jks` in the Claude scratchpad temp folder). It signed nothing that was published.
- [ ] Add the four repository secrets (*Settings → Secrets and variables → Actions*): `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- [ ] Create an environment named `release` (*Settings → Environments*) and add yourself as a required reviewer, so a tag can't publish without your approval.

## 3. CI

- [ ] Check the latest **CI** run on `main` is green, including the device-test matrix (Android 5.0 to 15). Those device tests are the coverage for the versions that couldn't be run locally (see [TEST_MATRIX.md](TEST_MATRIX.md)).
- [ ] Review the Dependabot pull requests that bump the GitHub Actions. Merge them if CI passes. For stricter supply-chain safety, pin each action to a full commit SHA.

## 4. Cut 1.0.0

- [x] 1.0.0 was released on 20 October 2026, built and signed locally with the release key and uploaded by hand, because the CI secrets weren't set yet. For later versions, add the secrets (section 2) and use the tag workflow below.
- [ ] Tag and push:
  ```bash
  git tag -a v1.0.0 -m "AttendEase 1.0.0"
  git push origin v1.0.0
  ```
- [ ] The **Release** workflow tests, builds, verifies the signature and creates a **draft** release with the APK and its `.sha256`. Open the draft and download the APK. Then:
  - [ ] install it on a real phone;
  - [ ] upload a PDF;
  - [ ] check the dashboard;
  - [ ] check *Settings → About and privacy*.
- [x] Signing certificate SHA-256 added to the README.
- [ ] Publish the draft.

## Status of CI on the 1.0.0 commit

- **Passing:** unit tests, lint, the debug build and the permission guard, plus device tests on Android 5.0, 6.0, 8.0, 10, 13 and 15 (API 21/23/26/29/33/35).
- **Not run:** the Android 12/12L emulator (API 32) never boots on GitHub's runners, so that job is informational only.
- [ ] Install the APK once on a real Android 12 phone, if you can borrow one. Upload a PDF, open a subject and check the splash screen.

## 5. After release

- [ ] Re-read [DEVELOPER_VERIFICATION.md](DEVELOPER_VERIFICATION.md) and Google's FAQ. Decide whether to register as a developer before the global rollout, keeping in mind what personal details Google would show.
- [ ] For every later release:
  - [ ] raise `appVersionName` (the versionCode follows);
  - [ ] add a CHANGELOG entry;
  - [ ] add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`;
  - [ ] tag.
- [ ] **Never sign with a different key.** Existing users could not update.

## Decisions taken during the release work (for the record)

| Topic | Decision |
|---|---|
| Notifications | **None**, at the owner's request. The prompt's `POST_NOTIFICATIONS` items were skipped. |
| versionCode | `MAJOR×10000 + MINOR×100 + PATCH`, so 1.0.0 = 10000 (the prompt mentioned 100). It only has to increase, so 10000 is fine for a first release and leaves room for the scheme. |
| Licence | MIT, copyright "The AttendEase contributors", so no personal name appears. |
| Contact | GitHub Issues and private vulnerability reports only; no email anywhere. |
| Author name | Only in the app, at the bottom of Settings ("Made by …"). |
| Backup | Attendance is included in Android backup to the user's own account; guest data, the guest switch and crash reports are excluded. |
| Calculation code | `parser/`, `calc/`, `ImportPlanner`, `SubjectResolver` and `LeaveRules` are unchanged in behaviour. Their English text is rebuilt from resources in `Formats` and checked by `WordingInstrumentedTest`. |
