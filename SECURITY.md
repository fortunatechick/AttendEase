# Security policy

## Supported versions

Only the latest release on the [Releases page](https://github.com/fortunatechick/AttendEase/releases/latest)
gets fixes.

## Reporting a vulnerability

Please **don't open a public issue** for a security problem. Use GitHub's private
vulnerability reporting instead:

1. Go to the repository's **Security** tab.
2. Choose **Report a vulnerability**.
3. Describe the problem, the affected version and the steps to reproduce it. Use placeholder data, never a real attendance report.

You should get an answer within two weeks. Fixes are released as a new version, and the
report is credited in the release notes unless you prefer otherwise.

## Scope

AttendEase has no server and no internet permission, so the realistic risks are on the phone:

- **Upload handling.** Other apps can send a PDF to AttendEase (*Open with* / *Share*); a crafted file could try to crash it or read something it shouldn't.
- **Data separation.** Guest mode must never expose the owner's data.
- **Release integrity.** An APK not signed with the project key, as published in the README.

Out of scope: problems that need a rooted phone or physical access to an unlocked phone,
and issues in the SAP portal itself.
