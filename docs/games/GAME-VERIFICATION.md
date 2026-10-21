# Game verification (2026-10-21)

Automated:
- Backend `npm test` 88/88 (spin curve/decay/cap/kick/side-bounce/angle bound; Reversi opening,
  flips, illegal/malformed, pass, end, draw), `tsc --noEmit` clean.
- Android `./gradlew assembleDebug lintDebug testDebugUnitTest` passed: `PongClientTest` (spin curve,
  seat-2 mirror, no `w` = no spin), `MatchTest` (Reversi strict decode, flip ranks), `GameMotionTest`
  (chess slide and Connect 4 drop timing).

Emulator (2026-10-21, OneOnOne_D3, API 36, debug build, `DemoMatches` local stand-in — not the server):
- Table tennis: serve after countdown, racket follows the finger, rally of several returns with angled
  shots, miss scores for the other side, serve goes to the loser, 0–7 result screen, series, rematch.
- Reversi: standard opening, four legal dots, flip wave, partner reply, disc counts.
- Connect 4: gravity drop with landing hop. Chess: drag-and-drop settles from the finger, partner reply slides.
- Hub: Reversi listed, Dots & Boxes not; leave confirmation and result screen.
- Found and fixed: one-frame "final state" flash on new moves (Connect 4, tic-tac-toe, Reversi) and the
  chess drop jumping back to its origin square.
- Emulator renders at ~15–20 fps (software GPU), so smoothness must still be judged on a phone.
- Reference APK: not installable (`INSTALL_FAILED_MISSING_SPLIT`).

Not verified: any device run, two-phone play, feel of spin constants, web ↔ Android play (web has no
Reversi). Manual steps: Xiaomi checklist in `docs/PROGRESS.md` (2026-10-21 entries).
