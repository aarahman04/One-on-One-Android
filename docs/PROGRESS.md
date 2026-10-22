# Android milestone progress

Current Ping Pong work: [reference remake](games/PONG-REFERENCE-REMAKE.md)
(2026-10-22). The 2026-10-21 handoff describes the earlier implementation.

## Ping Pong reference remake and red-racket contact (2026-10-22)

- Rebuilt the existing game from recovered flight/stroke constants and the owner's video: live finger-controlled endpoint drift, native cubic/easing, elapsed-time racket following, green court, upright red/cyan rackets and a short white trail.
- Optional raw `touch` samples keep finger power/spin separate from the eased racket; additive `drift` lets Android render the authoritative curve without the previous exponential approximation. Existing authentication, custom points, pause and rematch stay in their existing layers.
- Fixed the owner's forward-stroke overlap: collision includes both black outlines and the ball's height-dependent size. Client and server stop an advancing racket at its front contact; the renderer holds pending contact on the rim, and the simulator tests existing contact before advancing the flight. Scores and hit counts remain authoritative.
- Validation: debug build, lint and 260 Android tests passed; backend final full rerun passed 124/124 and TypeScript checking passed. An unrelated call-disconnect test raced in an earlier run; isolated and full reruns passed without changing it. Final debug APK installed on separate `PongRemake_Check`; controls left to the owner. Remaining phone checks and artifact hash are in the reference-remake document. No production deploy or Play upload.

### Xiaomi checklist

- [ ] Pull red racket back and swipe forward into a return, including near either side: ball meets the black rim and travels away.
- [ ] Neutral blocks, soft placements and hard forward strokes retain predictable aim and pace; finger release stops sideways steering.
- [ ] Test both seats on two accounts with normal and delayed network; verify scores, custom target, serves, pause/resume and rematch.
- [ ] Check light/dark themes, TalkBack controls, and smooth racket following at the phone's available refresh rates.

