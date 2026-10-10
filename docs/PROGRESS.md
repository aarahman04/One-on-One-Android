# Android milestone progress

| Milestone | Status | Scope |
| --- | --- | --- |
| A0 | Complete; Xiaomi device gate pending | Native scaffold, theme/icons, stack, CI and setup |
| A1 | Merged, PR #3; green CI; device QA pending | Auth and connection |
| A2 | Merged, PR #4; green CI; device QA pending | Chat core and transport |
| A3 | Merged, PR #5; green CI; device QA pending | Killed-app notifications |
| A4 | Merged, PR #8; green CI; device QA pending | Emergency alarm |
| A5 | Merged, PR #9; green CI; device QA pending | Earpiece-correct voice/video calls |
| A6 | Merged, PR #6; green CI; device QA pending | Feature parity |
| A7 | Merged, PR #7; green CI; device QA pending | Release hardening and Play checklist |
| S1–S5 | Merged, PRs #10–#14; green CI; device QA pending | Restyle to match the web client (tokens, fonts, icons, chat, screens, cards, call overlay) |

## A0 â€” 2026-10-07

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

## A1 â€” auth and connection (2026-10-07)

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

## A2 â€” chat core and frozen seams (2026-10-07)

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

## A3 â€” message push, permissions and OEM onboarding (2026-10-07)

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

## A6 â€” feature parity (2026-10-07)

