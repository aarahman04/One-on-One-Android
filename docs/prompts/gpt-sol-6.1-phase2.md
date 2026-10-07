# Prompt — GPT Sol 6.1, phase 2 (A1 → A7, through to a release-ready app)

Paste everything below the line into GPT Sol 6.1, started in a terminal at `C:\Users\aarah\One-on-One-Android`.

---

You are continuing the native Android app for "One on One" (Kotlin + Jetpack Compose). A0 (scaffold) is merged on `main`. Your job now is to build the rest of the app to a release-ready state. You share the work with a second engineer (Claude Opus), so read the split below carefully and stay inside your lane.

## Repos and sources of truth

- **Your repo:** `C:\Users\aarah\One-on-One-Android` → https://github.com/aarahman04/One-on-One-Android.git (`gh repo set-default aarahman04/One-on-One-Android`).
- **Web repo, read-only for you:** `C:\Users\aarah\One-on-One`. It holds the backend, the web client and the old Capacitor app; never edit, commit or push there. Run `git -C C:\Users\aarah\One-on-One pull` before each milestone.
- **API:** the authoritative contract is `C:\Users\aarah\One-on-One\docs\API-CONTRACT.md`. Your repo's `docs/API-CONTRACT.md` is a snapshot. If the two differ, the web repo wins; copy it over in your next PR.
  - Never guess an endpoint, event or payload.
  - If you need something the backend doesn't provide, write it under "Contract gaps" in `docs/PROGRESS.md` and in your milestone report. Claude makes the backend change.
- **Rules:**
  - Follow `CLAUDE.md` in your repo, plus `docs/prompts/gpt-sol-6.1-android.md` (non-negotiables, stack, quality bar, milestone definitions).
  - Treat `docs/prompts/PLAN-web-android-split.md` as background.

## Work split

**You (GPT Sol 6.1) own:**
- **A1** auth + connection.
- **A2** chat core.
- **A3** notifications for normal messages + permissions/OEM onboarding.
- **A6** feature parity (everything except alarm and calls).
- **A7** release prep.

**Claude owns** (do NOT implement these, only the seams below):
- **A4** emergency alarm: service, FCM handling, alarm card behaviour.
- **A5** voice/video calls: WebRTC, audio routing, call UI, incoming-call notifications.
- Any backend / web-repo change, and syncing the API contract.

**Seams you must create so the two lanes don't collide.** Put them in exactly these places, keep them small and documented, and treat them as frozen once merged:

1. **Realtime socket** (A2): `app.web.oneonone.data.realtime.RealtimeSocket`, a Hilt singleton wrapping the single Socket.IO connection:
   - `val state: StateFlow<ConnectionState>`
   - `fun events(name: String): Flow<JSONObject>`
   - `suspend fun emitWithAck(name: String, payload: JSONObject, timeoutMs: Long = 10_000): JSONObject`
   - `fun emit(name: String, payload: JSONObject)`
   `InternetTransport` (messages) uses it. Claude's call code will use it for `call:*` events. Only this class creates a socket.
2. **Push routing** (A3): your `FirebaseMessagingService` parses `data["type"]`. You handle message types yourself. Delegate the rest:
   - `type == "alarm"` → `AlarmPushHandler.onAlarmPush(data: Map<String, String>)`
   - `type == "call"` → `CallPushHandler.onCallPush(data)`
   - `type == "call_end"` → `CallPushHandler.onCallEndPush(data)`
   Put the interfaces in `app.web.oneonone.push.handlers`, bound in `di/PushHandlersModule.kt` to no-op implementations that Claude will replace. Also create the `alarm` and `calls` notification channels in `Application.onCreate` (Claude fine-tunes them in A4/A5).
3. **Chat message rendering** (A2): render `type == "alarm"` through `ui/chat/cards/AlarmCard.kt` and `type == "call"` through `ui/chat/cards/CallLogCard.kt`.
   - Write simple placeholder composables with the signature `@Composable fun AlarmCard(message: ChatMessage, isMine: Boolean, onSend: (type: String, payload: JsonObject, replyTo: String?) -> Unit)` (and the equivalent for CallLogCard). Claude owns these two files afterwards.
   - The `/alarm` slash command opens a confirm dialog and sends the raise through `onSend`.
