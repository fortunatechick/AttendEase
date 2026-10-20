# Android developer verification

Google is introducing a requirement that apps installed on certified Android devices come from
**registered (verified) developers**. This applies even to APKs installed from outside the Play
Store. It affects how AttendEase, distributed as an APK on GitHub Releases, can be installed in
future. This note collects what is known as of **20 October 2026**. Re-check the official
pages before each release; the rules have changed more than once.

**Official sources:**
- Android Developers Blog, June 2026: <https://android-developers.googleblog.com/2026/06/android-developer-verification.html>
- Developer verification overview and FAQ: <https://developer.android.com/developer-verification>

## What is known

| Point | Status |
|---|---|
| Announced August 2025: only apps from verified developers installable on certified devices, sideloading included | Confirmed (Google announcement, widely reported) |
| First enforcement on 30 September 2026, in Brazil, Indonesia, Singapore and Thailand | Confirmed (June 2026 blog post) |
| That first wave covers installs through *participating stores*. Direct downloads (like GitHub Releases) aren't affected there until the global rollout | **Reported from Google's FAQ; not confirmed here.** Check the FAQ |
| Global rollout "in 2027 and beyond" | Confirmed as a plan, with no fixed date |
| India in the first wave | **Not listed.** Treat it as part of the 2027+ global rollout |
| Unregistered apps stay installable through an "advanced flow" and through `adb` | **Reported, not confirmed.** Check the FAQ |
| Full developer account: legal name, address, contact email, phone number, ID verification, one-time US$25 fee | Reported; check the current requirements |
| Free limited tier for students and hobbyists: no fee or government ID, but distribution capped at 20 devices | **Reported, not confirmed** |

## What it means for AttendEase

- **Today:** nothing changes for users in India installing from GitHub Releases.
- **Before the global rollout**, the owner has to decide one of these:
  1. **Register as a developer** and register the package name `io.github.fortunatechick.attendease` with the release signing key's certificate. Registration ties the app to the signing key, which is another reason that key must never change or be lost (see [SIGNING.md](SIGNING.md)).
  2. **Use the limited free tier**, if it fits (it caps the number of devices).
  3. **Stay unregistered.** Users then have to use whatever advanced install flow Google offers.

- **Privacy decision for the owner:** a full registration needs a legal name and contact details, which Google may show to people installing the app. The project's own rule is that the author's name appears only in the in-app credit. **Check what Google displays publicly before registering.** This is the owner's call.

## Owner actions

These are tracked in [CHECKLIST.md](CHECKLIST.md):

- Read the official FAQ again before each release and update this file.
- Decide on registration before the global rollout date is announced, and register early if you choose to, since verification can take time.
