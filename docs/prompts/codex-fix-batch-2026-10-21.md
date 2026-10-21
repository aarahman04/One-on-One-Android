# Codex handoff: One on One fix batch (2026-10-21)

> HISTORICAL INPUT: this integration was completed and merged in Android PR #45 (`727ce59`) and backend PR #123 (`d982a84`). All five listed agent worktrees were removed. Do not repeat the WIP integration or cleanup workflow. Start with [the completed-batch Claude Code cross-check handoff](claude-cross-check-fix-batch-2026-10-21.md), which records verification limits and later owner corrections: only one emoji is large/bare, and PR merges were explicitly authorized. Production rollout and physical-device checks remain separate outstanding work.

You are taking over an unfinished batch of fixes for the "One on One" native Android app and its backend. The plan is
already approved by the owner. Do not re-plan or ask questions unless something is truly ambiguous and a wrong guess
would be costly. Decide, and record each decision in the final report.

## Read first
- `C:\Users\aarah\One-on-One-Android\CLAUDE.md`: repo rules. Keep diffs minimal and match the surrounding style.
  Never block the main thread. Keep accessibility. Never merge a PR, never push to main.
- `C:\Users\aarah\OneDrive\Documents\Gather\gather-2026-10-21-1415.md`: the owner's task brief (issues 1–20, the
  required report format, and the migration rules).
- `C:\Users\aarah\OneDrive\Documents\Gather\gather-2026-10-21-1413.md`: the raw voice-to-text dump the issues came from.
- `C:\Users\aarah\One-on-One\docs\API-CONTRACT.md`: the only API authority. Update it for every API change.

## Stack (verified)
- **Android:** native Kotlin with Jetpack Compose, Hilt, Coil 3 and Socket.IO. Not a web wrapper: every issue lives in
  the Android repo, the backend, or both.
- **Backend:** `C:\Users\aarah\One-on-One\backend` (TypeScript/Express). You may edit `backend/` and
  `docs/API-CONTRACT.md` only.
- **Off limits:** the web client `../One-on-One/client` is READ-ONLY. Don't edit the speech service
  (`../One-on-One/speech`) either.
- **Migrations:** none needed. The latest is `040_message_type_game.sql`. Image width and height are already in the
  payload, and the `checkin` DB constraint stays.

## OWNER UPDATE: games are already fixed, leave them alone
Issues 10 (game invite push/offline) and 11 (Ping Pong icon) are OUT OF SCOPE. Drop every game change the agents made:
- **Backend:** branch `claude/fix-games-calls` has commit `56522fb Games: invite an offline partner, 2 minute invite
  expiry`. Drop it (`git rebase --onto 56522fb^ 56522fb` or revert it). Keep `bc47f33` (checkin) and `5a82762` (calls).
- **Android:** worktree branch `worktree-agent-af64c11ce103daa35`, WIP commit `fe42ac2`. Restore these files to `main`:
  `ui/games/GameHost.kt`, `ui/games/GameViewModel.kt`, `ui/games/GameVectors.kt`,
  `app/src/test/.../ui/games/GameViewModelTest.kt`, and the game-invite timeout change in
  `push/MessageNotifications.kt`. Also strip any game notes from the `docs/PROGRESS.md` / `docs/ARCHITECTURE.md` edits.

## Where the work is
The `.claude/worktrees` folders below are under `C:\Users\aarah\One-on-One-Android`. Every Android worktree branched
from `main` at `492d283`. The "WIP" commits are unbuilt snapshots taken when the agents were stopped: build, review and
finish them.

