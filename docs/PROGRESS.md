# Android milestone progress

| Milestone | Status | Scope |
| --- | --- | --- |
| A0 | Complete; Xiaomi device gate pending | Native scaffold, theme/icons, stack, CI and setup |
| A1 | Merged, PR #3; green CI; device QA pending | Auth and connection |
| A2 | Merged, PR #4; green CI; device QA pending | Chat core and transport |
| A3 | Complete locally; PR/CI pending | Killed-app notifications |
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

## A2 — chat core and frozen seams (2026-10-07)

- Native paged history, live messages, optimistic sends, ack/echo deduplication by
  tempId, same-ID retry, monotonic read/delivered receipts, six allowed reactions,
  reply and search of loaded messages. Brand BubbleTokens, wallpaper-tinted love/
  samurai variants, solid tails, text time/ticks inline and card footer metadata.
- Room v1 schema is checked in. Cache/outbox keys include app-user and connection.
  Canonical reconciliation deletes the matching own pending row and upserts the
  server row transactionally. Pending sends and REST sync checkpoints survive
  process death; relaunch/reconnect replays them. Sends are never matched by text.
- Resync snapshots REST's watermark before flushing pending sends, then pages
  `after` until a short page. Incoming/ack timestamps cannot jump that checkpoint
  over a missing interval. Older pages use the oldest cached server timestamp.
- Because server tempId dedupe is only five minutes, an attempted but unconfirmed
  older send becomes `delivery unknown`; automatic replay stops. Explicit resend
  warns of duplicate risk. Never-attempted offline sends can still be replayed.
  No guessed history tempId, persistent backend dedupe or endpoint was introduced.
- Nickname and deliberate five-step leave (advance/cancel/mutual immediate end)
  use the documented REST routes. Server gates determine allowed actions. A socket
  `connection:ended` purges that connection's cache/outbox and refreshes boot routing;
  polling covers termination missed while offline.
- Google session refresh serialization now lives in AuthRepository too, so socket
  and REST callers share it. RealtimeSocket awaits fresh auth before each manual
  reconnect; a rejected token gets one refresh attempt before local invalidation.

### Frozen seams for Claude

- `data/realtime/RealtimeSocket.kt`: Hilt singleton, the only socket constructor;
  `state`, `events(name)`, `emitWithAck(name,payload,timeoutMs=10000)`, `emit(name,payload)`.
  `start/stop` belong to MessageService; call code shares subscribe/emit APIs.
- `data/model/ChatMessage.kt`: nullable server `id` while optimistic, independent
  `tempId`, server fields plus local `deliveryState`/`error`. Never use tempId as an
  alarm raise ID. These model fields allow A4's disabled-until-confirmed card.
- `ui/chat/cards/AlarmCard.kt` and `CallLogCard.kt`: requested composable signatures,
  simple placeholders only; Claude owns both files from this merge onward.
- `call/CallLauncher.kt`: `CallKind.Audio/Video`, `start(kind)`. Chat header calls
  the interface; `di/CallLauncherModule.kt` currently binds a "Calls coming soon"
  toast. Claude replaces the binding in A5.
- `/alarm` confirms and sends a raise through the shared send path. No ringing,
  ack/cancel behavior, WebRTC, audio routing or incoming-call handling implemented.

### Verification

`gradlew.bat assembleDebug lintDebug testDebugUnitTest` passed with 19 JVM tests
and zero lint issues; PR/CI URL appears in the milestone report. JVM checks cover
ack validation/error/duplicate, echo-before-ack, lost ack replay after service
restart, own-tempId scoping, multipage resync with a newer pending-send ack,
five-minute unknown-delivery safety, receipt monotonicity, reaction replacement,
ChatViewModel draft/reply/call seam, and connection-ended teardown. Room schema
queries/transactions are KSP-compiled; actual disk/process/OAuth/socket behavior
needs the owner device checks. No physical device is connected.

