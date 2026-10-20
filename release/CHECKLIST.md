# Release checklist (owner only)

Everything that needs the owner's accounts, keys or decisions. Do the sections in order. The
code side of 1.0.0 is finished: tests, lint, device tests and the CI and release workflows are in place.

## 1. Before the repository goes public

- [ ] **Rewrite the git history once more.** Earlier commits on `main` still contain:
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
- [ ] **Ask GitHub to purge cached objects (optional but recommended).** GitHub can keep unreachable commits from the old history (including the v1.0–v1.7 era with the old package name) for a while, and they stay reachable by commit hash. Contact GitHub Support ("remove sensitive data", <https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository>), or create a brand-new repository and push the clean history there.
- [ ] **Turn on private vulnerability reporting:** *Settings → Code security → Private vulnerability reporting → Enable*. SECURITY.md relies on it.
- [ ] **Make the repository public:** *Settings → General → Danger zone → Change visibility*.
- [ ] **Turn on GitHub Pages** from the `/docs` folder on `main` (*Settings → Pages*). The app's *Read the privacy policy* button opens `https://fortunatechick.github.io/AttendEase/privacy.html`. Open it once to confirm.

## 2. The release signing key (once, then never again)

Follow [SIGNING.md](SIGNING.md):

- [ ] Create `attendease-release.jks` **outside** the repository folder with the `keytool` command there, using a neutral `-dname` (no personal name).
- [ ] Back it up: two offline copies of the `.jks` and its password, stored apart. Test a restore with `keytool -list`.
- [ ] Point your local `keystore.properties` at the real key. It currently points at a **throwaway test key** in a temporary folder, which must never sign anything published. Delete that test key afterwards.
- [ ] Add the four repository secrets (*Settings → Secrets and variables → Actions*): `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.
- [ ] Create an environment named `release` (*Settings → Environments*) and add yourself as a required reviewer, so a tag can't publish without your approval.

## 3. CI

- [ ] Check the latest **CI** run on `main` is green, including the device-test matrix (Android 5.0 to 15). Those device tests are the coverage for the versions that couldn't be run locally (see [TEST_MATRIX.md](TEST_MATRIX.md)).
- [ ] Review the Dependabot pull requests that bump the GitHub Actions. Merge them if CI passes. For stricter supply-chain safety, pin each action to a full commit SHA.

## 4. Cut 1.0.0

- [ ] Make sure `appVersionName` in `app/build.gradle` is `1.0.0` and CHANGELOG.md has today's date instead of "unreleased".
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
- [ ] Copy the **signing certificate SHA-256** from the release notes into the README's *Verify the APK* section (replacing "published here with the first signed release") and commit it.
- [ ] Publish the draft.

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