| Area | Repo / worktree | Branch | State |
|---|---|---|---|
| Dictation + letter (issues 1–8), Android | `.claude/worktrees/agent-a32b33074565240ae` | `worktree-agent-a32b33074565240ae` | WIP `b47f3e7`. Edits in AndroidDeviceSpeech, SpeechTranscriber, SpeechRepository, SpeechViewModel, Dictation.kt (stopped mid-rewrite), Composer, ChatScreen, ChatViewModel, LetterNarrator, AndroidNarrationAudio, AccountApi. FeatureUi letter parts not started. |
| Dictation + letter, backend | `C:\Users\aarah\One-on-One-wt-speech` | `claude/fix-speech-letter` | `21274ef`: `myNickname` on /connections/current; short first narration part. Needs a review and `npm test` + `npx tsc --noEmit`. |
| Photo / calls / checkin (issues 9, 12, 13), Android | `.claude/worktrees/agent-af64c11ce103daa35` | `worktree-agent-af64c11ce103daa35` | `2fd1848` deterministic EXIF orientation (done). WIP `fe42ac2`: CallManager end-call fix, checkin removal (FeatureMessages, Composer, FeatureUi, S4Previews), docs, plus the game edits to drop. Checkin removal was in progress. |
| Calls + checkin, backend | `C:\Users\aarah\One-on-One-wt-games` | `claude/fix-games-calls` | `bc47f33` rejects new checkin sends; `5a82762` ends a call when a participant's socket is gone 30 s; `56522fb` games (DROP). |
| Chat UI (issues 14–19), Android | `.claude/worktrees/agent-af033aaf72acbc85e` | `worktree-agent-af033aaf72acbc85e` | WIP `56512b3`. Edits in MediaRepository, AttachmentPresentation (+test), ChatExtras, ChatHeader, ChatScreen, DocumentViewer, FeatureUi, FeatureViewModel, MessageBubble, DemoChatActivity. It was building when stopped. |

**Integration branch:** `claude/fix-batch-2026-10-21` in `C:\Users\aarah\One-on-One-Android` (at `main`, no commits
yet). Merge the three Android branches into it, after dropping the game changes. Expect conflicts in `FeatureUi.kt`,
`ChatScreen.kt`, `Composer.kt` and `MediaRepository.kt`: the agents edited different regions of the same files.

**Backend:** create one branch from `origin/main` (e.g. `claude/fix-batch-2026-10-21`) that combines
`claude/fix-speech-letter` with `claude/fix-games-calls` minus `56522fb`. Then open ONE PR in each repo and stop.
Don't merge.

## Approved behaviour per issue (finish whatever isn't done)

### 1–3 Dictation
**Root cause:** release builds use `SERVER_SPEECH=true`. Dictation records a clip capped at exactly 120 s; the clip is a
hair over 120 s, so the client (`duration > 120`) and the speech service (more than 120 s → 413) reject it, and the
whole clip is deleted. Server mode has no interim text. No code strips punctuation.

**Fix:**
- **Recognizer:** prefer the on-device `SpeechRecognizer` in continuous mode. When it ends on silence, timeout or no
  match, auto-restart it and append to the text already captured. Sessions must last ≥10 minutes.
- **Live text:** final and partial text goes live INTO the composer or letter body field, appended after the text that
  was there before dictation started. Truncate at 4000 characters.
- **Punctuation:** on API 33+ set `EXTRA_ENABLE_FORMATTING` = `FORMATTING_OPTIMIZE_QUALITY`.
- **Never discard text:** remove the cancel ✕ so only Stop remains. Leaving the screen stops the recognizer but keeps
  the text.
- **Server fallback:** keep the Whisper path only for phones without a recognizer. Set the cap to 118 s, and when the
  cap is hit, transcribe what was recorded instead of erroring.
- **Hygiene:** keep the generation/token guards and call the recognizer on the main thread. Release it on
  cancel/clear.
- **Tests:** add unit tests for the restart/append logic with a fake `DeviceSpeech`.

### 4, 6, 7, 8 Letter
- **4 (mic size):** the visible mic icon should be about 16 dp, centred with the "Your letter" label, without growing
  the row. Keep a touch target of at least 40 dp.