Shared files changed: MainActivity adds ChatViewModel; AppNavigation replaces the
A1 placeholder and coordinates connection teardown; app/build.gradle.kts exports
Room schemas. Manifest and libs.versions.toml unchanged. API snapshot still matches
web main. A1 merged PR: https://github.com/aarahman04/One-on-One-Android/pull/3;
CI: https://github.com/aarahman04/One-on-One-Android/actions/runs/37593099431.

### Xiaomi / HyperOS device checkpoint (owner; work continues to A3)

- [ ] Two accounts: live send/receive and multiline text; only one bubble per send.
- [ ] Sent/single tick, delivered/double tick, read/blue ticks; background/resume
      and return from Settings update read position correctly.
- [ ] Airplane mode: queue text, rotate/relaunch, restore network. Same pending
      send reconciles once. An attempted send unconfirmed past five minutes
      shows delivery unknown and requires the duplicate-risk resend confirmation.
- [ ] With more than 50 messages, load older pages. Search loaded text, reply to
      a server message, cancel reply, add/replace/remove each allowed reaction.
- [ ] Reconnect after 50+ missed messages: all missed pages load in time order;
      no duplication of sends acked during resync; cached history stays visible.
- [ ] Sign out/switch accounts: no previous account's conversation/draft shown.
- [ ] Set/rename nickname. Advance leave once; cooldown disallows another step;
      cancel your countdown. Both leaving enables immediate end; termination
      routes both clients away and clears that conversation's local cache/queue.
- [ ] Voice/video buttons show "Calls coming soon"; `/alarm` confirms a raise
      and renders the placeholder without sound. A4/A5 functionality is pending.
- [ ] Light/dark and existing love/samurai wallpapers: readable green/blue or
      tinted bubbles, correct sender alignment, footer ticks/time and tails.
- [ ] Large fonts/TalkBack, keyboard, scroll position after loading older messages,
      background/resume and rotation. Report failures for an immediate fix branch.

A2 contract gaps: none requiring a new endpoint. Known contract bounds: five-minute
in-memory dedupe and exclusive timestamp cursors. Older uncertain attempts require
manual confirmation; search is explicitly of loaded messages, matching the available
history API. Long-lived durable idempotency would require a backend extension.

## A3 — message push, permissions and OEM onboarding (2026-10-07)

- FirebaseMessagingService parses the authoritative data schema through PushRouter.
  `alarm`, `call`, `call_end` are delegated exactly to the frozen handler interfaces
  in `push/handlers`, bound to no-ops in `di/PushHandlersModule.kt`. No alarm/call
  service, ringing or incoming-call UI is implemented. Application.onCreate creates
  `messages`, `alarm`, `calls` channels before any registration/display work.
- WorkManager registers after authenticated login and token rotation with an explicit
  `platform: android-native` field (not an omitted serialization default). Rotation
  unregisters the previous token. Registration and sign-out/deletion share a mutex;
  the candidate is stored before HTTP so a lost response remains unregisterable.
  Registration completes before sign-out drops credentials. Constraint errors name
  migration 035 plainly; network/rate-limit failures back off and retry.
- Normal data-only messages schedule persisted notification work, expedited on API
  31+. The worker restores auth and checks server current-connection membership before
  displaying. It suppresses notifications when that chat is open and resumed, when
  permission is denied, or after an account/connection change. Message IDs are
  remembered for the newest 256 pushes to suppress common FCM replay across restarts.
- MessagingStyle notifications accumulate messages per conversation, use a private
  lock-screen presentation, and include tap, inline reply and mark-read actions.
  Explicit non-exported action receiver validates IDs and input; workers verify the
  current account and server connection before acting. Reply worker UUID is its
  persistent tempId; retry/process restart cannot create a new send ID. History keeps
  the acknowledged alias so completed reply work stays idempotent locally too.
- Token API/onNewToken intentionally use the contract's FCM registration tokens,
  rather than the newer Firebase Installation-ID registration protocol. Only these
  precise SDK deprecation diagnostics are suppressed; lint remains strict.
- The documented notification-block missed-call text fallback has no chat/action IDs;
  it receives a generic notice without fabricated inline actions. A4/A5 handlers
  remain no-ops. Deleted-message callbacks schedule conversation resync.
