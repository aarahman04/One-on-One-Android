# Claude Code cross-check handoff: completed fix batch, 2026-10-21

## Start here

Independently review the merged Android/backend fixes against the owner's brief. The implementation is complete and its required checks passed; production deployment and physical-device validation are not complete. Do not treat either the prior success report or this handoff as proof that every runtime path works.

The earlier `codex-fix-batch-2026-10-21.md` is historical input. Its worktree integration and cleanup steps have already been executed. Do not apply those snapshots again. The owner subsequently authorized PR merges, and both implementation PRs are merged. Never push directly to main.

Read in order:

1. Android `CLAUDE.md` and the backend repo's rules; preserve unrelated local changes.
2. This handoff, `docs/PROGRESS.md`, `docs/ARCHITECTURE.md`, and `docs/XIAOMI-FIX-BATCH-2026-10-21.md`.
3. `C:\Users\aarah\OneDrive\Documents\Gather\gather-2026-10-21-1415.md` and the raw `gather-2026-10-21-1413.md`.
4. `C:\Users\aarah\One-on-One\docs\API-CONTRACT.md`, the ONLY API authority, then the actual changed code and tests.

## Repositories and exact review ranges

| Repo | Baseline | Tested implementation head | Merged on main |
| --- | --- | --- | --- |
| `C:\Users\aarah\One-on-One-Android` | `492d283` | `e671598` | `727ce59672775e61930b1c0786a3f84b9ae4894b`, [PR #45](https://github.com/aarahman04/One-on-One-Android/pull/45) |
| `C:\Users\aarah\One-on-One` | `9f4081a` | `015791a` | `d982a84a731904e605ac83f0d995a08a53d1f640`, [PR #123](https://github.com/aarahman04/One-on-One/pull/123) |

Both tested heads have exactly the same Git tree as their respective merge commits. Review `git diff 492d283 727ce59` and `git diff 9f4081a d982a84`; the Android docs follow-up is separate from this implementation range.

The app is native Kotlin/Compose, Hilt, Coil 3 and Socket.IO, not the old Capacitor client. Backend is TypeScript/Express. Keep UI -> ViewModel -> MessageService -> Transport -> InternetTransport; call signaling shares the existing socket seam.

## Scope and decisions that must survive review

- Issues 10/11 (games) were already fixed and excluded. Android game files, game tests and the game-invite notification timeout have ZERO diff from the baseline. Backend `56522fb` was not integrated. The Android WIP contained game changes, but a later commit removed them; review the final diff, not one WIP commit in isolation.
- Backend edits are limited to `backend/` and `docs/API-CONTRACT.md`. Web `client/` and `speech/` remain READ-ONLY. Operational observations below do not authorize rewriting either service or editing web code.
- Latest owner correction: ONLY ONE emoji grapheme uses the large bare presentation. Two or more emoji use normal bubbles. This supersedes the earlier 1-3 emoji requirement. A family, skin-tone sequence, flag or ZWJ emoji can still be one grapheme.
- Prefer available Android on-device recognition, otherwise an installed recognizer. Restart segments continuously; preserve existing field text and current partials. Keep the 4000-character limit. Server fallback for a phone without recognition is deliberately capped at 118 seconds and transcribes at the cap.
- Keep old check-in rendering, decoding, push handling and export; reject new sends and remove creation UI. Do not remove the database constraint.
- Preview narration is device-only. Sent letters retain server narration/fallback. From is `myNickname`, then saved signature, then `me`; To remains `otherNickname`.
- No new dependency, migration, application ID, signing-key or release-version change was made.

## Change map

Android paths below are relative to `app/src/main/java/app/web/oneonone/`.

| Issues | Behavior to cross-check | Main code / tests |
| --- | --- | --- |
| 1-3 | Restart after silence/no-match; partial/final append; no discard; punctuation formatting on API 33+; stop/background/stale token handling; 118-second server fallback | `data/media/AndroidDeviceSpeech.kt`, `SpeechTranscriber.kt`, `SpeechRepository.kt`, `MediaRepository.kt`; `ui/chat/SpeechViewModel.kt`, `Dictation.kt`, `ChatScreen.kt`, `ChatViewModel.kt`, `Composer.kt`; `SpeechTest.kt` |
| 4-5 | 16 dp visible mic, >=40 dp target, centred label; existing large 4000-character body retained | `ui/chat/Dictation.kt`, `FeatureUi.kt` |
| 6-8 | Preview speaker/lifecycle; lazy shared warmed device TTS; short first server part; correct nickname perspective | `data/media/AndroidNarrationAudio.kt`, `LetterNarrator.kt`; `ui/chat/FeatureUi.kt`, `SpeechViewModel.kt`; `data/api/AccountApi.kt`; backend `connectionService.ts`, `speechService.ts`; `SpeechTest.kt`, backend speech/connection fixtures |
| 9 | Raw BitmapFactory decode, exactly one EXIF transform for all eight cases, re-encode without EXIF | `data/media/MediaRepository.kt`, `ui/chat/PartnerPhoto.kt`; `ExifOpsTest.kt` |
| 12,20 | Reconnect before end; sibling resolution after collector cancellation; no late setup revival; 30-second last-socket grace; reconnect/accept/end races | `call/CallManager.kt`, `CallTerminationTest.kt`; backend `callService.ts`, `socketServer.ts`, `callDisconnect.test.ts` |
| 13 | No new check-in composer/command/backend send; retain history | `data/model/FeatureMessages.kt`, `ui/chat/Composer.kt`, `FeatureUi.kt`, `cards/S4Previews.kt`; `FeaturesTest.kt`, `SlashMatchesTest.kt`; backend `messageService.ts`, `leaveGames.test.ts` |
| 14-15 | Payload-sized spinner placeholder, synchronous signed-URL seed, stable attachment-path Coil keys, working retry, GIF <=200 dp | `data/media/MediaRepository.kt`; `ui/chat/FeatureUi.kt`, `FeatureViewModel.kt`, `AttachmentPresentation.kt`, `DocumentViewer.kt`; `AttachmentPresentationTest.kt` |
| 16 | Exactly one valid emoji grapheme is large/bare with receipt/time pill; multiple emoji and ordinary symbols remain normal | `ui/chat/EmojiOnly.kt`, `MessageBubble.kt`; `AttachmentPresentationTest.kt` |
| 17-18 | Latest arrow near 1.5 screens, smooth return/re-pin, accessible labels; header photo viewer uses same cache identity | `ui/chat/ChatScreen.kt`, `ChatHeader.kt`, `FeatureUi.kt`, `AttachmentPresentation.kt`; `AttachmentPresentationTest.kt` |
| 19 | <=2048 px long side, JPEG 85 for ordinary photos, retain PNG alpha/GIF animation; close sheet immediately and show local pending image | `data/media/MediaRepository.kt`; `ui/chat/FeatureViewModel.kt`, `ChatExtras.kt`, `FeatureUi.kt` |

`app/src/debug/java/app/web/oneonone/DemoChatActivity.kt` adds local avatar/emoji/history fixtures, not production features. This batch also fixed synchronous TTS failure leaving Playing state, stale recognition callbacks/partial loss, cached failed-painter retry, and duplicate call accepts resetting duration.

## Verification already performed

- Android: `gradlew.bat assembleDebug lintDebug testDebugUnitTest --console=plain --max-workers=1` PASS; **237 tests, 0 failures, 0 errors, 0 skipped**. Used Android Studio JBR (`C:\Program Files\Android\Android Studio\jbr`), not the default JDK 25.
- Speech targeted tests: **23 passed**, including an eleven-minute fake-device session, restarts, latest partials, stale callbacks, lifecycle and preview/error paths. This is NOT eleven minutes on an actual phone recognizer.
- Backend: from `backend/`, `npm test` PASS **116/116**, `npx tsc --noEmit` PASS. No live credentials or production calls were used by these tests.
- [Android CI run 39580424551](https://github.com/aarahman04/One-on-One-Android/actions/runs/39580424551) PASS, including debug build/lint/tests, unsigned minified release APK/AAB, release lint, native-library validation and 16 KB alignment. The signed release/upload job was intentionally skipped for a PR; no Play release was produced.
- The first integrated unit run exposed an old nine-command fixture after check-in removal. It was corrected and the complete suite rerun. The owner's single-emoji correction also landed before the final passing run.
- API 36 emulator demo: header viewer open/close, arrow appearance/return, letter mic/body layout, preview punctuation and Pause/Stop/Edit controls, large single emoji versus normal multi-emoji bubble, and a local test-photo send/echo at 3:2 aspect were observed. The one-second pending-upload state was not captured. Demo upload copies local bytes, so it does NOT prove production resize output or network speed.
- Local APK `app/build/outputs/apk/debug/app-debug.apk` SHA-256: `bedb53ab8d704c1daac55d30c7950edde4cb06d8cb48261167ec610b1e13689c`. The installed emulator APK matched this hash.
- Local, ignored evidence: `captures/fix-batch-2026-10-21/` (screenshots, `verification.json`, helper scripts). Do not stage the whole captures folder or assume it exists on another machine.

For an independent runtime-code review, rerun the required commands above after any code change. This docs-only follow-up does not change the verified runtime trees.

## Railway / speech status: not a completed rollout

Snapshot checked **2026-10-21 10:59 UTC (16:29 IST)**:

- Public backend `GET https://one-on-one-production-a5b8.up.railway.app/health` returned **200**, `{"status":"ok"}` in about 0.8 seconds. This proves liveness only, not the deployed revision or speech functionality.
- `railway status --json` reported production `speech` and `One-on-One` deployments SUCCESS/RUNNING, both built from **`3d0c1482fa93298291dce9333b268cb88cb88542`** at approximately 03:33 UTC. That is older than backend PR #123. Do NOT report the fix batch as deployed based on this status.
- [Railway's official status page](https://status.railway.com/) reported an active control-plane outage with new builds paused across all regions; its latest listed update was 09:28 UTC. Status is time-sensitive: recheck it before acting. Running workloads can remain healthy while the dashboard/build pipeline is unavailable.
- The owner said they entered `SPEECH_MODEL_TTL=0` on the `speech` service. Applying that variable to a running deployment is NOT verified. No variable values or secret-bearing variable dumps were read.
- Read-only source check: `speech/app/main.py` starts the idle-model reaper only when `MODEL_TTL > 0`. Setting 0 retains a model after first use; it does not eagerly load it, disable Railway service sleeping, fix the platform outage or prove fast phone narration. Speech code was not modified. Persistent models also retain their memory until the process exits.

Next operational action after recovery: verify/redeploy the backend at `d982a84` or a newer commit containing it, confirm the speech variable change is actually applied, then test authenticated speech with approved test accounts without printing tokens, audio or letter text. Keep both services in the same Railway project; backend private host remains `http://speech.railway.internal:8000`. No redeployment was triggered during this documentation follow-up.

## Priority review targets, not verified production diagnoses

1. Trace Android pending invite/answer cancellation, early accepted/ended events and duplicate taps through actual `CallManager` callers, not only the small pure `CallAttempt` tests. Recheck no finished call can reopen camera/mic or finish a newer call.
2. Inspect slow/missing acknowledgements around TURN: backend `turnService.ts:41` has a fetch without an explicit timeout; `socketServer.ts` mutates invite/accept state before awaiting TURN, while Android `RealtimeSocket.kt:104` uses a 10-second ack timeout. An ack that never arrives is distinct from the fixed early-event ordering. Reproduce before choosing a change.
3. Inspect a late decline from another callee device after acceptance: `backend/src/services/callService.ts:311` does not itself require ringing state. Add a focused fixture before deciding whether/what to change. This has not been established as the owner's missing-card cause.
4. Review main-thread recognizer ownership, disposal, generation guards, Stop during restart, partial-to-final replacement and field truncation. Check shared TTS callbacks/focus/lifecycle across viewer and preview.
5. Check expired signed URLs/retry, cache keys and conversation/account isolation; all eight EXIF matrices, image memory limits, transparent PNGs and GIFs; failure/conversation-switch paths for pending uploads; single-emoji false positives and scroll re-pinning.

Call state/timers are process-local; restarts do not preserve active calls and call-log persistence remains best-effort. No production database read proved the original photo orientation or missing video-card cause. Old incorrectly encoded avatars need re-uploading; this batch does not repair stored pixels.

## Remaining web-client work (READ-ONLY here)

- `client/src/features/slashCommands.ts:10,30`: remove `writeCheckin` from the context and command list; hints derive from that list.
- `client/src/pages/ChatPage.ts:20,2569`: remove `openCheckinComposer` import and the `writeCheckin` callback. Preserve old check-in rendering around lines 862/1606 and export label around 1621.
- `client/src/services/connectionsApi.ts:19`: add nullable `myNickname`; pass it from `ChatPage.ts:2555` to `client/src/features/letters.ts:111`. Default From around lines 117-119/155 to nickname, then saved signature, then `me`. To stays as currently implemented.

These are review instructions for a separately authorized web change, not permission to modify `client/` in this batch. Line numbers refer to the current unchanged client; verify them before editing elsewhere.

## Migrations, manual checks and workspace state

**No migrations needed.** Latest remains `040_message_type_game.sql`. There is no Supabase SQL to run for this batch. The optional call-card diagnostic SQL and owner steps are in the Xiaomi checklist.

Still unverified: Xiaomi/physical recognizer duration and punctuation quality, TTS startup latency/sound, real push delivery, real two-account audio/video calls/reconnects, production upload bytes/timing, and authenticated speech after rollout. Re-upload the original avatar and perform the checklist. Do not call these complete from demo screenshots or unit tests.

Removed after integration:

- `.claude/worktrees/agent-a32b33074565240ae`
- `.claude/worktrees/agent-af033aaf72acbc85e`
- `.claude/worktrees/agent-af64c11ce103daa35`
- `C:\Users\aarah\One-on-One-wt-speech`
- `C:\Users\aarah\One-on-One-wt-games`

Keep unrelated worktrees `agent-a0fa498fab52f088d`, `agent-a1d47876fa06d6113`, `One-on-One-Report` and `oo-polish` alone. Existing local `CLAUDE.md` edits, tooling/skill directories, design files and unrelated backend untracked files predate this handoff and are not part of the push. Never `git add -A` blindly.

Emulator `OneOnOne_D3`, `emulator-5554`, is a temporary read-only API 36 session with 1024 MB RAM, two cores, software graphics, Vulkan disabled and no snapshot saving, after earlier disk/memory stalls. Local demo games/chat actions after verification did not change source. The owner was also operating the emulator; do not compete for controls or treat every screen change as automated evidence. Temporary keyboard preferences from the interrupted emoji interaction were restored (`show_ime_with_hard_keyboard=0`; added `stylus_handwriting_enabled` key removed). The emulator is left available; the idle Gradle daemon was stopped.

## Expected cross-check result

Lead with reproducible findings, severity, exact file/line, trigger and user impact. Separate regressions introduced by this batch, pre-existing limits, deployment blockers and physical-device checks. State what you independently ran versus what is recorded evidence. Fix only clear, bounded bugs in the allowed scope on a feature branch with meaningful regression coverage; list uncertain/large items instead of broad refactoring. Do not re-integrate WIP branches, alter games, invent APIs, add migrations or claim deployment/device success without evidence.