## Release 2.0.9 (2026-10-21, versionCode 16)
- Release workflow defaults bumped to 16 / 2.0.9. Contents since 2.0.8: games round 3 and Ping Pong (#43/#44), owner fix batch (#45), logos/viewer/BiRefNet stickers (#46), cross-check (#47); backend #122, #123, #124. No new migration (latest remains 040).
- Signed build run from main by Claude Code at the owner's request; Play upload is the owner's step.

## Release 2.0.8 (2026-10-20, versionCode 15)
- Signed build from main (run 39444598003) uploaded to Play by the owner. Contents: Android #37 (below), backend #115 (invite push, `game` rows, migration 040 applied in Supabase).
- Speech fixed on Railway (no app change): the `speech` service now runs in the backend's project (`One on One`, renamed from `fabulous-connection` on 2026-10-20), so `speech.railway.internal` resolves, and the backend's `SPEECH_SERVICE_TOKEN` references `${{speech.SPEECH_SERVICE_TOKEN}}` (verified equal, values not read). The old separate `speech` project is deleted.
- Release workflow defaults bumped to 15 / 2.0.8.

## Game invites, game history, letter mic, polish (2026-10-20, fix/game-invites-history-icons; backend #115)
- Game invites reached nobody off the chat screen: the app keeps its socket in the background, so the server created the invite but the banner only lives in the chat. Backend #115 now sends a data-only `game_invite` push; the app shows "Alex wants to play Chess" (30 s timeout) only when the chat isn't resumed, with the same owner/connection gates as message pushes.
- Played games appear in the chat as server-authored `game` rows (migration 040): game icon, "Chess", "You won · 12 min 14 sec". Not editable, no reply, no receipts. Call logs now read "Voice call · 5 min 12 sec" instead of an ambiguous "5:12".
- `/letter` has a mic in the corner of "Your letter": same `/audio` dictation pipeline, words are appended to the letter instead of the composer; closing the letter cancels a running dictation. The letter body field has a proper writing height. Dictation hint now says English only (Whisper base.en).
- Profile/partner photo decoding uses the platform ImageDecoder on API 28+ (orientation applied like the gallery, including mirrored tags); manual EXIF rotation stays only for API 26-27.
- Header phone icon: current Lucide path with a 1-unit inset; video icon inset too (strokes touched the viewport edge).
- Background `match:sync` failures no longer flash "Couldn't reach the game" when the chat opens; user actions still report errors.
- Speech: no app change needed. Railway backend had `SPEECH_SERVICE_URL=http://speech.railway.internal:8000` pointing into a different Railway project and an empty `SPEECH_SERVICE_TOKEN`, so every request was 503 (owner fixes in the dashboard).
- Verification: `gradlew assembleDebug lintDebug testDebugUnitTest` passed (202 tests). Debug demo checked on the emulator (game/call rows, letter mic states). Not device-tested.

### Xiaomi checklist
- [ ] Partner in background or on Settings: invite shows a notification; tapping opens the chat with the Accept banner; it disappears after 30 s.
- [ ] Partner in the chat: no notification, banner only.
- [ ] Finish a game (and a rematch): both phones show one game row each with winner and duration; declined/expired invites add nothing.
- [ ] Call log rows read "Voice call · N min N sec".
- [ ] /letter: mic → speak → stop → words appear in "Your letter"; close mid-recording stops the mic.
- [ ] /audio and letter "Listen" use the server voice once Railway is fixed.
- [ ] Settings → change photo with a portrait camera photo: upright for you and your partner.
- [ ] Chat header phone/video icons look complete in light and dark.

## Polish + 2.0.6 release (2026-10-19, versionCode 13 / 2.0.6, fix/pro-polish)
- Card headings use vector icons (letter, ask, this-or-that, check-in, countdown, location pin, alarm siren) instead of emoji; `alarm_title` and alarm status lines are plain text.
- Call log icon reads "Video call"/"Voice call" to TalkBack instead of emoji names.
- Human labels for choices (moods, Option A/B, wallpapers, theme, export formats, report categories) and system lines ("You changed the wallpaper to Samurai").
- Composer titles read "Write a letter", "Ask a sealed question", "This or that", "Start a countdown", "Check in" instead of raw `/command`.
- Encryption note says "Messages are encrypted on our servers" (encrypted at rest, not end-to-end). Blocked list no longer shows raw user IDs. Loading screen follows the theme.
- Parakeet (CC-BY-4.0) and Kokoro (Apache-2.0) server-side attribution in docs/THIRD-PARTY-LICENSES.md.
- Release workflow defaults bumped to versionCode 13 / 2.0.6. Round contents: Android #29, #30, #31, #32, this PR; backend #109, #110.
- Verification: `gradlew.bat assembleDebug lintDebug testDebugUnitTest` passed (211 tests). Build-verified only.

### Xiaomi checklist
- [ ] Card headings show icons (no emoji) in light and dark; alarm card shows siren icon.
- [ ] TalkBack on a call log card says "Voice call"/"Video call".
- [ ] Appearance and export choices show readable labels; changing wallpaper shows a readable system line.
- [ ] Each slash composer shows a readable title.
- [ ] Settings > Blocked shows "Blocked account".
- [ ] Full 2.0.6 regression: sign out/in no gates, leave + zip, partner-left, ticks, games, letters (PDF, natural voice), /audio.

## Natural letter voice and recorded /audio (2026-10-19, no version change)

Branch `feat/natural-voice`, based on `origin/main` after mini-games. Keeps the
letter design and title speaker control; a ViewModel now owns authenticated
part downloads, loading/play/pause/stop, a single next-part prefetch and two
linked MediaPlayers. Cache files and players are released on stop, dismiss,
audio-focus loss and backgrounding; rotation retains the session. Device TTS
remains the fallback for network/503/504 failures, with an in-modal notice.
Missing letters show the requested 404 notice. Device voice resumes at the
latest word boundary reported by the engine (or the last completed chunk).

`/audio` now records a separate AAC/MPEG-4 temp file, shows the existing
listening bar with a timer, then uploads raw `audio/mp4` to `/api/transcribe`.
The limit is 120 seconds / 3 MB. Success appends to the current draft and
focuses the composer; it never sends or creates a voice-note message. Cancel
aborts recording/upload and deletes the file. Voice-note and dictation recorder
ownership are mutually exclusive, including during asynchronous cleanup.
SpeechRecognizer / system recognition remains the fallback for network/503/504;
no-speech, too-long and rate-limit responses stay in-app. The first-use language
hint is persisted in DevicePreferences. No dependency or versionCode change.

Only the two endpoints supplied in the owner-approved Speech (2.0.6) contract
are used; narration sends connection/message IDs and part index, never text.
The backend repo is unchanged. The backend feature branch was not yet published
when implementation began; live endpoint/deployment verification remains open.

Validation: `gradlew.bat assembleDebug lintDebug testDebugUnitTest` passed;
211 JVM tests, zero failures/errors/skips, strict lint reports no issues.
The 18 new speech tests exercise sequential prefetch/handoff, rapid taps,
pause at a part boundary, stop/focus loss, missing/inconsistent parts, device
fallback, transcription success/errors/cancel/late results, timer/length caps,
recorder-start cleanup, external-result tokens and draft whitespace. Retrofit
tests check authenticated ID-only narration GETs and raw audio/mp4 POSTs.
`git diff --check` passed. Existing SDK XML / Gradle deprecation notices remain.
No physical-device, live-backend or two-account verification is claimed.

### Xiaomi checklist

- [ ] Natural voice reads short and long letters fully, across all parts.
- [ ] Pause / resume / stop; close letter stops audio; rapid taps never overlap.
- [ ] Rotation keeps playback and controls; backgrounding and audio-focus loss stop it.
- [ ] Airplane mode -> "Natural voice unavailable, using device voice" fallback.
- [ ] `/audio` records Indian-English speech, transcribes into the box without sending.
- [ ] Existing draft text is preserved when dictated text is appended; input focuses.
- [ ] Cancel during recording and upload; no late text or leftover temp file.
- [ ] Silence -> "Didn't catch that. Try again."; two-minute limit and timer.
- [ ] Voice notes are unaffected; neither recorder can start over the other.
- [ ] First-use language hint appears once; device speech fallback can be cancelled.
- [ ] Light/dark mode, large fonts, TalkBack control labels; no device testing claimed.

## Mini-games (2026-10-19, no version change)

Branch `feat/mini-games`, based on `origin/main`. Adds `/games` with a themed hub,
in-chat invitations (30-second expiry), cancel/accept/decline, five full-screen
two-player games, result/rematch, resign/leave confirmation and disconnect pause.
Commands and relay events never enter the message payload validator or Room.
Backend repo unchanged; API authority is its §10 Games relay. No new endpoint,
event, socket, push notification or versionCode change.

Rebased onto `origin/main` (`372f0e0`, PRs #29 and #31 merged). Keeps `/audio`
and `/games`, draft clearing before every command, and all nine slash commands.
Main's instant read/delivered receipts and instant leave/export remain intact.

Pure Kotlin engines cover chess (chesslib 1.3.7), table tennis, Connect 4,
tic-tac-toe and 5×5 Dots & Boxes. Both peers check move legality/order; invalid
remote moves end with a clear notice. Table tennis uses host authority, display
frames, 29.4 Hz relay, bounded guest prediction, increasing ball speed and first
to seven. Chess includes original vectors, black rotation, selection/drag,
promotion, captured pieces and all requested check/draw outcomes. Board surfaces
use OneTheme, motion, haptics and semantic controls; no bitmap/emoji game icons.
Saved IDs/roles are scoped to account/conversation; rotation keeps live engines,
process restoration cleanly ends the prior game. KEEP_SCREEN_ON is screen-scoped.
Tic-tac-toe's optional best-of-three tally is local across ordinary rematches;
the documented invite/move schemas have no shared series metadata.

Validation: `gradlew.bat assembleDebug lintDebug testDebugUnitTest` passed:
193 tests, zero failures/errors/skips; strict lint passed with no issues.
This includes 35 new game tests and the added slash-command test. JVM checks
exercise game rules, validation, ordering, invitation/disconnect timers, late
events, restoration and relay pacing. `git diff --check` passed. Build tooling
still emits its existing SDK XML / Gradle deprecation notices; no lint baseline
or new lint exclusion was added.
No physical-device or live two-account testing is claimed. Mobile-data latency,
actual frame pacing, touch feel, light/dark rendering and TalkBack require phones.

### Xiaomi checklist

- [ ] `/games` blocked when partner offline; same notice on `partner_offline` ack.
- [ ] Invite / accept / decline / cancel / 30-second timeout.
- [ ] Each of the 5 games played to a result; verify chess castling, en passant,
  all four promotions, check/mate, stalemate, repetition, 50-move/material draw.
- [ ] Rematch; fresh board and UUID; tic-tac-toe best of 3 tally.
- [ ] Resign; leave mid-game; Back confirms leaving.
- [ ] Partner kills app mid-game -> “Waiting for <nickname>…” then end after 15 s.
- [ ] Rotation mid-game on both phones; kill/relaunch ends the previous game.
- [ ] Table tennis smooth on mobile data and Wi-Fi; 3-2-1 serve, drag, first to 7.
- [ ] Light and dark mode; large fonts; TalkBack every square/cell/line and turn.
- [ ] Screen awake during play; normal screen timeout restored on result/exit.
- [ ] Incoming call controls remain visible and answerable over a game.

## Instant leave, zip export, server gates, instant receipts (P3, 2026-10-19, 12 / 2.0.5 unchanged)

Branch `feat/instant-leave-export`, against the contract of backend PR #109
(merged to One-on-One main as `bd3be70`). versionCode not bumped.

- Sign-in gates: server `ageVerifiedAt`/`termsAcceptedAt` are truth; local gates
  are only a same-account cache, pushed up once when the server lacks them.
  Reinstall or sign out/in with the same account no longer asks DOB/rules.
- Leave: one centred dialog (save a copy then leave / leave without saving /
  cancel); countdown banner, step fields and cancel/confirm-end routes removed.
- Zip export: `One on One - <partner> - <date>.zip` with `conversation.html`,
  `conversation.txt`, `letters/`, `images/`, `images/stickers/`, `voice/`,
  `files/`; progress + cancel; a failed/cancelled save deletes the document.
- Partner left: `PartnerLeftScreen` (save or continue), then acknowledge-end,
  local cache cleared, home. `connection_left` FCM opens it. Accepting a new
  request while an ended chat exists warns first.
- Receipts: socket `message:read` per incoming message while the chat is
  resumed (REST fallback, heartbeat kept); delivered on FCM arrival, debounced.

Verification: `gradlew.bat --no-daemon assembleDebug lintDebug testDebugUnitTest`
passed: 143 tests, zero failures/errors, lint passed. Build verification only;
no physical-phone or live-account verification claimed.

Known limits: an archive holds the message list (not files) in memory; delivered
debounce is lost if the process dies within 1 s (socket connect covers it);
the "old chat" warning on Accept persists the ended id last seen from
`/connections/current` (pending outranks ended there, so a request that arrives
before the app ever saw the ended state cannot warn: contract gap). The
`connection_left` notice is shown only for this account's own ended connection,
ended by the partner, with a locally built title.

### Xiaomi checklist

- [ ] Sign out and back in with the same account: no DOB or rules screen.
- [ ] Uninstall/reinstall, sign in with the same account: no DOB or rules screen.
- [ ] Leave with "Save a copy, then leave": zip opens; folders `letters/`,
  `images/`, `images/stickers/`, `voice/`, `files/` are right; `conversation.html`
  reads well; deleted messages are absent; then the app is on the code screen.
- [ ] Leave without saving: goes straight to the code screen.
- [ ] Partner with the app closed gets "<name> left the connection" notification;
  tapping opens the save screen; "Save conversation" writes the zip; then home.
- [ ] Partner taps "Continue without saving": "Delete without saving?" confirm
  appears; "Go back" returns to the save screen; "Delete" goes home, conversation gone.
- [ ] Partner with an ended chat gets a new request, force-stops the app, reopens:
  tapping Accept still warns "Your old chat will be deleted".
- [ ] Both in the chat: the blue tick appears in under a second.
- [ ] Partner app closed: notification arrives and the sender sees the double tick.

## P2 letter, audio and polish (2026-10-19, 12 / 2.0.5 unchanged)

Branch `feat/letter-audio`. Client only; no backend, contract or versionCode change.

- Letter PDF: signature is unbreakable and always on the last page foot, kept with at
  least the last 3 body lines. A letter that overflows one page only by the signature
  steps 20 -> 18 -> 17pt to fit one page (pure `planLetterPages`/`chooseLetterSize`,
  unit tested). On-screen `LetterBody` keeps readable sizes (18sp body) on a scrollable
  A4-height page; it does not mirror the PDF's line breaks.
- Letter reciter: speaker button in the viewer title (TextToSpeech, lazy init, stops and
  shuts down on close); friendly in-modal notice if TTS is unavailable.
- Picking a slash command (tap or send) now clears the "/" draft and closes the menu (pre-existing on main).
- `/audio`: client-only dictation (SpeechRecognizer, system dialog fallback), text is
  appended to the composer draft and sent through the normal path. Not a message type.
- /ask meta stays at the bubble end for text-only cards; /thisorthat tiles; swipe-to-reply
  claims only clearly horizontal drags; reactions are a single WhatsApp-style pill.

Verification: `gradlew.bat assembleDebug lintDebug testDebugUnitTest` passed. Pixel_9a
emulator (debug demo) checked letter preview, speaker start/stop, /ask send, /thisorthat
revealed tiles, swipe-to-reply. Not checked on a phone: PDF output, /audio, light mode.

### Xiaomi checklist

- Full-page letter keeps the signature with the body on page 1; page 2 never starts with the signature alone.
- On-screen letter is readable; the downloaded PDF paginates on its own.
- Speaker reads the letter, tapping again stops it, closing the viewer stops it.
- `/audio` fills the composer; denying the microphone shows a friendly message.
- /ask time and ticks sit bottom-right on mine.
- /thisorthat tiles in composer, sealed (tappable) and revealed (You/partner chips, "You matched!").
- Fast vertical or diagonal scrolling never starts a reply.
- Swipe left on mine and right on theirs replies smoothly (rubber band, one haptic).
- Reactions show as a WhatsApp-style pill overlapping the bubble, readable in light and dark.
- Tap the reaction pill: a sheet shows who reacted; tap your own row to remove it; "+ Add reaction" opens the quick bar.

## Live presence follow-up (2026-10-19, 12 / 2.0.5 unchanged)

Branch `feat/live-presence` implements the sibling backend's documented presence
event. Chat visibility and reconnect send through MessageService -> Transport;
partner snapshots flow back through the ViewModel to the header. Explicit offline
overrides fresh receipts; absent snapshots retain receipt fallback. Presence
clears on disconnect/conversation teardown; own network status keeps priority.
PR #27 is merged. Owner requested unchanged versionCode 12 / versionName 2.0.5.

Verification: `gradlew.bat --no-daemon assembleDebug lintDebug testDebugUnitTest`
passed: 129 tests, zero failures/errors, lint passed. Backend build + 39 tests,
web build + 16 tests and isolated Chromium 1280/360px presence checks passed.
No physical-phone/live-account verification claimed. Backend/web
[PR #107](https://github.com/aarahman04/One-on-One/pull/107) merged as `32233b1`;
Railway deployment `6d5e5a0d-c2ff-455b-9545-f2e8dc296173` succeeded and production
`/health` returned `ok`. Vercel reported success. No Supabase migration is needed.
Presence reconnect handling runs independently of REST history resync; a blocked
history request cannot prevent the current foreground state from being resent.

Xiaomi checks: foreground/background both phones; phone + web stays Online while
either is active; reconnect resends current state; fresh receipts cannot override
explicit offline; old backend/no event retains receipt fallback. Abrupt network/
power loss waits for socket disconnect detection.

Use the fresh workflow's `android-release/app-release.aab` for Play, APK for phone
checks. Earlier code-12 artifacts lack this wiring. If code 12 has already been
uploaded to Play, it cannot be uploaded again. This work does not upload to Play.

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
| D2 | Local checks green; PR review pending; live QA skipped | Chat header presence and logo-only launch |
| D3 | Local checks green; PR review pending; fixture emulator QA passed | Compact attachment cards, native viewers and friendly dates |

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
| 2.0.0 | 7 | 2026-10-10 | [38031209794](https://github.com/aarahman04/One-on-One-Android/actions/runs/38031209794) | D1–D5 design fixes, presence, viewers, settings/account, alarm send fix |
| 2.0.1 | 8 | 2026-10-13 | [38348089207](https://github.com/aarahman04/One-on-One-Android/actions/runs/38348089207) | Scroll-to-end, web-parity search, crisp splash logo |
| 2.0.2 | 9 | 2026-10-13 | [38377404608](https://github.com/aarahman04/One-on-One-Android/actions/runs/38377404608) | Onboarding/connect redesign, gates per sign-in, links, friendly errors, vector icons |
| 2.0.3 | 10 | 2026-10-14 | [38487293539](https://github.com/aarahman04/One-on-One-Android/actions/runs/38487293539) | Chat polish (gestures, selection, alarm glow, calls, share target), edit/delete/pin/typing, any-emoji reactions, link previews, /location fix, animations |
| 2.0.4 | 11 | 2026-10-14 | [38551746052](https://github.com/aarahman04/One-on-One-Android/actions/runs/38551746052) (success) | Chat polish round 2 (alarm silence, inline push, composer, GIPHY, profile photos), sticker maker + packs, bare stickers, photo time overlay, 12-hour times, instant media sends |
| 2.0.5 | 12 | 2026-10-19 | [39192560039](https://github.com/aarahman04/One-on-One-Android/actions/runs/39192560039) (success; app commit `7db64a3`) | Owner feedback: account gates, composer hints, sharing, handwritten PDF letters, card selection, call/alarm fixes and clock-skew-resistant presence; PR #27 unmerged |

- Signed releases use the `android-build` workflow (manual dispatch on the selected branch;
  use reviewed `main` for Play). Version name/code are workflow inputs with defaults in
  `.github/workflows/android-build.yml`. Signed with the existing
  Play upload key (SHA-1 check in the release job passed). Use the
  `android-release` artifact (AAB + APK); `android-debug` and
  `android-release-smoke-unsigned` are test-only.
- 2.0.5 uses versionCode 12 (workflow defaults 12 / 2.0.5); local Gradle defaults remain
  5 / 2.0.0-dev. **The upload after code 12 needs versionCode 13 or higher.** These
  workflow builds do not establish which codes have been uploaded to Play.
- 2.0.4 needs backend migration 037 (run 2026-10-14) and `GIPHY_API_KEY` on Railway (set 2026-10-14).
- 2.0.3 needs the backend from web PR #100 (merged to web `main`; deployed to Railway 2026-10-14 with `railway up`) and migration 036 (run 2026-10-14).
- Owner next steps for 1.0.5: install the APK over 1.0.4 on both phones, run the
  S2–S5 device checklists above, then upload the AAB to internal testing.

## D2 — presence and logo-only splash (2026-10-10)

Branch `design/d2-presence-splash`, based on main. No backend changes. The
reference-image folder and D1's composer/feature/overlay files are excluded;
ChatScreen changes only the ChatHeader argument.

- MessageService runs a heartbeat only while the chat is resumed, suppresses
  heartbeat reads for ten seconds after any successful read, and cancels on
  pause/deactivation. Immediate message/resync/notification reads remain immediate.
  Monotonic timing and a shared read mutex prevent overlapping heartbeat reads;
  skipped ticks wait only the remaining interval, avoiding a 20-second gap.
- The newest connection/live receipt timestamp drives the header's strict
  15-second Online window. A resumed five-second ticker expires it without events.
  Own socket Offline/Connecting wins; otherwise show Online, today's/yesterday's
  local time, an older locale-aware date, or Offline. System 12/24-hour preference
  determines the time pattern. ChatHeader crossfades pure label/dot inputs.
  Single/double/read tick calculation and rendering are unchanged.
- AndroidX core-splashscreen 1.2.0, a launcher-activity starting theme, the existing
  launcher foreground inset 10% per side (28.8dp on a 288dp canvas), and the fixed dark
  background provide a logo-only splash. All six bitmap densities fit inside the
  192dp safe circle (maximum normalized artwork radius after inset: 0.610 < 2/3).
  installSplashScreen precedes super.onCreate; keep-on-screen reads the boot route
  with a monotonic three-second cap. Compose Loading uses the same image/size and
  background, without ScreenFrame's text, progress or entry animation; its preview
  covers both app themes. Window/system bars stay dark while Loading.

Verification: `gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
--max-workers=2` passed on the final code (77 JVM tests, zero failures/errors;
lint: no issues). Added pure
presence checks for the exact 15-second boundary, today/yesterday/older/null,
socket priority, locale, time patterns, timezone boundaries and newest receipt;
service checks cover heartbeat cadence, recent-read suppression, repeated resume,
pause/deactivation cancellation, failed/in-flight reads and visibility before
activation. Live two-client testing is skipped at the user's request. No physical
Xiaomi/device verification is claimed. The API 36 emulator showed the intact
centered logo on the dark splash and reached sign-in; captures are linked below.
The first capture was obscured by a System UI ANR, dismissed before recapture;
the successful launch log has no app crash or ANR. Emulator process startup was
slow, so this is visual evidence, not a performance or three-second-cap timing
claim. Slow authenticated-session handoff, light appearance, API 26 fallback,
live presence/Wi-Fi and Xiaomi behavior remain unverified.

Screenshots: [cold launch](design/screens/d2-cold-launch.png),
[after launch](design/screens/d2-after-launch.png).

### Xiaomi / HyperOS and two-client checklist (unverified)

- [ ] Cold launch in dark/light appearance: only the intact centered logo on dark
  background, then the app; no opening text/spinner or logo handoff flash.
- [ ] With a slow restored session, native splash releases by roughly three
  seconds; the Compose loading logo remains aligned. Rotate/background/relaunch.
- [ ] With two accounts, open the other chat: Online within roughly ten seconds;
  close/background it: Last seen within 15–20 seconds after its last read.
- [ ] Turn off Wi-Fi: Waiting for network; reconnect: Connecting… then presence.
- [ ] Change timezone/locale and 12/24-hour settings; verify today, yesterday,
  older dates, muted/accent dots and TalkBack/large-font header truncation.
- [ ] Confirm single grey / double grey / blue ticks, message reads and calls
  retain their existing behavior.

## D3 - compact attachment cards and viewers (2026-10-10)

- Voice cards use a 44dp filled vector play/pause control, optical play offset,
  elapsed/duration label and real progress. MediaPlayer position polling runs
  every 100ms during playback and stops on pause, completion and background.
  Save a copy lives in the existing voice bubble long-press menu.
- Photos open by tapping the image. The black full-screen dialog has safe
  system-bar insets, close/save controls, bounded pinch zoom, double-tap zoom
  and accessibility zoom/reset actions.
- File cards show a type badge, readable decimal size, View and a small download
  icon. DocumentViewer uses the existing downloaded content URI: PDF pages
  render serially on IO at screen width, visible pages own disposable bitmaps,
  and the renderer/descriptor close on disposal. Text/CSV previews use lazy
  monospace rows with a 1 MiB cap and a truncation note. Office files use the
  existing Open with chooser; all document viewers offer save and Open with.
- Dates use Today, Yesterday, localized weekdays for the previous six calendar
  days, then a localized day/month/year. Unit coverage includes the seven-day
  boundary, time zones, locale/year boundaries, progress/size/type formatting,
  bounded text reads/rows, PDF raster limits and photo pan bounds.
- ChatScreen changes only dayLabel. FeatureUi changes only the attachment-card,
  openAttachment and PhotoViewer areas plus imports. D4-owned settings/menu/
  appearance/export code and the separate D4 worktree are untouched. No new
  dependency or backend change. The design-reference folder is excluded.

### Verification

`gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
--max-workers=2` passed after removing the temporary QA sources: 83 tests,
zero failures/errors, zero lint issues. The final APK contains no fixture
activity, instrumentation or assets; it installed and cold-launched on the
separate AVD without an AndroidRuntime crash.

A separate API 36 Pixel 7 AVD (`OneOnOne_D3`, emulator-5560) exercised the actual
production composables and native MediaPlayer/PdfRenderer using temporary local
fixtures. The fixture activity, instrumentation and assets were removed before
final build/commit. No authenticated backend attachment test is claimed.

- Playback advanced, paused at a stable position and resumed. The compact card
  and play icon were inspected in dark and light themes.
- Image tap opened full-screen. Native two-finger events enlarged the displayed
  image; double tap returned it to fitted size (colored pixel area
  14376 -> 62414 -> 14376).
- A 12-page PDF opened and scrolled with its indicator. Rotation retained the
  viewer and one descriptor; close released it. Rapid repeated scrolling and
  closing produced no AndroidRuntime crash and left no PDF descriptor open.
- TXT and CSV opened in-app; a large text file showed the truncation note. DOCX
  offered the chooser. The AVD has no Office handler, so launching an installed
  Office application remains unverified.
- Voice-menu, file-card, photo-viewer, TXT-viewer and CSV-viewer saves wrote
  byte-for-byte matching files. PDF toolbar save returned to the viewer.

Screenshots (local fixtures, not a signed-in conversation):
[cards dark](design/screens/d3-cards-dark.png),
[cards light](design/screens/d3-cards-light.png),
[voice playback](design/screens/d3-voice-playing-dark.png),
[voice actions](design/screens/d3-voice-actions-light.png),
[photo fitted](design/screens/d3-photo-fit-dark.png),
[photo zoomed](design/screens/d3-photo-pinch-dark.png),
[PDF scrolled](design/screens/d3-pdf-scrolled-light.png),
[PDF landscape](design/screens/d3-pdf-landscape-light.png),
[TXT](design/screens/d3-text-light.png),
[CSV](design/screens/d3-csv-light.png),
[text cap](design/screens/d3-text-truncated-dark.png),
[Office chooser](design/screens/d3-office-chooser-light.png).
Viewer surfaces intentionally stay black in both appearance modes.

### Xiaomi / HyperOS device checklist (owner)

- [ ] In a signed-in chat, play/pause/resume a downloaded voice note; check
  elapsed time/progress, centered icon, completion and background cleanup.
- [ ] Tap a received photo, pinch/double tap, rotate, close and save from its bar.
- [ ] View a real multi-page PDF, scroll/rotate/close; check the page indicator
  and save/Open with actions. Repeat with TXT/CSV and a file above 1 MiB.
- [ ] View DOCX/XLSX/PPTX with a compatible app installed; confirm the chooser
  can grant read access and save preserves the original bytes.
- [ ] Check Today/Yesterday/recent weekday/older-date separators in the device
  time zone, dark/light appearance, large fonts and TalkBack controls.
## D4 — Settings and Account redesign (2026-10-10)

Status: built, lint/unit tests green; branch `design/d4-settings-account`.
New shared kit `ui/components/Settings.kt` (`SettingsScaffold`, `SettingsGroup`,
`SettingsRow` with Chevron/Value/External/Chip/Action trailing, `SettingsCaption`,
`SettingsExpandable`) plus 18 new Lucide-style vectors. Settings, Account (new),
Blocked accounts and Notifications screens all use it (`ui/SettingsScreens.kt`,
`NotificationOnboarding.kt`). The chat 3-dot menu is now Search, Nickname,
Settings, Account and a collapsible "Connection" group (Report, Block, Leave).
Appearance and Export moved to Settings ("Chat" group, only with a chat
connection); without a connection a Theme row opens `AppearanceDialog` with the
wallpaper section hidden. Account holds the connection ID card (copy), Blocked
accounts (count), Legal links, Sign out and a "Danger zone" Delete account that
reuses the existing confirm modal. First-run notification onboarding keeps a
pinned Continue button; the long OEM text is a collapsible "Why is this needed?".
All intents, confirmations and ViewModel calls are unchanged. Verified by build,
lint and unit tests; @Previews (dark/light) added. Not verified on a device or
emulator (needs a signed-in session): the live screens remain unchecked.

## D5 — Design polish and bug sweep (2026-10-10)

Status: built, lint/unit tests green; branch `design/d5-polish`. Screens checked on
the Pixel 9a emulator through a local (uncommitted) debug gallery, dark and light.
- Headings: the Fraunces serif is gone; screen titles, the settings top bar and the
  call name use Figtree semibold/bold with slightly tighter tracking.
- Settings: Notifications + Account share one "General" group; value rows that are
  tappable (Blocked accounts count) keep a chevron; Child Safety has its own icon.
- File card: the whole row opens the viewer (no separate View button). Voice card:
  timestamp sits on the duration line, so the card is one row shorter. Bottom
  sheets use a 28dp top inset.
- Bugs fixed: stop-and-send was skipped while another action was busy (recording
  kept running); a double tap on stop showed a false "Record for at least one
  second"; a recorder failure during discard could crash the app; starting a
  recording while a voice note played let playback bleed into the clip; a stale
  error appeared in the next upload sheet; mimes with parameters
  (`text/plain; charset=utf-8`) skipped the in-app viewer. New unit tests cover the
  stop double tap and mime normalisation.
- /alarm audit: ring/stop paths (FCM + socket, server-confirmed acks only, token-checked
  activity extras, 2-minute window, handled-id store, fallback notification) checked
  against the backend rules — correct. One bug fixed: `ChatViewModel.sendCard` was
  busy-gated, so "Send alarm" (or an ack/cancel tap) while a text send was in flight
  was silently dropped while the dialog closed. It now always enqueues; the outbox
  lock serialises sends and the server dedupes acks.
- [ ] Device check: record → stop sends once; trash; record while a note plays;
  /alarm while a message is sending still raises; ack and cancel stop the ring.

## 2.0.1 — scroll-to-end, search parity, crisp splash (2026-10-13)

Branch `release/2.0.1`. Build-verified only (`assembleDebug lintDebug testDebugUnitTest`
green); nothing device-tested. No backend, endpoint or cache changes.

- Chat stays pinned to the newest message: opening (Room cache then server), keyboard
  opening, images/cards growing and tapping the composer all land on the end, and a
  tall last bubble is scrolled fully into view. A user scroll that stops short of the
  bottom unpins; a reply from the other side then leaves you where you are.
- Search matches web: the full log stays, every match is highlighted (accent-you;
  current match accent-other with an accent border), `N/M` count, ▲ older / ▼ newer
  wrapping, starts on the newest match, keyboard Search = older, Back closes and
  clears. Search stays client-side over loaded messages (no search endpoint exists).
- Splash logo is now a vector of the web `icon.svg` circles instead of a stretched
  192px PNG.
- `android-build` dispatch defaults: versionCode 8, versionName 2.0.1. Gradle
  fallback stays 5 / 2.0.0-dev. New unit tests: `ChatSearchTest`.

### Xiaomi / HyperOS device checklist (owner)

- [ ] Splash logo sharp on cold start (light and dark).
- [ ] Open a chat with long history + images: lands on the newest message; the last
  bubble is fully visible after images load.
- [ ] Scroll up, tap the text box: jumps to newest; with the keyboard open the newest
  message stays visible.
- [ ] Scroll up, receive a message from the other side: stays where you are; send your
  own: jumps to the end.
- [ ] Search "a": full chat stays, matches highlighted, count `M/M`, starts on the
  newest; ▲/▼ step and wrap; keyboard Search steps older.
- [ ] Back button / ✕ closes search and clears highlights; tapping a quote still jumps.
- [ ] Rotate, dark mode, Love and Samurai wallpapers: highlights readable.

## 2.0.2 — onboarding and connect polish (2026-10-13)

Branch `fix/onboarding-connect`. `assembleDebug lintDebug testDebugUnitTest` green.
Emulator-checked (OneOnOne_D3, 1080×2400) via a local, uncommitted route harness with a
dummy ID `K7F29PQ2`: sign-in, age + date picker, consent, connect, enter-their-ID,
request, account delete dialog. No Google account on the emulator, so the real sign-in,
backend calls and chat changes are **not** device-tested. No backend or endpoint changes.

- Connect: one title (duplicate eyebrow removed), short subtitle, full-width ID card
  (green primary buttons keep their natural width so they don't dominate the navy screen)
  — tap or hold copies, haptic + "Copied" — equal Copy / New ID buttons, primary
  "I have their ID" opens a separate step (paste button, uppercase 8-char field,
  IME Go, Back + system back). Footer is Settings · Sign out (Settings keeps blocks,
  legal and account deletion, which Play requires to stay reachable).
- Sign-in: "ONE" eyebrow and tagline removed (logo, name, button only); Continue with
  Google spans exactly the Privacy Policy · Terms · Child Safety row.
- Ground rules: subtitle "A private space for two people.", the three rules as a bulleted
  card, checkbox row on the same left edge, Agree and continue matches the legal row width.
- Waiting / Request: duplicate eyebrows removed, full-width buttons, same footer.
- Age and Terms gates are cleared on sign-out and account deletion, so every sign-in
  (new, re-created or another account) asks again. Still per-device, as on web.
- Date of birth is an inline iOS-style drum wheel (day / month / year, 7 rows on a cylinder, pill band; look after
  ty0x2333/WheelPicker, built in Compose): one navy selection band, rows curve and
  fade, snap, CLOCK_TICK haptic + system click per step (respects touch-sound setting), tap a row to jump.
  Subtitle shortened to "18+ only. It stays on this device." App background is #0D1017 (owner choice). Starts on today (neutral age
  screen) and Continue stays disabled until a wheel is moved, so a stray tap can't
  land on the under-18 dead end. Scrolling only redraws (graphicsLayer).
- Notification prompt now appears when the chat first opens, not after Terms.
- Every centred modal (alarm, delete account, reports…) is vertically centred.
- Chat links: bare domains (`google.com`, `www.…`) open with https, phone numbers
  (7–15 digits, not dates) open the dialer. `MessageLinksTest`.
- Errors never show ports, hosts or HTTP codes. Chat shows a "No connection ·
  Reconnecting…" pill (after 2s offline) and other errors as a 4-second pill.
- Alarm raise: refused locally within 3 minutes of your last raise (contract limit) and a
  server-rejected raise is removed instead of left as a failed bubble; both show
  "You just sent an alarm. Please wait a few minutes…". Tests in `MessageServiceTest`.
- Launcher icon is an adaptive vector (PNG mipmaps deleted); loading and sign-in logos
  use the splash vector. Disabled buttons use the neutral surface, border and muted text
  (no faded green). Pre-chat errors are one short line ("No connection. Try again.").

Emulator colour note: on this Windows host the OneOnOne_D3 window shows the app
background `#0D1017` as `#001515` (teal). `adb screencap` and SurfaceFlinger (identity
colour matrix, NATIVE mode) confirm the app renders `#0D1017`; every other colour passes
through unchanged, with software or host GPU. Judge background colour from device
screenshots or a real phone, not the emulator window.

Xiaomi checklist:
- [ ] Launcher icon, splash, loading and sign-in logo are sharp.
- [ ] Sign out from Connect, sign in again: date of birth and Terms are asked again.
- [ ] Delete account, sign in with the same Google account: both gates asked again.
- [ ] Notification prompt appears only once the chat opens.
- [ ] Hold the ID card: copies with a buzz and "Copied". "I have their ID" → Paste →
  Connect works; Back returns.
- [ ] In chat, send `google.com`, `https://x.com` and `+91 98765 43210`: each opens.
- [ ] Alarm twice within 3 minutes: second shows the wait pill, no failed bubble.
- [ ] Airplane mode in chat: pill "No connection · Reconnecting…", no port/host text.
- [ ] Delete account and alarm dialogs open in the middle of the screen.
- [ ] Background is navy #0D1017 on every intro screen (not teal/green).
- [ ] Date-of-birth wheel: smooth fling, tick vibration (and click if touch sounds on) per row;
  Continue disabled until a wheel moves.

## Chat polish from owner feedback (2026-10-14, branch `fix/chat-polish-1014`)

Client-only; no endpoint, payload or backend rule added. Build-verified, not device-tested.

- Gestures (WhatsApp model): double-tap a message = quick reaction bar (the 6 contract emoji,
  in the bubble's own wallpaper colours, springs out of the bubble's corner, not focusable so the
  keyboard stays open; Back or a tap outside closes it). Long press = select; tap more rows to add.
  The header becomes close · count · reply (one row) · copy · ⋮ (Save a copy, Report message).
  Copy of several rows joins them oldest first.
- One gesture detector per bubble routes taps by position: links/numbers open on tap and copy on
  long press; the reply quote jumps to the original on tap; a reply bubble can now be long-pressed,
  selected and reacted to like any other.
- Swipe to reply opens the keyboard on the composer; the swipe shows a round reply-icon chip with a
  haptic tick at the trigger. The reply preview is a compact docked card.
- Live messages rise, fade and scale in from their own side over 320 ms (was 160 ms).
- Read ticks: dark blue `#0B57D0` on light bubbles (Light, Love); on dark bubbles (Dark green,
  Samurai red) dark blue is invisible, so the deepest blue that clears 3:1 (`#5AB4FF`, `#80C8FF`).
  `BubbleTokensTest` enforces the contrast.
- Links: more TLDs (`shirt.online`, `.site`, `.store`, `.xyz`, country codes…); file names stay plain.
- Search matches word starts: "hey" finds "Hey!" and "heyy", not "they". `ChatSearchTest`.
- "Load older messages" only when the newest page was full (no button at the start of a short chat).
- A connection with no nickname asks "What would you like to call this person?" on entering the chat.
- Notifications off: a centred "Turn on notifications?" popup on entering the chat; "Turn on" opens
  the app's notification settings. Asked again every 3 days. `NotificationReminderTest`.
- Message notifications forget lines that were swiped away, read, or seen by opening the chat.
- Share sheet: One on One accepts text (goes into the draft), photos and the contract's document
  types (attachment preview; nothing is sent until the user taps send). Only `content://` streams.
- Calls: speaker button flips instantly (device switch off the main thread); volume keys set call
  volume during a call; remote audio gain 2.5×; keyboard closes when a call screen opens; the
  incoming screen rings and vibrates itself while the app is visible (notification posted silent then).
- Emergency alarm: tapping the alarm notification opens the chat without silencing. While their
  alarm is live the whole chat glows red with a red "Emergency alarm from X · Acknowledge" banner;
  acknowledging sends the contract ack, which stops the ring on every device.

The backend-dependent additions below supersede this batch's initial exclusions:
typing, edit/delete, shared pins, any-emoji reactions and server-side link previews.

Xiaomi checklist:
- [ ] Double-tap a message with the keyboard open: bar pops from the bubble, keyboard stays up.
- [ ] Bar colours match each wallpaper (off / love / samurai) in light and dark.
- [ ] Long press selects; tap others to add; count updates; copy, reply, Report work; Back clears.
- [ ] Long press a link and a phone number: copied, no selection. Tap opens. Double-tap reacts.
- [ ] Long press a reply bubble: selects. Tap its quote: jumps to the original.
- [ ] Swipe to reply: keyboard opens with the reply card above the composer.
- [ ] Read ticks visible on all four palettes.
- [ ] Send `shirt.online`: link. Search "hey" with a "they" message: not matched.
- [ ] New connection without nickname: prompt on entering chat.
- [ ] Notifications off: popup on entering chat, Turn on opens settings; not again for 3 days.
- [ ] Swipe away a message notification, receive another: only the new line shows.
- [ ] Share text, a photo and a PDF from Gmail/Chrome/Gallery into One on One.
- [ ] Call: audible on earpiece and speaker; volume keys change it; speaker toggles at once.
- [ ] Typing when a call arrives: keyboard closes; ringtone plays inside the app.
- [ ] Alarm received while in chat: red glow + banner, ring continues until Acknowledge.
- [ ] Alarm received in background: tap notification, chat opens glowing; Acknowledge stops it.

## Edit, delete, pin, typing, any-emoji reactions, link previews (2026-10-14, same branch)

Backend: web `main`, merged PR #100 (migration 036). Contract §5/§8 updated (copy here).
- Long press = select + quick reactions above the message; "+" opens the full emoji picker (androidx emoji2-emojipicker).
- Selection bar: reply · edit (own text, ≤30 min) · copy · delete for everyone (own, ≤30 min, with confirm) · ⋮ Pin/Unpin, Save, Report.
- Edited messages show "edited HH:mm"; deleted show "This message was deleted" for both. Tap a bubble for the full sent/edited date.
- Pinned banner under the header (tap jumps to it, × unpins); one pin per chat, newest replaces.
- Typing: header says "typing…" and a three-dot bubble bounces at the bottom; sent at most every 3 s.
- Link cards (server unfurl): wide art full width (YouTube, Spotify, Amazon), square art / site icon as a thumbnail.
- /location fixed: last fix from the past 2 min, else all providers raced for 20 s (GPS alone timed out indoors and nothing was sent), then any fix from the last 30 min.
- Composer pill 44 dp (was 48 + padding); reactions chips spring in/out; reaction bar animates out; Connection menu group, search bar,
  banners, reply/edit bar, slash menu and the header/selection swap all fade + fold instead of popping.
- Debug builds have a second launcher icon "1on1 Demo": the real chat UI against a scripted contact (types, replies, reacts; send "alarm"
  for an emergency alarm), with link cards produced by the real unfurler. Not in release builds.

Deploy order: run `036_message_edit_delete_pin.sql` in Supabase, then deploy the backend, then install this build.

Xiaomi checklist (after deploy):
- [ ] Long press: selects + reaction bar; "+" → picker; picked emoji shows on both phones.
- [ ] Edit within 30 min: both see the new text + "edited". After 30 min edit/delete hidden.
- [ ] Delete for everyone: both see "This message was deleted".
- [ ] Pin on A, then pin another on B: both show B's pin. × unpins on both.
- [ ] Typing on A shows "typing…" + dots on B, stops ~6 s after A stops.
- [ ] YouTube / Spotify / Instagram / Amazon links show cards on both phones.
- [ ] /location indoors sends a snapshot within ~20 s.


## Chat polish round 2 (2026-10-14, branch `feat/chat-polish-round2`, not committed)

Plan and task allocation: `docs/MASTER-PLAN-2026-10-14.md`. Android only; no backend change; no new dependency.
- Alarm: notification "Silence" now sends the contract ack (queued until a chat session exists, only while the raise is
  still Live), so the chat doesn't ask again. While the chat is open the ring's notification is quiet (low-importance
  channel, no full-screen) and the red banner is the ack UI. Backup notification (service start refused in background)
  is INSISTENT: before, its channel sound played once — the "rings a few seconds then stops" bug. Opening it from the
  full-screen intent promotes it to the real looping ring. `AlarmPolicyTest` covers which silenced acks are sent.
- Notifications: message pushes are shown inside `onMessageReceived` (≤8 s) instead of always through WorkManager, which
  Xiaomi/Doze defer; the worker stays as the retry path.
- Delete: failed / unknown unsent bubbles (text or media) can be deleted locally ("Delete", only this phone). Server
  delete for media already removes the file (contract §8); the 30-minute rule is unchanged.
- Composer: emoji (left, inserts into the draft), paperclip + camera (right; camera hides while typing), animated
  send↔mic swap with the arrow rotating in. Camera uses TakePicture into cache/shared → same preview-and-send as a photo.
- Reactions: the full picker sheet moved from the lazy row to ChatScreen (a row recomposing/leaving composition lost
  it) and reads the latest callback.
- Bubbles: animated tick → double → read; deleted swap fades; WhatsApp-style curved tail, also on the typing bubble;
  reply time/ticks pinned bottom-right when the quote is wider than the text.
- Voice: play/pause icon crossfade, smoothed progress, indeterminate bar while uploading.
- Location card: Directions left / View map right, label no longer touches the edge.
- GIFs: coil-gif decoder registered — GIF photos (picked from the gallery, `image/gif`) now animate for both sides.
- Settings: Chat text size (S/M/L/XL, 0.9–1.3×), device-wide, applied to the chat screen only. `ChatTextScaleTest`.
- Their photo (menu → Set their photo): private avatar for the other person, stored only on this phone (512 px JPEG,
  `files/partner_photos`, leftovers of other connections deleted). `PartnerPhotoTest`.

Not done (see master plan): GIF search, sticker maker/library, your own profile photo (backend gap), list smoothness audit.

Xiaomi checklist:
- [ ] Alarm, app in background: tap Silence → ring stops at once; sender sees "acknowledged" (immediately if the app process is alive, else on next open); opening the chat shows no Acknowledge banner.
- [ ] Alarm with the chat open: rings, no heads-up/full-screen, banner Acknowledge stops it on both.
- [ ] Alarm with app swiped away + battery saver: rings continuously (not a few seconds); full-screen tap keeps ringing.
- [ ] Message from B while A is in another app / screen off: notification within seconds.
- [ ] Composer: emoji left, clip + camera right, camera hides when typing; send/mic swap animates.
- [ ] Camera: first use asks permission, photo shows preview, sends; deny shows the message.
- [ ] Long press → + → pick any emoji: reaction appears on both phones.
- [ ] Ticks animate sent → delivered → read. Voice: icon morphs, bar moves smoothly; uploading shows moving bar.
- [ ] Reply with a long quote and short text: time + ticks bottom-right.
- [ ] Location card: buttons spaced left/right; label not cramped.
- [ ] Send a GIF from Gallery: animates on both phones; delete it within 30 min: gone for both.
- [ ] Turn airplane mode on, send a photo, let it fail; select it → Delete removes it.
- [ ] Settings → Chat text size XL: bubbles, times, reply previews, composer readable, nothing clipped.
- [ ] Menu → Set their photo: header shows it; other phone unchanged; Remove restores the initial.

## GIPHY + profile photo (2026-10-14, same branch; backend branch `feat/profile-photo-gif`)

Backend (web repo, worktree `../One-on-One-pfp-gif`): migration 037 (`users.avatar_path`), `PUT/DELETE /api/me/avatar`,
`GET /api/me/avatars`, `GET /api/gifs`, `POST /api/connections/:id/gifs`; `CurrentConnection.myAvatarId/otherAvatarId`.
Contract §9 (copied here).
- Composer emoji sheet has Emoji | GIFs tabs. GIFs: trending, search (350 ms debounce), infinite scroll, "Powered by GIPHY".
  Tap sends: the server fetches the GIF from GIPHY and stores it as this chat's attachment, then a normal image message.
- Settings → Your photo: add / change / remove (downscaled 512 px JPEG, EXIF-rotated, metadata dropped). Seen only by you and
  your live connection. Chat header: your private photo for them → their photo → initial.
- Call screen shows the same photo as the chat header (private one → theirs → initial).
- Not done: web client avatars/GIF picker (owner: leave the web client for now).

Deploy order: run `037_user_avatar.sql` in Supabase → set `GIPHY_API_KEY` on Railway → deploy the backend → install the app.
(Backend without the migration keeps working: photos are just absent.)

Emulator/Xiaomi checklist:
- [ ] Settings → Your photo → Add: shows in Settings; other phone's chat header shows it within one refresh.
- [ ] Set their photo (menu) on A: A sees the private one, not B's real photo; Remove → B's real photo returns.
- [ ] Remove your photo: other phone falls back to the initial.
- [ ] Emoji sheet → GIFs: trending loads, search "cat" works, scroll loads more, tap sends; both phones animate it.
- [ ] GIF while replying: sent as a reply. Delete it within 30 min: gone for both.
- [ ] Without GIPHY_API_KEY: GIFs tab shows "Couldn't load GIFs…", no crash.
- [ ] Voice and video call (ringing, connected, audio-only): the partner's photo fills the avatar circle.

## Sticker maker + library (2026-10-14, branch feat/stickers; Prompt 1 only)

Based on main 002b1d8, Chat polish round 2. The sibling repository is read-only.

- Emoji sheet now has Emoji, GIFs and Stickers. The + tile opens PickVisualMedia;
  the maker shows progress, Original / Cut-out, square drag/pinch crop, a
  checkerboard preview, accessible crop actions, Reset and Save.
- On-device CPU background removal uses MediaPipe Tasks Vision 1.1.0 and a
  bundled DeepLab-v3 model. No runtime model download. The comparison, scope
  limitations, Apache-2.0 licence, attribution and model checksum are in ARCHITECTURE.
  Missing subjects and segmentation failures offer Original.
- Every saved sticker is 512×512 WebP with alpha. Encoding tries quality 100,
  95, 90, 85 and 80 until ≤100000 bytes; if still too large, Save asks for a
  tighter crop rather than dropping quality below 80.
- Each account's files and atomic JSON index live under filesDir/stickers/ownerId.
  Favourites, last 16 Recent, My stickers and the original six-drawing Little
  signals pack persist across restarts. Hold a tile to Favourite/Unfavourite or
  Delete with confirmation. Deleted built-ins stay deleted.
- Successful sign-out and account deletion clear index and files under the
  shared account mutation lock. Failed unregistration/deletion preserves the
  library. Restored draft URI, mode and normalized crop are scoped to the owner.
- Tap sends through FeatureViewModel → MediaGateway attachment upload →
  MessageService → Transport → InternetTransport. It is the contract's normal
  image/webp image payload, including width/height 512 and optional replyTo.
  Received images and delete-for-everyone keep existing rendering and rules.

Verification: ./gradlew assembleDebug lintDebug testDebugUnitTest passed on the
final code (123 JVM tests, zero failures/errors, zero lint issues). Checks cover
crop/pinch/pan boundaries, 512 scaling, WebP cap/quality retries, alpha thresholds,
favourite/recent ordering, durable per-owner index/deletion, failed-account-action
preservation and a late upload after the conversation changes. Native bitmap
ownership has a runtime assert: MPImage may recycle only its private copy.
./gradlew assembleRelease bundleRelease lintRelease also passed. The native
checker self-test and final unsigned APK/AAB checks passed for all eight 64-bit
libraries; SDK zipalign -c -P 16 4 passed. No signing identity or version changes.

Universal debug APK, identical local configuration/all ABIs: main 002b1d8 was
67,544,688 bytes; this change is 133,462,489 bytes. Delta +65,917,801 bytes
(+62.86 MiB), mainly MediaPipe's four native ABIs plus the bundled model.
This is not a Play per-device download-size measurement.

API 36 emulator, 360dp, scripted Demo account: third tab, native photo picker,
offline portrait cut-out, Original/Cut-out switching and Save exercised.
Long-press Favourite updated the index and survived a force-stop/relaunch.
The saved file was independently decoded: WebP, 512×512, RGBA alpha 0–255,
90,464 bytes. Original remained valid after segmentation. The supplied portrait
fixture showed some remaining background objects/soft edges; this is a coarse
VOC model, not a measured quality match for WhatsApp or ML Kit.
The SDK ownership fix was found while inspecting the pinned SDK's bytecode.
During an earlier run alongside R8, both System UI and the app reported input
ANRs with high emulator CPU pressure. This run is not a hardware latency claim.
No authenticated upload, second-client receive/delete, physical Xiaomi, complete
light/XL/TalkBack, real process-death draft, pet/object quality, API 26–29 or
16 KB runtime verification is claimed.

Screenshots: [maker](design/screens/stickers-maker-dark-360.png),
[library](design/screens/stickers-library-dark-360.png). The portrait input is the
[MediaPipe test fixture](https://storage.googleapis.com/mediapipe-assets/portrait.jpg);
test photos and APKs are not committed.

### Emulator / Xiaomi HyperOS checklist (owner)

- [ ] Emoji → Stickers at 360dp, dark/light, chat text size S/M/L/XL, large
  system fonts and TalkBack: all tabs, tiles, crop actions and buttons reachable.
- [ ] + → photo picker: cancel, portrait JPEG with EXIF rotation/mirroring,
  PNG with alpha, landscape and a large photo; no camera/storage permission added.
- [ ] With networking off before first use, make cut-outs of a person, cat/dog
  and a supported object. Inspect hair/fur and edges; note latency and memory.
- [ ] Blank image / unrecognized object → friendly no-subject notice; Original
  remains usable. Switch modes; pinch/drag/reset inside the square; Save.
- [ ] Inspect saved WebP: 512×512, alpha present, ≤100000 bytes; a detailed crop
  retries down to 80 or explains the cap. Do not accept a silently oversized file.
- [ ] Rotate while picking, segmenting and cropping; background/relaunch or
  recreate the process with a draft open; crop and mode restore or an expired
  picker grant produces a friendly message. Saving succeeds once.
- [ ] Save several stickers; restart; Favourite/Unfavourite; send more than 16
  distinct stickers and check Recent. Delete/cancel; deleted built-ins do not return.
- [ ] In a disposable signed-in conversation, send a custom and built-in sticker,
  including a reply. Both phones/web receive an ordinary image with transparency.
  Upload error/offline retry does not send to a changed conversation.
- [ ] Delete a sent sticker for everyone within 30 minutes: both clients show
  the existing deleted-message state; library deletion does not alter sent messages.
- [ ] Sign out (including while saving), sign into another account: no old
  files, index, favourites or draft. Repeat after deleting a disposable account.
- [ ] API 26–29 legacy WebP encoding and a real 16 KB page-size device: inference,
  preview/save and native loading; real Xiaomi performance and edge quality.

### Owner review follow-ups (2026-10-14, Opus)

- Composer smiley → sticker icon; sheet is Stickers (default) | GIFs, emoji tab
  removed (keyboard has emoji). Slimmer 36dp pills inside 48dp targets, centred text.
  New camera icon. GIF/sticker send errors now show inside the sheet (the chat
  notice was hidden behind it).
- Built-in packs: "Cats" (5 original vector drawings: happy, love, sad, grumpy,
  sleepy) and Craftwork "Open Stickers" IT / Life / Nature / Shapes (80, owner holds a
  paid licence), pre-encoded offline to 512×512 WebP in `assets/stickers/packs`
  (+1.6 MB). Packs only seed when an account's index is first created.
  User-made stickers stay in "My stickers". Grid tiles have no checkerboard.
- Fix on main's code: link-preview card overlapped message text (round-2 deleted
  fade put bubble content in AnimatedContent's Box); now a Column.
- Emulator (demo, no backend): sheet, packs, maker, favourite, persistence and
  in-sheet error checked. Real send/receive still needs a signed-in account.
- Maker "Crop": free crop box over the whole photo (corner handles, drag to move,
  10% minimum, TalkBack actions); the cropped part is then placed with pinch/drag.
- Voice/sticker/GIF sends show an immediate "Sending…" bubble while uploading;
  text is no longer blocked by a media upload; composer ↔ recording bar slide/fade.
- Shared button fix: labels were top-aligned whenever a button was taller than its
  text (all 48dp maker buttons); `OneButton` now centres them app-wide. Other
  Surface/clickable labels audited: already centred.
- Stickers (512×512 WebP image payloads, no reply quote) render bare, 150dp, with a
  small bubble-coloured time+ticks pill hanging just under its corner. Photos sit
  in a thin 3dp frame with time+ticks on a dark scrim printed on the image. Both:
  bottom-right when sent, bottom-left when received (like text bubbles). The
  sticker "Sending…" placeholder is bare too.
- Debug demo: uploads now stay on the device (`DemoMedia`) and Alex echoes images
  back, so photos/stickers/voice render offline; both layouts checked on the emulator.
  Fixed a stuck fade-out ghost of the removed "Sending…" item (dropped `animateItem`).
- All chat times are 12-hour with AM/PM (bubbles, tap-for-detail, last seen,
  countdown cards), in the phone's time zone.

## Owner feedback round (2026-10-19, 2.0.5, branch `fix/owner-feedback-1019`)

- Re-sign-in into the same account skips date of birth and ground rules (gates keyed to the
  auth user; sign-out keeps them; another account / re-created account is still asked).
- Composer "Message" vertically centred (line-height centring + 1dp optical nudge for x-height);
  measured on emulator. Placeholder now alternates with "Try /letter", "Try /countdown"…
- Connection ID: "Copy" → "Share" (system share sheet) with Play Store link, web link and the ID.
  Tap/hold the ID card still copies.
- /letter: plain white full page, readable black cursive (Dancing Script), date, indented body,
  "With love" at the bottom-right; appearance picker removed. Download is a real PDF (A4).
- Long-press on photos, files and "Open letter" now selects the message (Delete appears); before,
  those tap targets swallowed the long press. Taps toggle while selecting.
- Double ringtone fixed: in-app ring silences the already-ringing call notification.
- Alarm: tapping Acknowledge stops the ring at once. "You acknowledged the alarm" row: text
  aligned with the quote, time/ticks at the bubble edge like text bubbles.
- Bubble times ~28% dimmer (ticks unchanged).
- Presence: clock-skew-proof live ageing, 1 s tick. Instant presence needs a backend socket
  `presence` event; [backend handoff prompt](prompts/backend-presence-prompt.md) preserved
  from the temporary scratchpad (proposal only; no DB/Supabase change needed).

Release handoff: [PR #27](https://github.com/aarahman04/One-on-One-Android/pull/27)
remains unmerged. Release workflow inputs are already **12 / 2.0.5**. README and
release checklist now match; the web repo is unchanged. Slash hints and handwritten
PDF export are Android-only; instant presence still needs the backend contract and
Android client integration described in the handoff prompt.

Handoff verification (2026-10-19): re-ran `gradlew.bat --no-daemon assembleDebug
lintDebug testDebugUnitTest` successfully (126 tests, zero failures/errors; lint
passed). Initial signed workflow [39192560039](https://github.com/aarahman04/One-on-One-Android/actions/runs/39192560039)
and PR checks [39192560395](https://github.com/aarahman04/One-on-One-Android/actions/runs/39192560395)
both succeeded. The documentation follow-up changes no app code; a fresh manual
release dispatch on its commit uses the same 12 / 2.0.5 inputs.

Verification: `./gradlew assembleDebug lintDebug testDebugUnitTest` green. Emulator (debug demo,
no backend): composer centring measured, hint rotation, letter preview/viewer, PDF saved and
rendered (1 A4 page), alarm ack row, long-press select on letter card. Not device-tested:
sign-in gates, share sheet targets, calls, real alarm FCM, presence.

### Xiaomi checklist
- [ ] Sign out, sign back in with the same Google account: lands in chat, no DOB/rules.
- [ ] Sign in with a different account on the same phone: DOB + rules asked.
- [ ] Composer "Message" looks centred; hints rotate; typing hides them.
- [ ] Connection screen Share opens apps; message has Play link, web link, ID.
- [ ] Send /letter; open on both phones; Download PDF opens in a PDF viewer, handwriting readable.
- [ ] Long-press a photo, a file and a letter you sent (<30 min): Delete removes it for both.
- [ ] App backgrounded, receive call, open full-screen call: only one ringtone.
- [ ] Receive alarm, tap Acknowledge: ring stops immediately; ack row layout.
- [ ] Partner opens/closes chat: Online/Last seen within ~15 s (instant after backend presence).

## Games rebuilt on server authority + speech cost rework (2026-10-20, 2.0.7, branch `claude/pensive-franklin-l096wx`)

Status: implemented; **build-verified only in part, not device-tested** (see Verification).

Backend (owner-authorised changes in `aarahman04/One-on-One`, same branch name, contract §11/§12):
authoritative `match:*` games (seats, server-side rules with chess.js, 60 Hz table tennis
simulation, reconnection, rematch, cleanup) and the speech service cost rework (Whisper
base.en, lazy load/unload, Serverless wake retry, daily quotas). Web client not changed: web
and Android players can't play each other until the web adopts §12.

Root causes found in the audit:
- Table tennis: the old relay did an async DB lookup per 30 Hz packet, so packets could be
  reordered; the strict `seq` check then ended the game ("out-of-sequence move"). Any relay
  hiccup also ended it. Both clients ran their own physics view of a host-owned ball.
- Chess: pieces were tinted "mine vs theirs" (accent colours), never white/black, so both
  players saw their own army in the same colour at the bottom; no move animation; colours
  and validation lived only on the clients.
- All games: no server validation, a restarted app ended the game, rematch didn't swap sides.

Android changes:
- `data/games`: `Match` (strict decoder), `Boards` (Chess/Grid/Dots views, sprite tracking),
  `PongClient` (render-only), `MatchSession` (pure, testable). Removed the client engines
  (`BoardGames`, `GameProtocol`) and the relay API from `Transport`.
- Chess: classic board, white/black pieces with outlines, "Playing as White/Black" chip,
  flipped for black, 200 ms slides + capture fade, legal dots/capture rings, last move and
  check highlights, file/rank labels, piece-picture promotion picker, offer/accept/decline/
  claim draw, captured rows, resign.
- Table tennis: portrait court, own paddle bottom, drag/tap control, server score, countdown,
  pause while backgrounded (`match:away`) or while the partner is away.
- Tic-tac-toe, Connect 4, Dots & Boxes: identity/turn text, last-move animation keyed to the
  server's `last`, consistent result screen with series score, rematch (accept partner's
  request), "Play something else". Hub closes when inviting; declined/expired invites show a notice.
- Speech: `SpeechApi` timeouts 95 s read / 125 s call (cold wake of the sleeping service);
  `quota_exceeded` falls back to the device voice/recognizer like 503/504.

Verification (what actually ran):
- This container has no Android SDK and Google Maven (`dl.google.com`) is blocked, so
  `./gradlew assembleDebug lintDebug testDebugUnitTest` **could not run**. Instead:
  - JVM harness: `Match.kt`, `Boards.kt`, `PongGame.kt`, `MatchSession.kt` compiled with
    Kotlin 2.4.20 + chesslib 1.3.7 (built from source) and their unit tests run:
    **14/14 pass** (`MatchTest`, `PongClientTest`, `MatchSessionTest`).
  - Compose type-check: the real `GameBoards.kt`, `GameHost.kt`, `GameVectors.kt`,
    `GameViewModel.kt`, `GameService.kt` compiled against Compose Multiplatform 1.6.11 with
    stubs for app-only symbols (theme, buttons, R, Coil, lifecycle): **compiles, no warnings**.
    Not a substitute for AGP + lint on the real Compose BOM.
  - `GameViewModelTest` and the `SpeechTest` additions were rewritten/extended but **not run**.
- Backend: 75/75 tests incl. two real Socket.IO clients playing chess and table tennis.
- Speech service: 7/7 tests with the real models; end-to-end HTTP benchmark (docs/SPEECH-SERVICE.md).
- Two phones were **not** used; no device testing.

### Xiaomi checklist (two phones, both on this build, backend from this branch deployed)
- [ ] Start chess from each phone: one shows "Playing as White", the other "Playing as Black";
      each sees its own pieces at the bottom; white moves first.
- [ ] Moves slide (~0.2 s), captures fade, castling moves the rook, en passant removes the pawn,
      promotion shows the four pieces; illegal taps never move anything.
- [ ] Check highlight; checkmate/stalemate end the game on both phones with the same result.
- [ ] Offer draw → partner sees Accept/Decline; repeat a position 3x → "Claim draw" appears for the side to move.
- [ ] Turn Wi-Fi off mid-chess for 30 s, back on: same board, same colours, game continues.
- [ ] Rematch from both: colours swap; series score shows.
- [ ] Table tennis: both phones show the same score; ball moves smoothly; paddle follows the
      finger; ball resets after a point; first to 7 ends both.
- [ ] Table tennis: background one app → both pause; return → 3 s countdown, play resumes.
- [ ] Tic-tac-toe, Connect 4, Dots & Boxes: turn text correct, last move animates, result + rematch.
- [ ] Partner offline: inviting shows "Games need … to be online."
- [ ] With `SERVER_SPEECH=true` and the speech service deployed: first letter read after
      ~15 min idle waits (spinner) then plays in the natural voice; dictation inserts text, never sends.

## Chess polish (chess.com-style) and table tennis rebuild (2026-10-20, 2.0.7, same branch)

Reference: the owner's table tennis screenshot (no video arrived), used for layout and mechanics only;
colours and type follow One on One.

Table tennis (backend `games/pong.ts` + Android `PongUi.kt`, `PongGame.kt`):
- Vertical table 1 : 1.8, centre line and net; round racket heads centred on each end line with short
  handles outside the table (own racket always at the bottom, opponent's rotated to the top).
- Server physics: circle contact — centre hit straight back, edge hit angled up to 62°, plus 30 % of the
  racket's sideways swing; substeps shorter than the ball radius with an overlap test (no tunnelling,
  tested at max speed with 50 ms frames); side edges bounce; past the end line = point; serve to the
  loser; first to 7. Assumptions in place of the missing video are written in contract §12.
- Client: drag anywhere (no scroll), cosmetic ball height arc + shadow and a bounce mark on the far
  half, ball held on the racket surface until the server rules, scoreboard animates only on change,
  countdown chip, pause banner, haptics on hits and points. Canvas redraws per frame; nothing else does.

Chess (`ChessUi.kt`, `GameSounds.kt`, `Boards.kt`):
- Original rounded Staunton pieces drawn for the app (owner's reference style; `docs/design/chess-pieces/`) on the green board, chess.com-style yellow last-move/selection, check
  glow, dot/ring hints, white hover outline while dragging, lifted 1.2x piece above the finger.
- Your move appears instantly (predicted with chesslib, confirmed or reverted by the server); 170 ms
  slides for everything else, capture fade, castling/en passant/promotion tracked.
- Promotion strip over the file; player bars with captured pieces and material lead; scrolling SAN
  move list (backend adds `history`); synthesized move/capture/check/end sounds; finished games keep
  the final position under a result card (rematch / new game / done).

Verification: backend 82/82 (incl. two real Socket.IO bots that rally 9 s without a miss, then a 7-0
when one stops, and a rematch). Android: data-layer harness 16/16; real UI composables type-check on
Compose 1.6.11 with no warnings and were rendered to PNG (dark/light chess, black's view, result,
table tennis rally and countdown). `./gradlew` still not runnable here; no device test.

### Xiaomi checklist (additions)
- [ ] Chess: your move lands the instant you release; opponent's move slides; sounds play once.
- [ ] Drag a piece: it lifts above your finger, the target square outlines; illegal drop snaps back.
- [ ] Promote: the four-piece strip appears over the file; tapping outside cancels.
- [ ] Table tennis: drag anywhere moves your racket without scrolling; edge hits angle the ball;
      swinging while hitting curves it; a miss scores for the other side; ball resets and serves to you.
- [ ] Both phones show the same score through a full game to 7; rematch swaps ends.

## 2.0.7 release prep — natural voice on (2026-10-20)

- Owner configured the `speech` Railway service and the backend's `SPEECH_SERVICE_URL` /
  `SPEECH_SERVICE_TOKEN`. The release job now builds with `SERVER_SPEECH=true` (public
  client flag only; no secret). If the service is down or over quota (503/504/429
  `quota_exceeded`, network error) the app still falls back to the device voice/recognizer.
- `android-build` dispatch defaults bumped to **versionCode 14 / 2.0.7** (13 was used for 2.0.6).
- [ ] Phone: play a letter (first one after idle wakes the service, can take ~1 min), dictate a phrase.

## Chess: smoother piece motion (2026-10-21, branch `claude/chess-smooth-moves`)

Android only (`ui/games/ChessUi.kt`); board, pieces, highlights, sounds and rules unchanged.
- Slide time scales with distance (150 ms for a step up to 280 ms for a long diagonal) instead of a
  fixed 170 ms, with the theme's standard easing.
- A dropped piece glides from under the finger onto its square (spring, no overshoot) instead of
  jumping; an illegal or cancelled drop glides back home instead of teleporting.
- Lift scale (1.2x) springs in and out instead of popping.
- Pieces are placed by an `offset {}` lambda (placement only, no recomposition per frame).
  (2026-10-21 emulator round: the `graphicsLayer` translation was reverted, see below.)
- A captured piece fades over the attacker's travel time, so it disappears as the attacker lands.

Verification: `./gradlew assembleDebug lintDebug testDebugUnitTest` passed; new `ChessMotionTest`.
Not device-tested.

### Xiaomi checklist
- [ ] Opponent's pawn step looks quick, a long bishop/queen move glides smoothly end to end.
- [ ] Drag and drop on a legal square: the piece settles from your finger, no jump.
- [ ] Drop on an illegal square or outside the board: the piece glides back to where it started.
- [ ] Capture: the taken piece fades out as the attacker arrives; castling slides king and rook together.
- [ ] Promotion by drag: piece returns to its square while the picker shows, then slides after picking.

## Games round: sidespin, Reversi, Connect 4 / tic-tac-toe polish (2026-10-21, branch `claude/games-spin-reversi-android`)

Backend (merged, aarahman04/One-on-One#119): table tennis sidespin (`w` in `match:pong`), Reversi
rules (`reversi` match game), contract §12. Backend tests 88/88.

Android:
- Table tennis: `PongClient` reads optional `w`, curves the extrapolated ball, rolls a seam on the
  ball with the spin (`PongUi`). Older servers (no `w`) behave as before.
- Reversi (`ReversiView`, `ReversiBoard`): green board like chess, black/white discs, legal-move dots,
  new disc drops in, flips turn edge-on in a wave from it, pass notice in the status line, disc
  counts, results "32–30 discs" / "Equal discs" (a draw). Replaces Dots & Boxes in the hub.
- Connect 4: drop time follows free fall (≈200 ms from one row, ≈360 ms from six), small landing
  hop, winning ring fades in after the disc lands. Tic-tac-toe: win line strikes after the last mark.
- Reference APK: Unity IL2CPP base split without native libraries; only names recoverable. REA MCP
  Android tools need a jadx-headless-mcp JAR (`REA_JADX_MCP_JAR`), not configured. Details:
  `docs/games/GAME-INVENTORY.md`.

Verification: `./gradlew assembleDebug lintDebug testDebugUnitTest` passed (new `PongClientTest`
spin, `MatchTest` Reversi, `GameMotionTest`). Not device-tested; two-phone play not tested.

### Xiaomi checklist (two phones on this build)
- [ ] Table tennis: swipe sideways while hitting: the ball curves in flight and its seam spins; a
      plain block flies straight; a curving ball that hits the side wall goes straight after.
- [ ] Hub shows Reversi, not Dots & Boxes. Start Reversi: seat 1 is Black and moves first;
      dots show your legal squares; tapping one drops your disc and flips a wave of discs.
- [ ] Reversi pass: when one side has no move the status says so and the other plays again.
- [ ] Reversi end: board full or no moves for either side: result shows disc count; rematch swaps colours.
- [ ] Connect 4: a disc dropped into an empty column falls visibly longer than one onto a high stack,
      lands with a small hop; the winning four get rings after the last disc lands.
- [ ] Tic-tac-toe: the winning line draws after the final mark.

## Reference APK analysed with JADX + Unity data; emulator game testing (2026-10-21, branch `claude/games-emulator-fixes`)

Reference APK (`com.JindoBlu.OfflineGames` 3.15.3): JADX 1.5.6 decompiled the DEX; it holds only the
Unity bootstrap (`UnityPlayer.loadLibrary`), ads/analytics SDKs and a small `com.junglefrog.shared`
activity. The games are C# compiled by IL2CPP into `libil2cpp.so`, which this file does not contain:
the manifest has `requiredSplitTypes="base__abi"` and there is no `lib/`; installing it on an emulator
fails with `INSTALL_FAILED_MISSING_SPLIT`. What could be recovered without code: class/field names
(IL2CPP metadata v31) and the scene's serialized values and geometry (`data.unity3d`, read with UnityPy
in the external workspace). Details and confidence: `docs/games/GAME-MECHANICS.md`.

Emulator testing (OneOnOne_D3 AVD, API 36, headless): the debug build gained a debug-only local
stand-in for the server's matches (`app/src/debug/.../DemoMatches.kt`, used by the demo chat) so the
real game screens can be played without accounts. All five games were played on the emulator.

Bugs found on the emulator and fixed (Android only):
- Chess: on drop, the piece was drawn back on its old square for a frame (several on the slow
  emulator) before gliding; the released position is now used from the first frame.
- Connect 4, tic-tac-toe, Reversi: the first frame of a new move showed the finished state (disc
  already landed / discs already flipped), then the animation restarted from zero. Animation state is
  now created per move, so it starts at zero on its first frame.
- Table tennis footer text wrapped "Leave game" onto two lines; shortened.

Verification: `./gradlew assembleDebug lintDebug testDebugUnitTest` passed. Emulator: played, with
screenshots/recordings in the external workspace (`evidence/emulator`). Real two-player play was not
possible on the emulator (needs two signed-in accounts); the stand-in is not the server.

### Xiaomi checklist
- [ ] Chess: drag a piece and let go: it glides from your finger to the square, never back first.
- [ ] Connect 4 / Reversi / tic-tac-toe: a new disc or mark never flashes in its final place before animating.

## Ping Pong rebuild and playability tuning (2026-10-21, `codex/pong-native-mechanics`)

Owner rejected the previous flat wall-bouncing game and approved a fresh replacement using the supplied
video and recovered native mechanics. Display name is Ping Pong, with an original racket-and-ball icon
in `/games` and result screens. No other game implementation or web-client file was changed.

- Rackets move across their own half in two dimensions, including outside the table. Gesture direction
  aims the shot; power adjusts depth and duration. Bat tilt follows the stroke up to 55 degrees and
  eases back; this movement was retained after owner feedback.
- Server sends height-bearing cubic flights, table bounces and serve state. Sides and corners are open.
  Serving waits for contact after the countdown; service alternates every two points; first to seven.
- Owner requested further improvements beyond the reference: angled shots now have more landing room,
  duration is .88..1.06 s, and gentle placements stay spin-free. A stronger sideways brush transfers
  persistent spin to the ball (light .1 / hard .2 after owner feedback). It curves gently in air and receives a lateral kick on the table bounce.
- Contact resolves outside the drawn racket along the outgoing shot; confirmed hits snap promptly
  rather than blending through the bat. The owner accepted the current speed; it was retained.
- Canvas displays actual height, shadow, connected trail and a 300 ms contact pulse. Prediction is capped
  at 150 ms. Removed the old side reflections, cosmetic height and artificial hold at the racket.
- Demo opponent holds its baseline until countdown zero, avoiding the blue-bat/ball overlap. It blocks
  at the baseline rather than retreating from the ball. Its simulation advances by elapsed time.
- Release/cancel uses the existing 33 ms throttle; lifecycle transitions discard queued input. Backend
  validates optional y/released and rejects paused input. Existing protocol fields/types are retained.

Validation: `npm test` **94/94**, TypeScript check passed. Android
`assembleDebug lintDebug testDebugUnitTest` passed, **214 tests**. Socket fixtures cover two clients'
shared state, rallies, scoring and rematches; these are not two independently signed-in Android users.

Visible emulator evidence and the final play round are recorded in the external `SESSION-RESULTS.md`.
The earlier real-touch round confirmed zero-spin blocks and a rotating brush return with nonzero spin;
its recording is `evidence/emulator/pingpong-spin-test.mp4`. It predates the final gentler tuning.
Reference installation ran only in a disposable account-free isolated AVD; both packages opened menus,
but Ping Pong was not reached in the bounded attempt. Supplied video is owner evidence, not our recording.

Previous installed iteration: real-touch exercise recorded as `evidence/emulator/pingpong-final-play.mp4`,
with 1,181 authoritative demo frames in 40 s. Observed neutral blocks, rotated brush hits, table
landings with retained spin, open-edge travel and scoring. Contact-frame inspection exposed the remaining
forward-stroke/back-face overlap and led to the final front-contact guard and regression tests.
This round used the preceding .45 cap/.6 bounce tuning; the final front-face/.1-.2 spin build supersedes it; the accepted speed and bat movement were retained. Unit
coverage checks surface clearance through the first 80 ms, plus immediate confirmed-contact display.
Earlier completion/rematch rounds reached 7-1 and 0-7 and reset to 0-0 with seats swapped.

Emulator remains visible (OneOnOne_D3, API 36, emulator-5556). At 720x1600/density280, with Gradle
stopped and recording off, a 46.5 s steady sample rendered 2,741 frames (about 59/s) with 1.50% modern
frame-deadline misses. This is emulator calibration, not a physical-device benchmark; earlier 1080
recording while compiling had poor frame timing. Original display can be restored with `wm size reset`
and `wm density reset`. No physical phone or personal account was changed. Scripted input was stopped when it became clear it was
competing with the owner's control. Subsequent inspection is read-only while the owner plays.

Wrap-up requested by owner: latest gameplay APK reinstalled at 08:59:58 IST; installed/local SHA-256
`c8701aaa1b2203098c4a996e274a3e08d7304e452597e9a65622a8a014ce0962` matches. Backend PR 121
merged as `3d0c148`, Railway deployment succeeded and health passed. Android PR 43 remains open:
gameplay `ca63c3e`; CI release packaging exhausted heap in run `39538900347`. Fix `84c12b5`
splits release Gradle invocations and scopes demo tests to debug; retry `39540116953` was pending
at the last check. Work stopped for Claude Code to follow up. Final front-edge/spin feel, physical
devices and genuine two-account Android play remain unverified; see the linked handoff.

### Physical-device and real multiplayer checklist

- [ ] Two independently authenticated Android accounts: invite/accept, both seats, identical score/result.
- [ ] Xiaomi and another phone: slow/fast strokes, diagonal placement, left/right brushing and bounce kick.
- [ ] Serve: countdown is separate from the bat, contact starts play, every two points changes server.
- [ ] Open corner exits continue outward; a return is accepted only after a table landing.
- [ ] Variable network latency: smooth bounded prediction, no artificial wall/hold or duplicate bounce.
- [ ] Background/resume pauses both sides and preserves score; rematch resets and swaps seats.
- [ ] Physical frame timing, touch calibration and haptics; emulator numbers are not a phone benchmark.

## Games round 3 (2026-10-21, `claude/games-round-3`, supersedes PR #43)

- Chess: fixed the move preview flashing the starting position (`ChessView.played` loaded the new
  Board's own FEN through the `apply` receiver); parsed view cached per server board so the slide
  animation no longer stutters; classic black-and-white board (Reversi keeps green felt).
- Clocks for every turn game (backend PR #122): presets No limit / 1 / 2 / 5 / 10 min / 1 hour, or
  custom time each (10 s..3 h) plus a per-move bonus (0..60 s). Defaults: chess 10 min, Reversi 5,
  Connect 4 2, Tic-Tac-Toe 1 (No limit one tap away). Server decides `timeout`; clients count down.
- Ping Pong: target score (7 / 11 / 21 / custom 1..99); relative racket drag (no jump on touch, the
  finger can steer from below); depth controlled by swipe speed from short to the end line, only a
  smash goes long; jitter-resistant stroke window; capped bounce spin (no sideways exploit near the
  net) plus a rare razor spin with a hot trail; hit/bounce sounds; app-accent table, bats and ball.
- Tic-Tac-Toe and Connect 4 draw your own move instantly; board taps no longer flash a white box.
- Owner playtest follow-ups: chess squares jet black (no piece outlines). Ping Pong spin made clearly
  visible and physically correct: it hooks against the brush (so wide spin shots bend back onto the
  table), bends 8-14% of the table width in flight and turns up to ~29 deg at the bounce; razor ~46 deg.
- Rematch when the partner left: closing the results or losing the connection there lists you in
  `away`; the partner sees "<name> has left the game" instead of a rematch that can never be answered.
- Verified: backend 103/103 + tsc; Android 216 unit tests, assembleDebug, lintDebug; emulator
  demo screens checked; owner played chess and Ping Pong on the emulator demo. Not verified: physical phones, real two-account play, real network latency.

### Xiaomi / two-phone checklist (games round 3)

- [ ] Chess on both phones: move a pawn two squares mid-game; the board never flashes back.
- [ ] Each clock preset and a custom clock; the side to move counts down on both phones; running
      out shows "ran out of time" on both; No limit shows no clocks.
- [ ] Ping Pong first to 11 and a custom target; scoreboard and result show the target.
- [ ] Ping Pong strokes: soft touch lands short, strong swipe lands deep, smash goes long; diagonal
      reaches near the corners; brushed spin bends and turns at the bounce, never straight sideways.
- [ ] Racket never jumps when touching the screen; hit and bounce sounds play at media volume.
- [ ] Tic-Tac-Toe / Connect 4: your mark appears immediately; no white box on tap.
- [ ] Finish a game, close the results on one phone (or turn it off): the other shows "has left the
      game" and no rematch button; reopening the results restores the rematch.


## Owner fix batch (2026-10-21, `claude/fix-batch-2026-10-21`)

The approved handoff resumes three unbuilt Android snapshots and two backend batches. This is the native Kotlin/Compose app, not the older web wrapper. Games (issues 10/11) are excluded: all Android game edits and invite-timeout changes from the snapshot are restored to main; backend commit `56522fb` is not integrated. The web client and speech service are read-only.

- **1-3:** prefer Android's on-device recognizer when available, otherwise its installed recognition service. Restart segments after silence/timeout/no match, append finals and partials directly after the original draft, cap the field at 4000 characters, retain the latest partial on Stop/background, and guard old session/segment callbacks. API 33+ requests quality formatting. The Stop-only UI replaces the discard control. Server fallback for phones without a recognizer records at most 118 seconds and transcribes at the cap.
- **4-5:** letter mic glyph is 16 dp with a 40 dp touch target centred on its label. Large body field and 4000-character limit retained.
- **6-8:** preview has device-voice read-aloud and stops on Edit/Send/close/background. Device TTS is shared, lazy and warmed when a viewer/preview opens; ordinary release stops rather than destroying the engine. The backend's first narration part is short. From chooses `myNickname`, then saved signature, then `me`; To keeps `otherNickname`. No punctuation-removing transform was found in letter send/render paths.
- **9:** avatars and chat photos decode raw pixels with BitmapFactory, apply one explicit EXIF matrix for all eight orientations and re-encode without EXIF. The production stored file could not be inspected in the prior investigation, so the original upside-down root cause remains unproven; the owner must re-upload their avatar.
- **12:** hangup/error/drop reconnect before `call:end`; media failure resolves from a sibling job so cancelling the media collector cannot cancel its own end request. Device teardown failure cannot skip server resolution. Backend resolves a participant's last-socket loss after 30 seconds, handles reconnect/timer races and keeps repeated accepts from resetting call duration. The production missing-card cause remains likely, not proven against production data.
- **13:** new `/checkin` creation is removed from Android command suggestions/composer/previews and rejected by the backend; historical cards, push decoding, payload handling and exports remain.
- **14-19:** payload-shaped photo placeholders/spinners and synchronously seeded signed-URL cache; attachment-path Coil keys; GIF long side capped at 200 dp; one emoji grapheme uses large bubble-free presentation and the existing receipt/time pill; latest arrow appears around 1.5 screen heights and smoothly re-pins; header avatar opens full-screen with the same cache identity; photo sheet closes immediately with a local pending preview while images downscale to 2048 px/JPEG 85 (transparent PNG/GIF preserved).
- **20:** safe fixes in the touched paths include stale recognizer callbacks, latest-partial preservation, synchronous TTS failure state, media-collector cancellation, native teardown errors, late invite/accept acknowledgements reviving a finished call, early accepted/ended events arriving before invite acknowledgements, and backend call timer/duration races. No broad refactor, new dependency, database migration, server thumbnail service or speech-service implementation change.

### Verification and delivery

`gradlew.bat assembleDebug lintDebug testDebugUnitTest --console=plain --max-workers=1` passed on the integration branch: **237 tests, zero failures/errors/skips**, assembleDebug and lintDebug successful. Backend `npm test` passed **116/116**, and `npx tsc --noEmit` passed. The first Android run exposed a stale nine-command fixture after check-in removal; the fixture was corrected and the full suite rerun. The owner changed issue 16 during verification: **only one emoji grapheme** gets the large bare style; multiple emoji use normal bubbles. Device-only behavior is deliberately not claimed. Backend API authority is `../One-on-One/docs/API-CONTRACT.md`; it documents the new nullable nickname, first-part narration and call/check-in rules. The read-only web command and letter defaults need the separate follow-up recorded in the final report.

See [Xiaomi / HyperOS checklist](XIAOMI-FIX-BATCH-2026-10-21.md) for recognizer duration and permissions, read-aloud latency/lifecycle, re-uploaded EXIF photos, real calls/reconnects, old check-ins, image caching/compression/upload, emoji, avatar viewer and scroll pinning. No new migrations are needed (latest remains `040_message_type_game.sql`).


Emulator: OneOnOne_D3 (API 36, emulator-5554) booted after an initial disk-space failure; a temporary read-only launch with a 1024 MB partition override worked. A later stall was corrected by relaunching with 1024 MB RAM, two cores, software graphics and Vulkan disabled. The reviewed debug APK was installed without clearing app data. Confirmed header full-screen photo open/close, latest-arrow appearance/return, letter mic alignment/large field, preview punctuation and speaker Pause/Stop controls, and Edit stopping playback. Fake-device speech tests simulate eleven minutes; this is not a physical recognizer run. Screenshot evidence remains local under `captures/fix-batch-2026-10-21/`.

Final emulator UI checks also confirmed the single ZWJ emoji stays large and bare while the three-emoji sample uses a normal bubble; an uploaded local test photo and its scripted partner echo render at the payload's 3:2 aspect ratio with no loading text. The one-second fake pending-upload state was not captured, and demo uploads copy local bytes: this does not verify production resize bytes or real-network upload speed. No AndroidRuntime fatal crash appeared during the final demo launch. Backend PR: [#123](https://github.com/aarahman04/One-on-One/pull/123). All five handoff agent worktrees were removed after integration; the other worktrees remain untouched.

### Merged delivery and Claude Code cross-check handoff

- Android [PR #45](https://github.com/aarahman04/One-on-One-Android/pull/45) merged as `727ce59`; backend [PR #123](https://github.com/aarahman04/One-on-One/pull/123) merged as `d982a84`, following the owner's later merge authorization. Their trees exactly match tested heads `e671598` and `015791a` respectively.
- [Android CI 39580424551](https://github.com/aarahman04/One-on-One-Android/actions/runs/39580424551) passed debug build/lint/tests and the unsigned minified release/native-library/alignment checks. No signed Play release/upload was made.
- Read [the Claude Code cross-check handoff](prompts/claude-cross-check-fix-batch-2026-10-21.md) before further review. It records exact ranges, changed paths, evidence, latest owner decisions, web follow-ups, call edge cases to investigate, cleanup and outstanding phone checks. The earlier integration prompt is historical and must not be executed again.
- Operational snapshot at 2026-10-21 10:59 UTC: public backend `/health` returned 200, but Railway's reported active backend/speech deployments still referenced older `3d0c148`, not backend PR #123. [Railway status](https://status.railway.com/) reported a control-plane outage and paused new builds. Code merged is not evidence that rollout completed. Recheck/deploy the matching backend after platform recovery.
- The owner entered `SPEECH_MODEL_TTL=0`; application to a running speech deployment remains unverified. Source confirms 0 disables idle model unloading after first use, not Railway sleeping or platform outages. No new Supabase migration/SQL is required.
- This follow-up changes documentation only. Runtime tests were not rerun unnecessarily; the exact prior tested trees and results above remain the evidence. Physical-device checks and the production root causes remain open.

## Owner priority: logos, transparent photo viewer and BiRefNet (2026-10-21)

The owner then explicitly requested these before completing the handoff. This permits the two shared game-logo corrections despite the initial batch's exclusion of game edits: Ping Pong grip vertical and centred (the owner rejected the first angled draft), Tic-Tac-Toe O circular. Gameplay remains unchanged. The supplied WhatsApp reference clarifies photo/header viewers: dark translucent 94% scrim with faint underlying chat and white controls, replacing the first fully transparent draft. Document viewers keep their black background.

Sticker removal replaces the old class-limited DeepLab model with BiRefNet Lite (Swin-Tiny, general-use epoch 232), running locally through ONNX Runtime Android 1.24.3. The explicit automatic-removal action downloads the pinned Studio Ludens **512 px FP32 conversion, 192 MB**, once with progress, exact byte/hash verification and an atomic cache promotion. Every unique learned-tensor byte matches the upstream 1024 export. Photos remain on device; cached inference needs no network. Manual Erase/Restore is available without a model, even after AI failure. MediaPipe and the bundled DeepLab model are removed; both MIT contributor notices and JNI/R8 protection are included. This does not depend on Railway or Supabase.

Real Windows CPU inference with the downloaded official model passed on the public portrait fixture: 67.766 s inference, 507,730 fully transparent / 522,614 opaque / 18,232 soft-edge mask pixels. The resulting cut-out was composited and visually inspected. This is a desktop result, not a phone speed claim. An early 2.5 GB emulator run was killed for LOW_MEMORY after reaching roughly 1.5 GB RSS; it did not complete. That finding prompted a free-memory/low-RAM preflight and native termination on cancellation. The initial emulator model download also failed after about 275 KB; the complete host download passed the pinned hash. Fresh-download reliability and completed Android inference are tracked separately from unit/build results.

The 4 GB emulator retry of that **rejected 1024 model** reached 2,764,472 kB high-water RSS plus roughly 2 GB swap and remained unfinished after nearly five minutes. It was stopped; this is not a successful peak/performance result. The owner then explicitly requested no further emulator work. The selected **512 model** passed real Windows CPU inference in **10.156 s**, total setup/run 13.312 s, process peak working set **1,610,080,256 bytes**. Its portrait mask has 126,478 transparent / 129,988 opaque / 5,678 soft-edge pixels; foreground IoU versus the 1024 result at original dimensions is **0.996697**. Side-by-side composited output was visually inspected. This is one fixture and one desktop, not an Android/phone benchmark. The final 512 download/native run, reference-matched viewer and manual brush are intentionally left for device checks.

Manual workspace: Original stays untouched; Cut-out contains Auto/Crop/Erase/Restore, Undo/Redo/Reset icons, an offset touch dot/circle, and active-tool-only circle-size/distance sliders. Restore samples original pixels/alpha. Done flattens up to 32 strokes on a worker; Undo/Redo is scoped to the active brush session, and very long strokes are decimated within 1024 points. A bounded scrolling body keeps the footer reachable; Crop Cancel/back/X restores its entry trim and placement. No model/network is needed for manual editing.

Final source validation: `gradlew.bat assembleDebug lintDebug testDebugUnitTest --console=plain --max-workers=1` passed, **243 tests / 39 suites, zero failures/errors/skips**. Lint has no errors or warnings (one existing hint in untouched GameInvite). The debug APK's ten 64-bit native libraries pass LOAD/RELRO and ZIP alignment. Commits: `840d2c7` (logos/reference viewer), `18a7e9d` (model/manual workspace); no version/signing/backend change. The final handoff and Xiaomi checklist explicitly retain runtime checks because the owner requested skipping further emulator work. No new migrations are needed.

## Claude Code cross-check of merged fix batch (2026-10-21, `claude/cross-check-2026-10-21`)

Independent review of Android `492d283..53cccb8` (PRs #45/#46) and backend `9f4081a..d982a84` (PR #123) against the owner brief and the handoff decisions. Both PRs are merged; this supersedes the handoff's "open PR" status. No emulator was launched (owner instruction).

Fixed (clear, bounded, with regression tests):

- **Backend: late decline ended an answered call.** `declineCall` did not check state. A second callee device (or a killed app's never-dismissed ring notification, since accept sends no dismissal push) tapping Decline after another device answered emitted `call:ended declined` and tore down the live call. Decline of a connected call is now a no-op `{ok:true}`; contract updated. Test fails without the fix.
- **Backend: TURN credential fetch had no timeout.** `call:invite`/`call:accept` ack only after `getIceServers()`; a hung Cloudflare request outlived Android's 10 s ack timeout while server state had already moved on. Fetch now aborts after 5 s and falls back to STUN through the existing catch.
- **Android: Stop between recognizer segments waited 3 s.** After each pause the recognizer is destroyed and restarted 300 ms later; Stop in that gap had no recognizer to flush and fell through to the 3-second grace showing "Adding your words…". Stop now keeps the words at once when no segment is running.
- Stale `BigEmojiSize` comment (1/2/3 emoji sizes) corrected to the single-emoji rule.

Reviewed and kept (matches agreed behavior): call attempt ownership/late-ack/early-accepted/ended ordering, finish-then-end with socket wait, 30 s disconnect grace; one EXIF pass via BitmapFactory for all eight orientations; 2048 px/JPEG 85 with PNG alpha and GIF passthrough; payload-sized placeholders and path-keyed Coil caches; single-grapheme big emoji (bare `❤` deliberately counts, per tests); latest arrow; shared warmed TTS with per-player utterance prefixes; preview narration device-only; From = `myNickname` → signature → `me`; check-in creation removed with history retained; BiRefNet download integrity/cancellation/memory guard and brush preview/worker parity (Clear vs Src shader in both).

Noted, not changed (judgement or larger scope): every Auto run re-hashes the 192 MB model (integrity over ~0.5 s); emoji text-presentation symbols such as bare `♥`/`✔` render large; photo pending upload holds the feature `busy` flag like GIF/sticker sends; accept still sends no push to dismiss a killed second device's ring (now harmless, but the notification lingers until the 45 s ring timeout or call end).

Verification: Android `gradlew.bat assembleDebug lintDebug testDebugUnitTest` PASS: **244 tests / 39 suites, zero failures/errors/skips**, lint zero errors/warnings (Android Studio JBR). Backend fix: [PR #124](https://github.com/aarahman04/One-on-One/pull/124), open, not deployed. Backend `npm test` **117/117**, `npx tsc --noEmit` pass (local run needed `npm ci` in `backend/` and `client/` for socket tests; no lockfile change). No device testing.

## In-game reactions, game controls, draws and invite pop-up (2026-10-21, `claude/game-reactions`; backend `claude/game-reactions`)

Owner requests, tested on the API 36 emulator demo (`OneOnOne_D3`, debug APK) with a scripted partner:

- **Reactions/comments during a game.** Smile button in the game header opens a tray: eight quick emoji, eight ready lines ("Nice move!", "Good game"…) and a one-line comment with a live `n/60` counter (code points, emoji count once). Any send closes the tray. New socket event `match:react` → server relays `match:reaction` to both players; not stored, not pushed, 700 ms per-user throttle (a throttled tap is silent). Path: UI → GameViewModel → GameService → Transport → InternetTransport.
- **Nothing is covered.** The partner's reaction replaces the "Chess · 5 min" line under their name for 3.5 s; ours replaces the how-to line above the buttons. A haptic tick and a polite live-region announce the partner's.
- **One action bar for every game.** Header is back / avatar / name / react only. Bottom bar: how-to line (or notice/reaction/draw offer), then equal **Offer draw · Resign · Leave** buttons with icons. Leave/Resign keep their confirmation.
- **Draw in every game, Ping Pong included, at any time.** Backend `match:draw` previously chess-only. A partner's offer shows "Alex offers a draw · Decline · Accept" in the bar. A declined offer shows "Alex declined your draw offer."; an accepted one ends the match "A draw · Draw by agreement". Chess keeps claimable draws (repetition / fifty moves) as a highlighted Claim draw button.
- **Invite pop-up.** The invitee now gets a centred card (game icon, "Alex invites you to play", game, options, Play / Not now) instead of a top bar; X/back/outside = Not now.
- **Chat polish.** Message-reaction pill emoji 15 → 12.5 sp and quick bar 22 → 19 sp; single-emoji time pill no longer overlaps the glyph. All six game result cards checked in chat (chess, ping pong, tic-tac-toe draw, connect 4, reversi, dots & boxes).
- Demo only: every game card in history; typing `game` makes Alex invite you; Alex answers reactions and declines then accepts draw offers.

Verification: Android `assembleDebug lintDebug testDebugUnitTest` **247 tests / 39 suites, 0 failures**, lint 0 errors/warnings. Backend `npm test` **119/119**, `tsc` pass. Emulator demo only; no real two-phone run. Release defaults stay **16 / 2.0.9** by owner request.

### Xiaomi / two-phone checklist (game reactions and draws)
- [ ] During each game send an emoji, a quick line and a typed comment from both phones; each shows under the sender's name on the other phone, above the buttons on the sender's, and covers nothing. Counter stops at 60.
- [ ] Offer a draw in chess, a grid game and Ping Pong; decline once (offerer sees "declined"), accept once (both see "Draw by agreement", a game card is written).
- [ ] Invite from phone A: phone B shows the centred pop-up; Play starts, Not now declines, and X/Back/outside preserves the invitation in the chat banner (updated by the 2026-10-22 review).
- [ ] Reaction pills on chat messages look smaller but still readable.

## Revert live dictation and shared TTS (2026-10-22, 2.0.10, versionCode 17)

Owner report: dictation (`/audio`, letter mic) and letter read-aloud stopped working after the 2026-10-21 "see what's being written" change; restore the previous behaviour.

- Root cause: the batch made `SpeechTranscriber` use the device recognizer whenever one existed (`!server || device.available`), so release builds never used the speech service's server models; it also forced `createOnDeviceSpeechRecognizer` and restarted segments continuously.
- Reverted to the pre-batch (`2fd1848`) `SpeechTranscriber`, `AndroidDeviceSpeech`, `AndroidNarrationAudio` (per-play TTS engine, no singleton/prewarm), the composer/letter dictation wiring (append on Stop, Cancel button, composer hidden while listening) and their tests.
- Kept: unsent letter preview read-aloud (device voice; the server can only narrate saved letters), From nickname default, label-aligned letter mic, 118 s cap.
- Release defaults bumped to **17 / 2.0.10**.

Verification: `assembleDebug lintDebug testDebugUnitTest` PASS: **242 tests, 0 failures**, lint clean. Build-verified only; not device-tested. Server dictation/narration also needs the Railway speech service running.

### Xiaomi checklist (dictation revert)
- [ ] Release build: `/audio`, speak, Stop: "Transcribing…" then punctuated text appended to the draft.
- [ ] Letter mic: speak, Stop: words appended to the letter body; closing the letter mid-dictation cancels.
- [ ] Cancel (X) in the dictation bar discards the recording.
- [ ] Open a sent letter, tap speaker: natural server voice; preview speaker reads with the phone voice; Edit/Send/close stops it.


## 2026-10-22 ? Games timer/comments and letter preview recitation

Fixed countdown receipt anchoring: MatchSession timestamps accepted snapshots, including same-version sync responses. Clock UI uses that timestamp instead of resetting at composition or reusing an older version timestamp, clamps at zero, and stops at the server result. Comments now require the connected game transport and current account/connection, rather than blocking on stale partner presence. Regression tests cover same-version sync, screen re-entry, stopped/expired clocks, stale presence, reaction delivery and disconnected sends.

Letter previews now select the same narration path as sent letters. SERVER_SPEECH=true uses a new authenticated draft POST in ../One-on-One/backend, reusing saved-letter voice/speed/splitting and the existing Android part playback/prefetch/fallback. Device-only builds keep both paths local. Both paths share spoken paragraph formatting. Drafts are not saved or sent as messages. Tests verify identical server synthesis input/voice, API authentication/payload, preview cleanup and device fallback.

Validation: testDebugUnitTest (246 tests, zero failures/errors/skips), assembleDebug and lintDebug passed. Backend npm test (121/121) and npx tsc --noEmit passed. No physical-device check, install, release or deployment performed. Natural preview requires the companion backend update before use with SERVER_SPEECH=true; the website's preview is unchanged.

## 2026-10-22 - App review, invitations and game verification

Incoming invite dismissal now keeps the invite pending and exposes Play/Not now in a chat banner, allowing typing to continue. Android/web no longer block invitations on partner presence; the backend creates pending invites for offline recipients and attempts the existing native push. Membership, busy checks and the 30-second expiry remain. Four turn-game clock tests cover synchronization, increments, reconnect, draw offers, timeout, late moves, rematches and no-limit play. Ping Pong remains score-based per owner instruction.

Also fixed a reproduced Chess zero-size/NaN crash during keyboard/window resizing, short-screen comment tray scrolling, game action wrapping, old-match reaction leakage, malformed live-event stream termination, per-message cache error isolation, stale history undoing edits/deletions, retry after transient preference-sync failure, expired photo URL reuse and GIF search pagination/retry. See QUALITY-REVIEW-2026-10-22.md for the scope and remaining checks.

Validation: latest Android JVM results are 251 tests across 40 suites, zero failures/errors; final Kotlin compilation and assembleDebug completed. Backend 126/126 tests and TypeScript passed. Web 48/48 tests, strict lint/TypeScript, production build and full browser fixtures at 1280px/360px passed. Earlier narration/timer lint passed; final broad-review lint and remaining emulator checks were stopped at the owner's request to update docs and push without further build work. Emulator reproduced the original Chess crash but ANRs prevented completing post-fix native interactions. No physical-device or live FCM/two-device-call check, release or production deployment performed. Companion backend changes must be deployed for offline invitation delivery and server preview narration.
