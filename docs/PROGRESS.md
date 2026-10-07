# Android milestone progress

| Milestone | Status | Scope |
| --- | --- | --- |
| A0 | Complete; Xiaomi device gate pending | Native scaffold, theme/icons, stack, CI and setup |
| A1 | Not started | Auth and connection |
| A2 | Not started | Chat core and transport |
| A3 | Not started | Killed-app notifications |
| A4 | Not started | Emergency alarm |
| A5 | Not started | Earpiece-correct voice/video calls |
| A6 | Not started | Feature parity |
| A7 | Not started | Release hardening and Play checklist |

## A0 — 2026-10-07

- Initial scaffold goes on main, per the explicit empty-repo exception; no A0 PR.
- Single Activity native Compose welcome, Navigation and Hilt. Brand light/dark
  theme, full bubble palettes, existing launcher/adaptive/notification art plus
  monochrome launcher support. Cloud backup and device transfer are excluded.
- Gradle Kotlin DSL, version catalog and checksum-pinned wrapper. Entire requested
  stack wired; no business feature implementation before its milestone.
- app.web.oneonone, minSdk 26, stable compile/targetSdk 37, default versionCode 5.
- Secret-free debug CI; manual signed AAB/APK with pre/post SHA-1 and SHA-256
  checks. Existing signing identity and Firebase project retained.
- Local configuration copied from the reference files into ignored paths only.
  The API URL is localhost; GOOGLE_WEB_CLIENT_ID is empty because the source
  `.env` omits it. Resolve before A1. No API gap is needed for A0.

### Verification

Local `gradlew.bat --no-daemon assembleDebug lintDebug testDebugUnitTest` passed:
zero lint issues, one contrast test passed (all four palettes, both stops,
message text/time/ticks). The same checks passed in a clean staged-tree build
without either local credential file or VITE_* environment values, validating
the secret-free PR path. GitHub Actions runs these checks on the initial main
push; the actual run link/result is included in the milestone delivery report.

The initial main Actions run failed before Gradle because setup-android's
default package list included Google's removed `tools` package. A0 follow-up
branch `a0/fix-ci-sdk-setup` explicitly installs platform-tools, platform 37.0
and build-tools 36.0.0 in both jobs. This is an A0 CI fix only; its PR remains
unmerged for the owner to review. The initial scaffold commit stays on main.

`aapt2 dump badging` and the merged manifest confirmed app.web.oneonone,
versionCode 5 / 2.0.0-dev, minSdk 26 and target/compileSdk 37. `apksigner verify`
confirmed the debug signer; keytool matched this machine's documented debug
certificate. The generated Gradle wrapper JAR and distribution checksums match
Gradle's published checksums. Workflow YAML and Bash syntax passed validation;
staged secret/config scanning passed; the web repo remains untouched.
`gradlew.bat help -PappVersionCode=4` failed with the intended minimum-version
message, confirming the version guard. No API contract gap was needed for A0.

Build setup fixes: nullable AGP version fields, a transcribed checksum, Hilt
metadata compatibility (pinned Hilt 2.60.1), Windows SDK-property escaping,
backup rules and adaptive icon lint. Only dependency update-notice lint IDs are
excluded; all correctness warnings fail lint. Non-fatal toolchain SDK XML and
Gradle deprecation messages remain; no application lint baseline is used.

No physical device connected; no device verification claimed. Neither the
reference checkout nor this repo's Actions secrets contains the upload
credentials, so signed release CI cannot be exercised until the owner supplies
the same existing credentials plus all four VITE_* configuration secrets.

### Xiaomi / HyperOS device checklist (owner)

- [ ] Use a chosen test device/profile. A debug build has the same package but a
  different signer from the installed Play/Capacitor app; Android will reject
  an in-place update. Only uninstall the old app after deciding its local data
  can be removed. A0 has no sign-in/chat and is not a daily-use replacement.
- [ ] Install `app-debug.apk` or run `gradlew.bat installDebug`.
- [ ] Launcher shows the existing One on One mark and label.
- [ ] If supported, enable themed launcher icons and check the monochrome mark.
- [ ] Cold launch shows the native welcome screen without a crash or WebView.
- [ ] Toggle system dark/light modes: brand colors and readable text remain.
- [ ] Rotate, background/resume, swipe from recents/relaunch: welcome survives.
- [ ] Large font/display size and TalkBack: text is readable, safe from system
  bars, and announced once; the decorative logo is not announced redundantly.
- [ ] No microphone, camera, location or notification permission prompts in A0.
- [ ] Report results before authorizing A1. Auth/chat/push/alarm/call testing
  belongs to later milestones and is not expected to work in this build.