- **6 (read-aloud on preview):** add a speaker button to the letter PREVIEW, using the device voice (an unsent letter
  can't use server narration). It speaks "Dear {to}. {body}. With love, {from}." Stop it on Edit, Send, close or app
  stop.
- **7 (slow start):**
  - Use one shared, lazily created `TextToSpeech` engine, warmed when a letter viewer or preview opens. On release, stop
    it; don't shut it down.
  - Backend: part 0 is just the salutation plus the first sentence (~160 characters); done in `21274ef`, verify it.
  - Cold starts of the Railway speech service are a manual step for the owner (stop it sleeping, or set
    `SPEECH_MODEL_TTL=0`).
- **8 (From/To):** "From" defaults to `myNickname` (what the partner calls me), then the saved signature, then "me".
  "To" stays `otherNickname`. Add `myNickname: String? = null` to `CurrentConnection`.
- **Web-client note for the report:** the web letter's "From" could default to `myNickname`.

### 9 Photo
- Done in `2fd1848`: one shared deterministic decode (BitmapFactory, then the explicit EXIF matrix covering all 8
  cases, then re-encode with no EXIF), used for avatars and chat photos.
- Verify the build and tests.
- Report: the root cause was not proven against the stored file (the production read was blocked), and the owner must
  re-upload their avatar.

### 12 Video call card
- **Likely cause (unproven, production read blocked):** `call:end` is lost when the socket is reconnecting; the server
  never resolves the call, so no row is written.
- **Fix:**
  - Client: hangup, error and drop exits do `connected()` and then `signaling.end(id)`.
  - Backend: when a participant's last socket is gone for 30 s, end the call; done in `5a82762`.
- **Owner can confirm with this SQL:**
  `select created_at, payload->>'kind', payload->>'outcome' from messages where type='call' order by created_at desc limit 20;`

### 13 /checkin
- Android: remove it from the command list, composer hint, composer branch and previews. Keep rendering of old
  check-in messages, decoding of old pushes, and export.
- Backend: reject new check-in sends; done in `bc47f33`.
- Report under "Needs web-client change": the web client still offers /checkin. Find the exact file and lines in
  `../One-on-One/client` (read-only) and give the exact change.

### 14–19 Chat UI
- **14 (photo loading):** no "Loading photo…" text. Use a placeholder sized from the payload's width/height aspect
  ratio, with a subtle spinner. Seed the URL synchronously from the signed-URL cache so a scrolled-back item renders
  immediately. Coil cache keys = the attachment path.
- **15 (GIF size):** cap GIFs at about 200 dp on the long side.
- **16 (emoji):** messages of only 1–3 emoji (grapheme-aware, with unit tests) show large with no bubble, only the
  time and read receipt. Reuse the sticker time-pill treatment.
- **17 (scroll button):** a scroll-to-bottom arrow appears when scrolled up about 1.5 screens, animates to the end on
  tap and re-pins. Label: "Scroll to latest messages".
- **18 (header photo):** tapping the header photo opens a full-screen viewer, using the same Coil request and cache
  key. Label: "View profile photo".
- **19 (photo upload):**
  - Downscale to ≤2048 px on the long side; JPEG quality about 85.
  - Close the photo sheet at once and show a `PendingMediaBubble` for "image" with the local preview and a spinner
    while it uploads.

### 20 General pass
Fix only clear, safe bugs in the code paths above. List anything uncertain or large in the report instead.

## Verification (required)
- **Android:** `./gradlew assembleDebug lintDebug testDebugUnitTest` on the integration branch.
- **Backend:** `npm test` and `npx tsc --noEmit`.
- **Optional visual check:** if an emulator is available, use the debug `DemoChatActivity` with
  `adb exec-out screencap -p`.
- **Docs:** update `docs/PROGRESS.md` and `docs/ARCHITECTURE.md` (additive Mermaid diagrams) and add a Xiaomi device
  checklist.
- **Honesty:** never claim device behaviour you didn't test. The recognizer, TTS latency, push and real calls all need
  a real phone.

## Final report (the owner's required sections)
Done (per issue number) · Root causes found · Decisions made · Needs web-client change · Migrations (state "none
needed") · Manual steps for me (re-upload avatar; Railway speech sleep / `SPEECH_MODEL_TTL`; run the call SQL if
wanted) · Not done / uncertain and why. Include the PR links and the build/test results.

## Cleanup after merging into the integration branches
Remove the agent worktrees: `git worktree remove` for the three `.claude/worktrees/agent-a32b…`, `agent-af03…` and
`agent-af64…` folders, plus `C:\Users\aarah\One-on-One-wt-speech` and `C:\Users\aarah\One-on-One-wt-games`. Leave the
other worktrees (`agent-a0fa…`, `agent-a1d4…`, `One-on-One-Report`, `oo-polish`) alone; they aren't part of this work.
