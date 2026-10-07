# Prompt — Claude Opus, phase 2 (A4 alarm + A5 calls + backend/contract + reviews)

Paste below the line into Claude Code (Opus) started at `C:\Users\aarah\One-on-One`.

---

You own the native-heavy, bug-critical parts of the One on One native Android app, plus everything on the backend side. GPT Sol 6.1 builds the rest in parallel. Read first:
- `C:\Users\aarah\One-on-One-Android\docs\prompts\gpt-sol-6.1-phase2.md`: the work split, and the seams GPT creates (RealtimeSocket, the AlarmPushHandler/CallPushHandler interfaces, the AlarmCard/CallLogCard files, CallLauncher).
- `docs/prompts/gpt-sol-6.1-android.md` (A4/A5 definitions).
- `docs/API-CONTRACT.md` and `CLAUDE.md` in both repos.

## Your lane

1. **Reviews:** review each GPT PR in One-on-One-Android after it merges.
   - Check it against the contract, the CLAUDE.md rules (server is the authority; transport abstraction; no secrets) and the seams.
   - Fix real problems in small `fix/` PRs.
   - Report anything that needs a product decision to the owner.
2. **Backend / contract:**
   - Close every "Contract gap" GPT logs. Each change goes in a web-repo PR with tests, plus an `docs/API-CONTRACT.md` update in the same PR.
   - Copy the updated contract into the Android repo.
   - Migrations: write them and have the owner apply them.
3. **A4 Emergency alarm.** Branch `a4/alarm` in One-on-One-Android, started after GPT's A2 (seams) and A3 (push routing) are merged.
   - **Push handling:** implement `AlarmPushHandler`, an alarm foreground service and the alarm channel, porting `C:\Users\aarah\One-on-One\android\app\src\main\java\app\web\oneonone\Alarm*.java` and the intent-token checks in MainActivity.
   - **Alarm card:** replace the `AlarmCard.kt` placeholder. Disable it with "sending…" until the raise has its server id. Only the raiser can cancel, only the other member can acknowledge, and the ring stops only after the server confirms.
   - **Stopping:** every stop path (FCM ack/cancel, socket ack, tap, Silence action, auto-clear) cancels the service AND the notification by id. Tapping opens the chat once and never re-rings.
   - **Never re-ring:** persist handled alarmIds in DataStore, so resync or relaunch never re-rings.
   - **Killed app:** must work with the app killed. If the foreground-service start is refused, fall back to a high-priority notification with a full-screen intent.
   - **Tests:** unit-test the state machine.
4. **A5 Calls.** Branch `a5/calls`, after A4.
   - WebRTC (stream-webrtc-android) over `RealtimeSocket` with the `call:*` events and `/api/turn-credentials`; replace the `CallLauncher` binding and `CallLogCard.kt`.
   - **Audio routing:**
     - `MODE_IN_COMMUNICATION` for the whole call; restore the previous mode afterwards.
     - Voice calls default to the earpiece; video calls default to the speaker.
     - Speaker toggle, plus Bluetooth and wired headset routing (`setCommunicationDevice` on API 31+, `setSpeakerphoneOn` below).
     - `STREAM_VOICE_CALL` volume control.
   - **During the call:**
     - Proximity wake lock on voice calls.
     - Foreground service of type `phoneCall|microphone|camera`.
   - **Incoming calls:** implement `CallPushHandler`, a full-screen CallStyle notification with Answer/Decline; `call_end` dismisses it.
   - **Interop:** test against the web client as the other party.
5. **W6 (last, only after the native app is live on Play):** remove the Capacitor `android/` folder, the Capacitor deps and native-only JS from the web repo, in one PR.

## Process

- One branch + PR per part.
- Before touching a shared file (`AndroidManifest.xml`, `MainActivity.kt`, the nav graph, `libs.versions.toml`), pull `main` and check `gh pr list` for an open GPT PR on the same file. Merge after it, not against it.
- Merge your own PR when CI passes.
- Update `docs/PROGRESS.md` / `docs/ARCHITECTURE.md` in the repo you change.
- Say plainly what was device-verified and what wasn't. Gate A4 and A5 on the owner's Xiaomi device test.
