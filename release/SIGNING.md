# Release signing

Every AttendEase APK published on GitHub Releases must be signed with **the same private key**,
forever. Android installs an update only when it's signed by the key that signed the installed
app. Losing the key, or signing with a different one, means **no existing user can ever update
again**. They would have to uninstall, which deletes their data, and install the new APK as a
fresh app.

The key never goes into git. `keystore.properties`, `*.jks` and `*.keystore` are git-ignored.

## 1. Create the key (once)

Use the JDK's `keytool`. Android Studio bundles one at `<Android Studio>/jbr/bin/keytool`.

```bash
keytool -genkeypair -v \
  -keystore attendease-release.jks -storetype PKCS12 \
  -alias attendease -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=AttendEase"
```

- **Validity:** 10950 days is 30 years. Android requires the certificate to stay valid for the app's whole life.
- **Password:** use a long random one. PKCS12 uses the same password for the store and the key.
- **Name:** the `-dname` is visible to anyone who inspects the APK. Keep it neutral (no personal name).

Keep the `.jks` **outside** the repository folder (for example one level up), so it can't be
committed by accident.

## 2. Point the build at it

Copy `keystore.properties.example` to `keystore.properties` in the project root and fill it in:

```properties
storeFile=../attendease-release.jks
storePassword=...
keyAlias=attendease
keyPassword=...
```

`gradlew assembleRelease` now produces `app/build/outputs/apk/release/AttendEase-v<version>.apk`,
signed with v1 + v2 + v3 schemes. Without `keystore.properties` the release build stops with a
clear message instead of producing an unsigned APK. Debug builds, unit tests and lint don't need
the key.

## 3. Back it up (do this before the first release)

Keep **at least two offline copies** of both the `.jks` file and the password, stored separately
from each other:

- **Copies:** an encrypted USB drive, plus a password manager entry with the `.jks` attached (or an encrypted cloud vault).
- **No single place:** never only on one laptop.
- **Test the backup:** restore it to a temporary folder and run `keytool -list -keystore <copy>`.

## 4. Publish the certificate fingerprint

Users verify a downloaded APK against this fingerprint. Print it from a signed APK:

```bash
apksigner verify --verbose --print-certs AttendEase-v1.0.0.apk
```

`apksigner` is in `<Android SDK>/build-tools/<version>/`. Copy the line
`Signer #1 certificate SHA-256 digest: …` into the README's "Verify the APK" section. It stays the
same for every future release, because it's the key, not the APK.

## 5. CI signing (GitHub Actions)

The release workflow rebuilds `keystore.properties` from repository secrets. Never echo them.

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 attendease-release.jks` (macOS: `base64 -i attendease-release.jks`) |
| `RELEASE_KEYSTORE_PASSWORD` | the store password |
| `RELEASE_KEY_ALIAS` | `attendease` |
| `RELEASE_KEY_PASSWORD` | the key password |

## Notes

- **Signature schemes:** v1 (JAR) is what Android 5–6 verify, v2/v3 are used from Android 7 and 9. v3 makes a future key rotation possible (`apksigner rotate`). That's the only supported way to change keys without breaking updates, and it still needs the old key.
- **Instrumented tests on release:** `gradlew connectedReleaseAndroidTest -PtestBuildType=release` runs the instrumented tests against the minified, release-signed build. It adds `app/proguard-instrumented-test.pro`, which keeps only test-runner linkage. The PdfBox rules are as shipped.
