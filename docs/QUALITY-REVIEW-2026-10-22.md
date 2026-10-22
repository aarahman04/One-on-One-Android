# App review - 2026-10-22

Reviewed the Android chat, game, letter, media, settings, account and notification paths, plus the shared game backend and web invite UI. This pass fixes concrete failures; it is not a claim that every device and network combination has been tested.

## Fixed

- Game clocks/comments: countdowns use the snapshot receipt time, including same-version sync, and comments use the active connected game session instead of stale partner presence.
- Letter narration: preview and sent letters share spoken formatting and the narration pipeline. Server-speech builds use the same voice, speed and text splitting through the new authenticated draft endpoint; device-only builds use the device voice for both.
- Incoming invitations: dismissing the modal by outside tap, Back/Escape or X keeps the invitation pending. A chat banner retains accept/decline actions, so typing can continue. Only explicit decline sends a decline.
- Offline invitations: Android and web require the sender's connection, without blocking on partner presence. The backend creates the pending match and attempts its high-priority native push even when the recipient has no socket. Existing membership checks and 30-second expiry remain.
- Chess resizing: a zero-size board during keyboard/window transitions divided by zero while animating pieces and crashed with `Cannot round NaN value`. Zero-size rendering is skipped and negative board dimensions are clamped.
- Game layout: comment trays scroll in short viewports; action labels wrap and buttons grow with text size. Reactions are cleared when the match changes.
- Live chat: malformed socket JSON or timestamps no longer kill the stream of later events. A failed message/cache write is handled per event so later messages still arrive.
- Message history: stale acknowledgements and older history cannot restore a deleted message or overwrite a newer edit.
- Account setup: failed preference synchronization retries when the connection recovers. An older server's explicit 404 still retains the established one-attempt compatibility behavior.
- Photos: refreshed signed URLs reach the image loader in settings and calls instead of reusing requests built with expired URLs.
- GIF picker: query changes cancel pagination belonging to the previous search; pagination observes the cursor as well as scrolling. Failed searches have a Retry action.

## Timing rules checked

Chess, Tic-Tac-Toe, Connect 4 and Reversi share the tested countdown path. New server tests cover invitation time not charging the clock, each player's clock, increments, same-version sync, away/reconnect, draw offers, expiry, late-move rejection, no-limit games, and rematch reset/seat swap. Ping Pong remains score-based by the owner's request; score targets, pause/resume and rematches retain their existing tests.

## Verification

- Android JVM tests: 251 passed, with zero failures/errors across 40 suites. The final source changes compiled and `assembleDebug` completed before the owner requested stopping further build work; final lint was cancelled.
- Backend: 126 tests passed; TypeScript passed, including real-socket offline invite recovery and native push payload checks.
- Web: 48 tests, strict TypeScript/lint and production build passed. Browser integration passed at 1280px and 360px, including all five games, clocks/reactions, Escape/outside/X invitation dismissal, continued typing and explicit decline.
- Runtime Android evidence and screenshots: `app/build/reports/quality-review/`. An isolated emulator reproduced the original Chess crash and showed running clocks and the comment composer. Final native checks were interrupted by emulator ANRs and then stopped at the owner's request; post-fix resize, five-game native UI, invite-banner and GIF Retry interactions are not device-verified.

No production deployment, physical-device install, live FCM delivery, real-account sign-in or two-device call was performed. Offline invites and natural letter previews require deploying the companion backend changes in ../One-on-One.