- Notification/OEM onboarding appears once after gates/first login and remains in
  Settings. It covers POST_NOTIFICATIONS, API 34+ full-screen access, and Xiaomi/
  Redmi/POCO HyperOS/MIUI, Oppo/Realme, Vivo/iQOO, OnePlus and Samsung background
  settings. Proprietary shortcuts are best-effort, with App info/battery-settings
  fallback on unresolved or restricted intents. No battery exemption is requested
  automatically. Permission state refreshes when returning from Settings.

### Verification

`gradlew.bat assembleDebug lintDebug testDebugUnitTest` passed with 26 JVM tests
and zero lint issues; final-revision PR/CI results are recorded in the report.
Checks cover exact handler delegation, payload/type/UUID
validation, live-chat suppression, account/conversation action scoping, explicit
platform serialization, rotation/sign-out serialization, lost-response candidate
retention, persisted reply tempId/history alias, and first-login onboarding once.
All A1/A2 tests remain included. Manifest, Hilt bindings, worker classes, permissions
and channel construction compile/lint; actual FCM delivery, OS notification actions,
OEM components, permission dialogs and killed-app behavior require physical-device
verification. No physical device is connected; no FCM/device claim is made.

Shared edits: AndroidManifest adds POST_NOTIFICATIONS/USE_FULL_SCREEN_INTENT,
non-exported FCM service/action receiver and default message channel metadata;
Application creates channels and starts registration; MainActivity injects the
registration controller; navigation adds onboarding/settings entry. Catalog and
frozen RealtimeSocket/AlarmCard/CallLogCard/CallLauncher files are unchanged.
A2 merged PR: https://github.com/aarahman04/One-on-One-Android/pull/4;
CI: https://github.com/aarahman04/One-on-One-Android/actions/runs/37596154289.

### Xiaomi / HyperOS device checkpoint (owner; work continues to A6)

- [ ] Install the configured local APK using the existing Firebase project. Sign
      in: first-login onboarding shows once and remains reachable from Settings.
- [ ] Grant/deny notifications, reopen system notification settings, grant later;
      summary updates on resume and denial never crashes notification posting.
- [ ] API 34+: full-screen-access link/summary works. This only configures access;
      actual alarm/call full-screen behavior belongs to Claude A4/A5.
- [ ] HyperOS: enable Background autostart and battery No restrictions. Proprietary
      links open an appropriate screen or safely fall back to App info; also check
      Oppo/Vivo/OnePlus/Samsung on an available device (unverified here).
- [ ] Backend token row uses android-native. If rejected by a constraint, confirm
      migration 035/backend logs. No live authenticated registration was exercised here.
- [ ] Two accounts: normal text/image/letter/etc push when backgrounded and swiped
      from recents, including reboot/relaunch. Do not use Force stop as the killed
      test; Force stop blocks FCM until manual reopen on every OEM.
- [ ] Open/resumed matching chat suppresses the notification; Settings/other screen
      and background show it. Conversation grouping accumulates sender/preview text.
- [ ] Tap notification restores auth and server boot route, without bypassing gates.
- [ ] Inline reply sends once, adds your reply to the notification; offline reply
      persists/retries with the same tempId. Old uncertain delivery needs chat review.
- [ ] Mark read flips the peer's read receipt and dismisses that message notification.
- [ ] Sign out/switch accounts or end connection: old notifications/actions cannot
      send/read in a new conversation. Sign-out unregisters the stored token.
- [ ] Token rotation/relaunch re-registers correctly; duplicate data pushes do not
      repeatedly alert within the remembered message-ID window.
- [ ] Alarm/call/call_end data delegates without normal-message notifications;
      ringing and incoming-call UI are intentionally absent until A4/A5.

A3 contract gaps: none. Migration 035 is stated applied by the phase-2 prompt; its
live constraint behavior is still device/backend-verified by the owner. APIs provide
no direct message fetch/search; normal notification uses the contract preview, and
membership/action safety uses current connection.
