# Android milestone progress

| Milestone | Status | Scope |
| --- | --- | --- |
| A0 | Complete; Xiaomi device gate pending | Native scaffold, theme/icons, stack, CI and setup |
| A1 | Complete locally; PR/CI pending | Auth and connection |
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

## A1 — auth and connection (2026-10-07)

- Credential Manager Google button flow, a fresh random raw nonce / SHA-256 Google
  nonce, and Supabase `signInWith(IDToken)` (the Kotlin SDK's ID-token sign-in API).
  Supabase owns persistent sessions and automatic refresh. Missing public build
  configuration produces a visible error; secret-free CI builds remain launchable.
- Shared Retrofit/OkHttp REST client adds Bearer auth. Concurrent 401s serialize
  refresh and reuse a newer token, retry once, then clear only the rejected session
  on another 401. Temporary refresh failures preserve session storage. Redirects
  are disabled on the authenticated client so credentials stay on the API origin.
- Server-driven boot routes, local age 18+/Terms gate, show/copy/regenerate code,
  request, waiting/cancel, incoming accept/decline, blocked list/unblock, settings,
  sign-out and typed account deletion confirmation. Active/leave_pending routes to
  an A1 conversation placeholder; actual chat is A2. No alarm/call implementation.
- Connection polling runs only while resumed, at 3-second intervals. A failed
  poll keeps the last successful view with an error; it never invents a connection.
  Sign-out unregisters the stored push token before dropping auth and preserves
  the session if unregister fails. Deletion waits for server success; cascades
  remove token rows, then local session/token state is cleared.
- Local ignored config now uses the existing deployed Railway origin from the
  read-only `.env.production` and the Google audience explicitly in the contract.
  No web files changed. The Android API snapshot matches the authoritative file.
- Shared-file changes: MainActivity delegates to AppNavigation/AppViewModel;
  libs.versions.toml adds coroutines-test for required ViewModel tests. Manifest
  and application remain unchanged in A1.
- Phase-2/user authorization supersedes the earlier stop/never-merge delivery
  wording: one PR per milestone, merge only after green CI, continue autonomously.

### Verification

`gradlew.bat assembleDebug lintDebug testDebugUnitTest` passed with zero lint
issues; final check results and PR/CI links are recorded in the milestone report.
Tests cover nonce hashing, code validation, birthday boundary, gate and pending/live
routing, failed polls retaining state, connection actions, sign-out unregister
ordering/failure, deletion ordering, one-refresh/retry, terminal 401 and concurrent
401 deduplication. No physical device is connected; no device test is claimed.

`gradlew.bat signingReport` passed. Debug SHA-1:
`ED:02:08:A3:38:18:A4:18:40:AC:EF:D9:BD:C2:B2:0C:ED:37:55:9D`.
Register it for `app.web.oneonone` in Firebase/Google Cloud before device sign-in.

### Xiaomi / HyperOS device checklist (owner)

- [ ] Install the locally configured debug APK; register its debug SHA-1 first.
- [ ] Sign in, dismiss the picker, sign out, choose another Google account.
- [ ] Fresh-device adult birthday proceeds; under-18 stops; Terms requires the
      checkbox and both legal links work. Rotate/relaunch after accepting.
- [ ] Copy/regenerate the 8-character ID. Old code stops accepting new requests.
- [ ] With two accounts: request/cancel, incoming decline, request/accept; both
      route to the conversation placeholder. Pending changes sync while resumed.
- [ ] Reopen a restored session and live connection; background/resume and rotate.
- [ ] Lose network during polling/actions: visible error, retry succeeds, no
      duplicate connection. Session refresh/expired-session routing needs device QA.
- [ ] Blocked list displays outbound blocks; unblock does not restore conversation.
- [ ] On a disposable test account, type delete and confirm account deletion;
      errors preserve the signed-in screen and successful deletion returns to login.
- [ ] Large fonts and TalkBack: scrollable screens, labelled fields/checkbox,
      Material buttons and no controls hidden by system bars.

## Contract gaps

A1: none. Google OAuth certificate registration is an external device setup step,
not an API gap. Alarm/call seams will be created in A2/A3 as instructed.