4. **Call entry point** (A2): voice/video buttons in the chat header call `CallLauncher.start(kind: CallKind)`, an interface in `app.web.oneonone.call` bound to a no-op that shows "Calls coming soon". Claude replaces the binding.
5. **Shared files** (`AndroidManifest.xml`, `MainActivity.kt`, the nav graph, `libs.versions.toml`): Claude may need small edits there too. Keep your edits to these files minimal and append-only where possible, so merges stay trivial.

## How to work (autonomous, all milestones)

- Work through **A1 → A2 → A3 → A6 → A7** in order without waiting for me between milestones.
- Per milestone:
  1. Pull `main`.
  2. Create a branch `aN/<short-name>`.
  3. Implement, with tests.
  4. Run `./gradlew assembleDebug lintDebug testDebugUnitTest` locally.
  5. Update `docs/PROGRESS.md` + `docs/ARCHITECTURE.md`.
  6. Open a PR with a device-test checklist.
  7. Wait for CI to pass, then merge it yourself (`gh pr merge --merge --delete-branch`) and continue.
  Never merge with red CI. Never force-push `main`.
- Large milestones (A2, A6) may be split into several PRs (e.g. `a6/letters`, `a6/media`). Smaller PRs are better.
- Before merging, check `gh pr list`. If Claude has an open PR touching the same shared file, rebase on `main` after it merges rather than racing it.
- **Device checkpoints:** the owner tests on a Xiaomi (HyperOS) phone after A2, after A3 and at the end. When you reach a checkpoint:
  - Post the checklist in your report.
  - Keep going on the next milestone.
  - If the owner reports a failure, fix it first (on a `fix/` branch) before anything else.
- **Google sign-in on debug builds:** the debug keystore SHA-1 must be registered in Firebase/Google Cloud for package `app.web.oneonone`, or sign-in fails. Print the debug SHA-1 (`./gradlew signingReport`) in your A1 report so the owner can add it.

## Milestone details (beyond `gpt-sol-6.1-android.md`)

- **A1:**
  - Credential Manager Google sign-in → supabase-kt `signInWithIdToken`.
  - Session persisted and auto-refreshed; an OkHttp interceptor adds `Authorization: Bearer`; a 401 triggers one refresh + retry, then sign-out.
  - Screens: sign-in, age gate/consent (same rules as the web app), connect (show/regenerate code, enter code, pending request, accept/decline/cancel), blocked list, settings (sign out, delete account).
  - Boot routing mirrors the web app: no session → sign-in; no connection → connect; live connection → chat.
- **A2:**
  - Implement everything in A2 of `gpt-sol-6.1-android.md` plus the seams above.
  - Bubbles use `BubbleTokens` (brand green own / blue other; wallpaper-tinted variants love/samurai), with the same time/tick placement as the web app.
  - The `message:send` ack, tempId dedupe and resync logic must be unit-tested.
- **A3:**
  - Normal message notifications only (alarm/call go to the handlers).
  - Register the token with `platform: "android-native"` (migration 035 is applied).
  - Suppress the notification when that chat is open and resumed.
  - The OEM onboarding screen is reachable from settings and shown once after first login.
- **A6:** letters, voice notes, images, files, ask, countdown, checkin, thisorthat, location, reactions, reply/search polish, wallpapers/appearance, report/block, nickname and leave-connection polish. Match web behaviour. Check each payload against the contract validators.
- **A7:**
  - R8 enabled with keep rules (Socket.IO, kotlinx.serialization, Supabase/Ktor, WebRTC, Hilt) and a release build smoke test.
  - Play Data Safety answers in `docs/RELEASE-CHECKLIST.md`, plus the manual Play Console steps.
  - Do not upload anything to Play. Do not bump versionCode past what the owner asks.

## Report after every milestone

PR link(s), merged yes/no, CI result, commands run with results, what was verified on a device vs only compiled/tested, device checklist, contract gaps, and anything you changed in a shared file.

Start now with **A1**.