Branch: `a6/feature-parity`. A3 merged as [PR #5](https://github.com/aarahman04/One-on-One-Android/pull/5)
with [green CI](https://github.com/aarahman04/One-on-One-Android/actions/runs/37599656301).
The web repo was pulled read-only and its authoritative contract checked again.

- Structured composers/cards: letter write/preview, Dawn/Botanical colors and serif
  typography, per-device signature, HTML download; sealed ask and this-or-that
  replies are new messages with the original server id as replyTo. Original cards
  remain sealed. Revealed names resolve through the original message. Countdown
  uses native date/time pickers and a live timer; check-in uses the five moods.
- Location requires explicit confirmation before permission, accepts approximate
  access, requests one snapshot with a ten-second timeout, rounds coordinates to
  five decimals and never tracks in the background. Lazy cards use a bounded OSM
  tile, attribution and external Google Maps view/directions links. OSM sees the
  tile area/IP; its identifying User-Agent and cache headers are respected.
- Native photo picker and SAF file picker, raw-byte upload, MIME/size validation,
  shared signed-URL cache refreshed after 55 minutes. Static images re-encode
  pixels to strip EXIF/GPS and handle orientation; bounded decode resizes large
  photos to <=4 MP. Animated GIFs remain GIFs and use Coil's official decoder;
  GIFs above 4 MP are rejected to bound decoded memory. Upload limits remain
  10/16/25 MiB. No broad storage permission or API Bearer sent to storage/maps.
- Voice notes record AAC/M4A off-main, stop at one hour or 16 MiB, and retain
  completed clips in SavedStateHandle/private cache for retry and restoration.
  In-progress recordings discard on background/navigation and never use a mic
  foreground service. Native playback is asynchronous, one player at a time,
  releases on background, requests transient media audio focus, and avoids active
  communication audio mode. Call routing remains Claude's responsibility.
- Media view/open/save uses private cache, narrowly scoped FileProvider read grants
  and SAF destinations. Shared downloaded cache files older than one day are
  removed on subsequent downloads. Completed clips are scoped to app user and
  connection. Upload/location completions cannot enqueue into a changed session.
- Appearance stores dark/light on-device (web default dark), uses the exact A0
  bubble palettes and copied web love/samurai wallpaper assets, and PATCHes shared
  wallpaper. The web renders legacy `line` as bubbles too; no invented style UI.
- Slash menu includes all seven commands; alarm only invokes the frozen A2 raise
  confirmation. Alarm/call cards, RealtimeSocket and push handler seams untouched.
- Reply snippets and search include media labels/file names; quoted loaded messages
  scroll to their source, unloaded targets explain paging/search; text links open
  natively and long-press supports copy/report plus existing reactions/reply.
- Person/message reports use closed categories and <=1000-character reasons.
  Blocking warns that ending deletes history/media for both and offers export from
  the chat menu beforehand. Nickname and server-gated five-step leave remain intact.
- TXT/JSON/escaped HTML exports page all available server history, preserving types
  and payloads; media exports contain metadata, so download media separately before
  ending. Export currently holds the conversation in memory (documented ceiling).
  Server membership remains authoritative. Durable outbox enqueue shares the
  lifecycle mutex so connection teardown cannot race a late enqueue.

Shared-file changes: MainActivity adds FeatureViewModel and observes stored theme;
AppNavigation passes it and scopes chat composition by user/connection; manifest
adds optional mic/location permissions/features and a non-exported, cache-only
FileProvider; catalog adds pinned Coil GIF and HTTP cache-control extensions;
Application configures a separate unauthenticated Coil client. No A4/A5 behavior.

### Xiaomi / HyperOS A6 checklist (owner)

- [ ] Two accounts/native + web: every slash feature, letter preview and download,
      cached signature, both letter appearances. Rotate/recreate with a draft open.
- [ ] Ask/this-or-that: original remains sealed; recipient reply reveals both,
      names are correct on each side, reply target is the original server id.
- [ ] Countdown past/future and local timezone; all five moods and length limits.
- [ ] Confirm/cancel location before permission; coarse/fine denial, GPS disabled,
      timeout, one snapshot, OSM attribution and View/Directions. No background updates.
- [ ] Photo picker JPEG/PNG/WebP/GIF, EXIF orientation/privacy, oversized/unsupported
      files, offline/upload failures, retry; signed URLs refresh after expiry.
- [ ] Record >1s, stop/send/play/stop, mic denial, background/discard, rotate,
      restored completed clip, interrupted playback and actual speaker loudness.
- [ ] Allowed file types open in external apps with read-only grants; save photos,
      voice and files through SAF. No installed viewer produces a safe notice.
- [ ] Themes persist across relaunch; wallpaper is shared and refreshes on peer;
      green/blue tinted bubbles, solid tails and timestamp/ticks match reference.
- [ ] React/toggle, reply to text/media, quote jump, older-target explanation,
      search/copy/links, large font and TalkBack. Alarm/calls still placeholders.
- [ ] Export >50 messages in all formats; inspect escaped HTML and media metadata.
      Download attachments before ending; nickname/leave cancellation/server timing.
- [ ] Report person/message/category/details; block confirmation/cancel, immediate
      end, both clients lose history/media, old cache/outbox purged, unblock does
      not restore history. Use a disposable connection for destructive checks.

A6 contract gaps: none. Physical phone/live authenticated backend tests remain
pending. Emulator launch smoke and final local/CI results will be recorded below.

Validation also fixed inherited launcher asset packaging: unused pre-26 launcher
bitmaps are removed, while Compose loads the raster foreground (adaptive XML is not
supported by painterResource). No branding or app identity changed. All 31 JVM
tests pass; final lint/build and emulator smoke are being checked before PR.


Final A6 local validation: `gradlew.bat clean assembleDebug lintDebug testDebugUnitTest`
passed (32 tests, zero failures; lint reports no issues). `git diff --check` passed.
Debug APK installed on the existing API 36 emulator, MainActivity rendered sign-in,
its process remained alive and AndroidRuntime showed no crash. Host resource pressure
caused a System UI ANR, so this is limited launch evidence, not functional feature QA.
No real phone, live Google login, upload, location, or two-account workflow tested.

## A7 — release preparation (2026-10-07)

Branch: `a7/release-prep`. A6 merged as [PR #6](https://github.com/aarahman04/One-on-One-Android/pull/6)
with [green CI](https://github.com/aarahman04/One-on-One-Android/actions/runs/37606564240).
Both main repos were pulled first; the contract snapshot still matches its authority.

- Release enables R8 optimization/resource shrinking with explicit Socket.IO,
  WebRTC JNI, Supabase/Ktor, serialization and Hilt boundary rules plus the SDK
  consumer rules. Ktor's optional IntelliJ debugger probes desktop JMX in a
  Throwable-guarded block; only those exact two missing JMX types get exclusions,
  verified against 3.6.0 bytecode. No blanket warning suppression.
- Every PR now also builds unsigned minified APK/AAB, checks release lint, verifies
  64-bit native LOAD/RELRO geometry and APK ZIP alignment, and saves R8 mapping in
  a clearly unsigned smoke artifact. Signed manual CI retains all certificate,
  Firebase project, identity and version assertions and adds native checks/mapping.
- A cross-platform stdlib Python check validates both 64-bit ABIs without installing
  another NDK in CI. Its self-test covers safe whole-segment RELRO page rounding,
  unsafe protected writable bytes and insufficient LOAD alignment. Actual release
  APK/AAB each pass six native libraries; SDK zipalign -P16 passes. No dependency
  upgrade was needed: graphics-path's non-aligned RELRO endpoint covers its entire
  LOAD region, so rounding reaches no other writable bytes (verified against the
  pinned binary and Android linker; runtime 16 KB test still required).
- docs/RELEASE-CHECKLIST.md contains a source-grounded Data Safety draft, native and
  SDK/provider collection/sharing review, deletion/retained-report behavior, all
  owner Play/Firebase/OAuth/secrets/version/internal-testing steps and full Xiaomi
  final QA. Current official personal-account testing guidance is 12 testers/14
  continuous days where applicable, correcting the stale read-only web note of 20.
- Version defaults remain 5 / 2.0.0-dev. No Play upload, workflow dispatch or key
  replacement. No custom baseline profile captured without final authenticated
  device flows; dependency ART profiles remain packaged.

Local checks: `gradlew.bat assembleDebug lintDebug testDebugUnitTest assembleRelease
bundleRelease lintRelease` passed; 32 JVM tests pass, debug/release lint report no
issues, minified APK/AAB and mapping are generated. Native checker self-test,
actual APK/AAB geometry and SDK zipalign passed. aapt2 verifies package
app.web.oneonone, versionCode 5 and target 37. No API-contract gaps.

Original artifacts are unsigned (upload key unavailable); a separate minified APK
copy uses the already-existing debug key for emulator smoke only. Android repo
Actions secrets are absent, so signed release CI cannot run until the owner
restores the existing values. Expected certificate checks are unchanged.

Shared changes: app Gradle release configuration, CI steps/artifacts, README and
MainActivity's system-bar icon contrast follows the stored app theme. No manifest,
nav, catalog, frozen socket/cards/handlers or A4/A5 behavior
changed in A7. Full phone checklist is in RELEASE-CHECKLIST.md; all A2/A3/A6
checkpoint items remain pending on the owner's HyperOS phone. A4/A5 integration,
production-configured signed-device QA, 16 KB runtime, old-app upgrade tests and
manual Console review are release gates, not claimed completed here.

Final minified launch smoke: a separate existing-debug-key-signed copy of the
release APK installed and cold-launched on API 36 (4 KB pages, 3442 ms). Sign-in
rendered, system-bar icons were legible against the stored dark theme, the app
process remained alive, and its AndroidRuntime log contained no crash. The
emulator showed a System UI ANR and unrelated com.android.phone crash; after
dismissing the system dialog, sign-in remained visible. This is limited launch
verification, not a physical-phone, authenticated SDK or 16 KB runtime test.

## A4 — emergency alarm (2026-10-07, Claude)

Branch `a4/alarm`. Owner: Claude (per `docs/prompts/gpt-sol-6.1-phase2.md`).

- `alarm/AlarmPolicy` (pure, unit-tested): the card state and "should ring" rules, derived only from server-backed messages. The card is disabled ("sending…") until the raise has its server id, so an ack/cancel can never carry a temp id (the web re-pop bug). A ring stops only on a SERVER-CONFIRMED ack/cancel (a message with an id), or when the user silences it or it auto-clears.
- `alarm/AlarmService`: foreground service (`mediaPlayback`) playing a looping USAGE_ALARM `res/raw/alarm.wav` + vibration, ongoing notification with a full-screen intent, Silence action, auto-clear at raise + 2 min. Every stop path removes the notification with the service. Per-ring random token on its PendingIntents; MainActivity (exported) acts on alarm extras only with a matching token and strips them so a recreate can't replay. Alarm PendingIntents use their own action + request codes so they don't collide with the message notification's MainActivity intent.
- `alarm/AlarmCoordinator`: binds the frozen `AlarmPushHandler` seam. Inputs: FCM data (killed/backgrounded) and the live chat's messages (socket + history), deduped by alarmId. If the FGS start is refused, a fallback notification on `alarm_fallback` (own alarm sound) is posted.
- `alarm/HandledAlarms`: acked/cancelled/silenced/auto-cleared ids (last 50) in SharedPreferences. A handled id never rings again on resync, relaunch or a late FCM.
- Channels: GPT's `alarm` channel (with a sound, which would double the player) is deleted; new silent `alarm_ring` plus `alarm_fallback`. MainActivity is now `singleTop`.
- Chat: the `/alarm` raise is sent with `replyTo = null` (it was the current reply target).

Verified: `assembleDebug lintDebug testDebugUnitTest` pass locally; 10 AlarmPolicy tests. NOT device-verified.

### Device checklist (two phones, owner)
- [ ] Phone B app killed (swiped away), Autostart on: A sends /alarm → B rings at alarm volume with a full-screen alert over the lock screen.
- [ ] B taps the notification → ring stops, chat opens, the card says "tap to acknowledge"; tap → "acknowledging…" → "acknowledged" on both phones.
- [ ] A sends /alarm, then A taps "tap to cancel" right away → B's ring stops; both cards show "cancelled". Reopen both apps: nothing rings again, cards stay "cancelled".
- [ ] Silence from the notification action: ring stops and does not restart after opening the app.
- [ ] No ack: B's ring stops by itself after 2 minutes; the card shows "expired".
- [ ] Do Not Disturb on: note whether it rings (depends on the phone's alarm-stream DND settings).

## A5 — voice + video calls (2026-10-07, Claude)

Branch `a5/calls`. Owner: Claude.

- `call/CallManager`: the single call state machine (Idle → NeedsPermission → Outgoing/Incoming → Active → Ended). Binds the frozen `CallLauncher` and `CallPushHandler` seams. Signaling over the shared `RealtimeSocket` (`call/CallSignaling`, events per docs/API-CONTRACT.md). The server owns busy, ring timeout and missed rows. Signals that arrive before our accept ack (the caller offers on `call:accepted`) are queued.
- `call/WebRtcSession` (stream-webrtc-android): same protocol as the web `CallSession`. Caller offers after accept, callee answers, trickle ICE as `{candidate}`, offerer restarts ICE on a drop, 20 s reconnect grace. Local tracks are attached before any offer can arrive, so the answer always carries our audio. Candidates are held until the remote SDP is set.
- `call/AudioRouter` (fixes the WebView problems):
  - MODE_IN_COMMUNICATION for the whole call (full voice-call volume, hardware AEC/NS).
  - Voice → earpiece, video → speaker; a headset (Bluetooth or wired) always wins; Speaker toggle. Uses `setCommunicationDevice` on API 31+, legacy speakerphone/SCO below.
  - Proximity wake lock while on the earpiece; everything restored at the end.
  - Routing choice is pure `RoutePolicy` (tested).
- `call/CallService`: foreground service `phoneCall|microphone|camera`, with a silent "ongoing call" notification and Hang up.
- `call/CallNotifications`: incoming full-screen CallStyle notification (insistent ringtone, Answer/Decline), dismissed by `call_end`, `call:ended`, or `call:accepted` from another device. Answer/full-screen intents carry a per-ring token checked in MainActivity (exported). Decline goes through a non-exported receiver that connects just long enough to send `call:decline`.
- `ui/call/CallOverlay`: full-screen call UI over the whole app. Remote + local video, mute, speaker, camera on/off, flip, end. Requests mic/camera at call time.
- `CallLogCard`: server-authored call rows (`{kind, outcome, durationSec}`) render like the web card.
- Manifest: CAMERA, MANAGE_OWN_CALLS, FOREGROUND_SERVICE_PHONE_CALL/MICROPHONE/CAMERA, MODIFY_AUDIO_SETTINGS; new channel `calls_ongoing` (silent).
- Test dep: `org.json` for unit tests only (android.jar ships stubs).

Verified: `assembleDebug lintDebug testDebugUnitTest` and minified `assembleRelease` pass locally. 11 new unit tests (wire format against web shapes, ICE server parsing, error acks, log text, routing). NOT device-verified, and NOT yet tested against a web caller.

Known limits:
- If the app is opened from Answer, the chat screen's MessageService restarts the socket on activate. Answering waits for the replayed `call:incoming`, with an 8 s fallback that connects the socket itself.
- `call:signal` is fire-and-forget, so signals sent while the socket is reconnecting are dropped. ICE restart covers that.

### Device checklist (Xiaomi + a second phone or the web app)
- [ ] Voice call native → native: audio is in the EARPIECE; screen turns off at the ear; Speaker toggles loud/quiet; volume keys change call volume.
- [ ] Plug in / connect earphones mid-call: audio moves to them; unplug: back to the earpiece.
- [ ] Video call: speaker by default, remote volume comparable to WhatsApp, both videos show, flip and camera off work.
- [ ] Native ↔ web (both directions): connects, two-way audio.
- [ ] Callee app killed (swiped, Autostart on): full-screen ring over the lock screen; Answer connects; Decline stops the caller's ringing within a few seconds.
- [ ] Caller cancels while ringing: the callee's ring stops; the chat shows the call log row.
- [ ] Lock the screen mid-call: the call continues (ongoing notification present).


## S1 — style foundation (2026-10-10)

Branch `style/s1-foundation`. Presentation only; no behaviour, ViewModel, route or network changes.

Shipped:
- `docs/design/WEB-STYLE-SPEC.md`: token + component spec extracted from the web `global.css` (with line refs) for S2-S4.
- Fonts (`res/font`, generated from the web woff2s by `scripts/make_fonts.py`, OFL, license in `docs/design/fonts-LICENSE.txt`): Fraunces (web variable font pinned wght 600 / opsz 28), Figtree 400/500/600/700, JetBrains Mono 400/500/700 (static TTFs). Fraunces 500 not bundled (no static upstream, unused by the web CSS).
- `ui/theme`: `Tokens.kt` (`OneColors` Dark/Light, spacing, radii, sizes, motion, elevation, `OneTheme` accessor), `Type.kt` (`FontFamilies`, `OneTypography`, `OneTextStyles`), `Theme.kt` rebuilt from the tokens (same `OneOnOneTheme(darkTheme)` signature, shapes 4/6/10/16). `BubbleTokens` re-diffed against web L1416-1600: no drift.
- Icons: 20 vector drawables `ic_*` (calls, composer, menus, receipt ticks); `res/raw/keep.xml` keeps them from UnusedResources until S2-S4 reference them (delete the entries as they get used).
- `ui/components`: `PrimaryButton`, `SecondaryButton`, `DangerButton`, `TextLink`, `OneTextField`, `OneIconButton`, `Eyebrow`, `ScreenTitle`, `Subtitle`, `OneModal`, `OneMenu`/`OneMenuItem`, `Modifier.pressScale`, with dark and light `@Preview`s.
- `MainActivity`: window background follows the resolved theme (no dark flash in light mode). New string `close`.
- Tests: `TokensTest` (web hex values), `BubbleTokensTest` unchanged and green.

Verification: `./gradlew assembleDebug lintDebug testDebugUnitTest` green. Not device-verified; no screenshots (previews only).

Device checklist:
- [ ] Every existing screen still works; text is now Figtree/Fraunces, colours match the web (dark: #0d1117 ground, #e6edf3 text).
- [ ] Appearance toggle to Light: no dark flash at the window edges (overscroll, keyboard open/close, rotation).
- [ ] Fonts render (not the system sans) on the Xiaomi.


## S2 — chat restyle (2026-10-10)

Branch `style/s2-chat`. Presentation only: every existing action, callback, dialog trigger and ViewModel call in `ChatScreen` is preserved (see the PR for the before/after call list).

Shipped (all under `ui/chat/`):
- `ChatScreen.kt`: now only wiring/state; layout moved to the files below. Chat column capped at 720dp and centred, log padding 6/8, date-separator pill, "Messages are encrypted" note, outlined "Load older", wallpaper overlay (love .22 / samurai .30, `BubbleTokens.*WallpaperOverlay`).
- `ChatHeader.kt`: 56dp header (bg-raised, 40dp avatar, title + "● status"), video / phone / more `OneIconButton`s; More menu regrouped like the web (Search, Appearance, Settings, Nickname, Export, divider, collapsible "Connection & account" with Report / Block / Leave in danger); search bar and leave banner styled per the web.
- `MessageBubble.kt`: 8dp-radius gradient bubble with 1dp edge, 6dp tail on group start, quote block, meta (time + tick vector) at the end of the last line via a custom `Layout` (`TextWithMetaPolicy`, uses `onTextLayout`; falls to its own row when it does not fit), "· not sent", reaction emoji under the bubble, long-press `OneMenu` with the reaction row + Reply / Copy / Report, swipe-right-to-reply (60dp trigger, 80 max), live-arrival fade + 8dp rise.
- `BubbleGrouping.kt`: pure `isGroupStart` (same sender, 0..60s, same local day) with unit tests.
- `Composer.kt`: reply bar, pill composer (paperclip, growing field up to 120dp, focus outline), 48dp send circle when there is text / mic when empty, `/` drop-up `SlashMenu` (replaces the old "Commands" dropdown; same command list and handlers; `slashMatches` unit-tested).
- `ChatPreviews.kt`: `@Preview`s for bubbles (dark, light, love, samurai: grouped run, quote, reactions, pending, failed), header, composer (empty, text + reply bar), slash menu.
- Cards S4 will restyle read the bubble colours from `LocalBubbleColors` (and `LocalContentColor` = bubble text colour).

Deviations: the old "Attach" / "Commands" text buttons are gone (paperclip, "/" menu); the mic opens the attachment panel where "Voice note" lives (no standalone recorder action exists in ChatScreen); no "About" menu group (no matching actions on Android); closing the search bar also clears the filter (web `search-close`); menu pop animation is the stock `DropdownMenu` one (`OneMenu` is S1's); header/composer apply status/navigation-bar insets but `AppNavigation`'s `safeDrawingPadding()` (S3) currently consumes them, so the header background does not extend under the status bar until that is removed.

Verification: `./gradlew assembleDebug lintDebug testDebugUnitTest` green. Not device-verified; previews stand in for screenshots.

Device checklist:
- [ ] Dark, light, love and samurai: bubbles/tails/meta colours match the web; wallpaper overlay readable.
- [ ] Grouped run: only the first bubble has a tail and an 8dp gap, followers 2dp; a message after 61s, another sender, or a new day starts a group.
- [ ] Meta sits on the last line of short and long messages (and wraps to its own row when the last line is full); ticks: clock-ish single at 70% while pending, single sent, double delivered, blue-ish double when read; failed shows "· not sent" with Retry.
- [ ] Reply: long-press -> Reply, swipe right on a bubble, reply bar above the composer, close X; tapping a quote scrolls to the original.
- [ ] Long-press reaction row (heart, thumbs-up, laugh, wow, sad, pray) adds/removes; tapping an emoji under a bubble toggles it; Copy / Report still work.
- [ ] Typing "/" shows the command menu above the composer; picking each command opens its dialog (alarm asks to confirm); text with a normal message still sends.
- [ ] Paperclip toggles attach controls; mic opens them with Voice note; send circle appears only with text.
- [ ] More menu: every entry opens what it did before; "Connection & account" expands; header video/phone start calls (disabled while recording).
- [ ] New incoming/outgoing message animates in; opening the chat does not animate history.

## S3 — pre-chat screens restyle (2026-10-10)

Branch `style/s3-screens`. Presentation only; reuses the merged S1 foundation.

- Restyled loading, sign-in, age, under-age, consent, connection ID/connect, waiting, request, Settings and Blocks with the web screen vocabulary: centered scrolling content, responsive 16/24dp padding, 20dp gaps, Fraunces titles, Figtree subtitles and shared button/field/link primitives.
- Connection ID uses the raised bordered panel, JetBrains Mono tracking, 24sp below 480dp and 32sp otherwise. Screen entry fades and rises 8dp over 280ms with the standard easing, keyed to the route rather than recomposition.
- Delete-account confirmation uses `OneModal`, `OneTextField` and `DangerButton`; confirmation, busy guards and dismissal behavior are preserved. Notification onboarding uses the rationale panel's raised surface, bold title and 14sp body while retaining every Android permission/settings action.
- Dark/light `@Preview` variants cover every restyled screen and the delete dialog, plus empty Blocks, notification permission states, busy/error and tablet Connect. Preview data and callbacks do not involve ViewModels or network calls.
- All 54 original AppNavigation and 46 original notification string literals remain. ViewModel calls, navigation, clipboard, date selection, consent, delete confirmation and notification/OEM callbacks were traced before/after; the full inventory is in the PR description. No theme/component, dependency, chat, call, alarm or web files changed. No architecture change.
- Opus round 1 fixes: rebased onto the merged S2, preserving both progress sections; kept exactly the requested sign-in/consent/connect/waiting/request eyebrows and removed the age/under-age ones; constrained scrolling content to at least the viewport height for vertical centering; moved safe-drawing padding from `NavHost` to `ScreenFrame` after its background so chat receives its own insets and the pre-chat background fills the window.

Verification after the S2 rebase and Opus round 1 fixes: `./gradlew assembleDebug lintDebug testDebugUnitTest` passed locally in 27s (69 tests, no failures/errors; lint warnings treated as errors). Source preservation self-check passed, including identical notification lifecycle and OEM settings helpers. Physical Xiaomi behavior has not been tested.

Foundation TODOs (outside S3 ownership): `TextLink` fixes font size at 13sp, so 12sp legal links need a style parameter; `OneTextField` has no visual transformation, so uppercase-only presentation of the entered connection ID needs that parameter without changing the raw value submitted to `request`.

Device checklist (Xiaomi/HyperOS, owner):
- [ ] In-app Appearance: choose dark and light, then visit every pre-chat screen and Settings/Blocks; confirm backgrounds, fonts and borders match the web reference, independently of system theme. Repeat with love/samurai selected (pre-chat screens keep the normal theme).
- [ ] At phone and tablet/split-screen widths, verify the 480dp breakpoint, centered layout, connection ID sizing, safe-area spacing and wrapping; increase font size and use TalkBack. Scroll short screens/notification onboarding to every action; open the keyboard without losing fields or controls.
- [ ] Route changes animate once; polling, busy/error changes and typing do not restart entry. Disable system animations and check reduced motion.
- [ ] Sign in with Google; open both legal links. Pick/change a birth date, Continue, verify the under-age Sign out path and the consent checkbox/Agree and continue path.
- [ ] Copy and regenerate the connection ID; enter up to eight characters and Connect. From a second account, exercise waiting/cancel and request/accept/decline; verify Settings and Back throughout.
- [ ] Settings: notifications/background settings, Blocks, Sign out and Back remain reachable. Verify populated/empty Blocks and Unblock.
- [ ] Delete account: open/cancel, close/back, invalid and case/whitespace confirmation; ensure deletion and dismissal remain blocked while busy. Use a disposable test account only for actual deletion.
- [ ] Notifications: allow/deny permission, return from notification/full-screen settings, Autostart/background and Battery shortcuts/fallback, Retry registration and Continue. Confirm status text refreshes on resume.

## S4 — cards, dialogs, call overlay restyle (2026-10-10)

Branch `style/s4-cards-call`. Presentation only; cards reuse S2's `LocalBubbleColors`, and dialogs/buttons/fields reuse the S1 foundation. No ViewModel, service, navigation, dependency or web changes.

- Restyled letter, countdown, check-in, ask, this-or-that, location, image, voice, file, alarm and call-log cards: 13/600 headings, 16dp glyphs, 15sp body, 13sp hints, bubble-aware text/actions. Alarm uses a live 4dp danger stripe; call logs use 36dp discs and four composite phone/video + arrow vectors ported from the web.
- Dawn and Botanical letter sheets use the web gradient, colors, serif type, 40/36dp padding, radius and botanical inset. Letter colors are isolated in `LetterThemes`.
- Replaced feature AlertDialogs and FilterChips with `OneModal`, shared fields/buttons, counters and radius-4 choices. Extracted presentation-only helpers so previews render without ViewModels, network, permissions, recordings or calls.
- Call overlay uses resolved theme tokens, vector controls, responsive avatar/local preview sizing, a raised controls panel and safe-area scrolling. Entry fades/rises over 320ms; ringing/reconnecting pulses last 2s/1.1s. System animator scale is observed: disabling animations shows a static ring, and re-enabling restores the pulse.
- Previews cover every card in Dark/Light/Love/Samurai with both sender directions, every dialog in dark/light, and ringing/connected/reconnecting/video calls (including landscape video). Image/map/video previews use inert media placeholders.
- Existing payload construction, limits, busy guards, date/time pickers, signed-URL refresh/error paths, URI permissions, document launchers, recorder lifecycle, alarm policy/ack/cancel, call permission requests and all call controls were traced before/after. The PR contains the full callback inventory. No architecture or public interface change.

Verification: `./gradlew assembleDebug lintDebug testDebugUnitTest` passed locally in 42s (69 tests, zero failures/errors; lint warnings treated as errors). Source preservation and drawable XML checks passed. Preview UI was rendered on a headless API 36 emulator (60 card variants and 42 dialog/call variants); with animator scale 0, two ringing captures were pixel-identical, and changing scale to 1 while the preview remained open resumed animation. These are presentation checks with inert callbacks, not live call/alarm or physical Xiaomi verification.

Ownership TODOs: S2's bubble padding/meta slot prevents full-bleed image + overlaid white metadata; voice state lacks playback-position progress; call logs have no existing call-back handler despite the unused `onSend` parameter; shared `OneModal` controls panel centering/height. These need work outside S4's owned files and are described in the PR.

Device checklist (Xiaomi/HyperOS + second phone/web, owner):
- [ ] Dark, light, love and samurai: inspect every card in both sender directions, pending/failed rows, sealed/revealed ask and this-or-that, countdown ticks, check-in moods, location tile/actions, image, voice, file, alarm and call logs. Compare text, hints, borders and glyph colors to the web.
- [ ] Open both letter themes; preview, edit, send, view and download a long letter. Verify serif type, gradient/inset, wrapping and scroll reachability.
- [ ] Exercise every dialog in dark/light: all choices, counters/limits, busy/error and close guards, date/time picking, keyboard, rotation and large font/TalkBack. Verify upload, location, appearance, both report variants, block, export and image viewer.
- [ ] Pick a photo/file, record/stop/discard/send a voice note, play/stop, retry an unavailable photo, open/save attachments and export both formats. Check permission denial/grant and the recorder's background/disposal cleanup.
- [ ] With two clients, raise/ack/cancel/expire alarms and verify pending-to-server-ID guards and ringing behavior are unchanged.
- [ ] Incoming/outgoing, connected/reconnecting and video calls in dark/light, portrait/landscape: Answer/Decline/Cancel/End, Mute/Speaker/Camera/Flip remain reachable; verify remote video, mirrored local preview, audio routes, mic/camera permissions and lock-screen continuation on hardware.
- [ ] Disable animator scale and confirm static ringing/reconnecting rings; re-enable while open and verify the 2s/1.1s pulse. Entry, rotation, narrow widths and large fonts must not obscure controls.
- [ ] Revisit the image metadata, voice-progress, call-back and shared-modal TODOs when their owning interfaces are extended.

## S5 — restyle polish (2026-10-10)

Status: done (Opus 5.5). Final consistency pass after S1–S4.

- Chat dialogs still on stock Material (alarm confirm, resend, nickname, leave) now use `FeatureModal`/`OneModal` with web buttons: Send alarm / Advance / End = DangerButton, Resend / Save = PrimaryButton, Cancel / Keep = SecondaryButton, nickname = `OneTextField`. Callbacks unchanged.
- Alarm: the 4dp danger bar moved from the card onto the bubble's left edge (web `.chat__message[data-type='alarm'] .chat__message-body { border-left }`), for every alarm-type bubble; `AlarmContent` lost its now-unused `live` param.
- Sign-in legal links match web `.screen__legal`: Privacy Policy, Terms, Child Safety, 12sp text-dim.
- Removed `res/raw/keep.xml` and the three never-used icons (`ic_arrow_left`, `ic_search`, `ic_volume_off`); every remaining `ic_*` is referenced.
- Audit: no `Color(0x…)` literals outside `ui/theme` except the documented `LetterThemes`; no stock `AlertDialog` / `OutlinedTextField` / `TextButton` / `FilterChip` left in UI.

Verification: `./gradlew assembleDebug lintDebug testDebugUnitTest` green. Pixel 9a emulator (API 36): sign-in screen renders with Fraunces title, Figtree body, accent-you primary button, web legal row — `docs/design/screens/android-signin-dark.png`.

Known content differences vs web (not styling, left as-is pending owner call): Android sign-in shows the app logo, title "One on One" and tagline; web shows only eyebrow + title "one connection. nothing else.".

Device checklist: open each chat dialog above (dark + light); alarm bubble shows red left bar for raise and ack; legal links open the right pages.

## D1 — Composer, keyboard and attachments

Status: build verified (see PR); device check pending.

- Keyboard gap: `MainActivity` uses `windowSoftInputMode="adjustResize"`; `ChatScreen` no longer applies `imePadding()` on the root, and `ChatComposer` applies `navigationBars ∪ ime` once, so the header stays visible and the composer sits flush on the keyboard.
- Mic records immediately (asks for `RECORD_AUDIO` first if missing). While recording the composer row is a 48dp bar: trash (discard), pulsing red dot + mm:ss timer, and a stop circle that stops and sends the voice note (reply respected). `FeatureViewModel` gained `stopAndSend` and `cancelRecording`; `stopRecording`, the voicePath "Send voice note / Discard" row and `AttachmentControls` are gone. Under one second still shows "Record for at least one second." and nothing is sent. The recorder's size/length cap sends what was recorded.
- Attach chooser is Photo + File only: a bottom-anchored `AttachSheet` above the composer (animated, tap outside or the pin to close). Picker launchers live in `ChatScreen`, so picking closes the sheet immediately.
- `OneModal` has `placement: ModalPlacement { Top (default), Bottom }`, threaded through `FeatureModal`. The upload confirmation uses Bottom (full-width sheet, top corners md10, nav-bar inset, slides up); photo preview max 280dp, rounded, `ContentScale.Fit`.
- Previews added for the recording bar, attach sheet and bottom modal.

Device checklist: open keyboard in chat (header visible, no gap, none after closing); mic tap records, stop sends, trash discards, under 1 s errors; pin shows only Photo/File; pick a photo -> sheet closes, confirm sheet at the bottom; leaving the chat mid-recording discards it.

## Releases

| versionName | versionCode | Date | Workflow run | Contents |
| --- | --- | --- | --- | --- |
| 1.0.4 | 5 | 2026-10-09 | [37893939464](https://github.com/aarahman04/One-on-One-Android/actions/runs/37893939464) | A0–A7 native app |
| 1.0.5 | 6 | 2026-10-10 | [37998420889](https://github.com/aarahman04/One-on-One-Android/actions/runs/37998420889) | A0–A7 + S1–S5 web-styling restyle |

- Both built by the `android-build` workflow (manual dispatch on `main`); version
  name/code are workflow inputs, not stored in the repo. Signed with the existing
  Play upload key (SHA-1 check in the release job passed). Use the
  `android-release` artifact (AAB + APK); `android-debug` and
  `android-release-smoke-unsigned` are test-only.
- Not yet uploaded to Play. **Next upload needs versionCode 7 or higher.**
- Owner next steps for 1.0.5: install the APK over 1.0.4 on both phones, run the
  S2–S5 device checklists above, then upload the AAB to internal testing.
