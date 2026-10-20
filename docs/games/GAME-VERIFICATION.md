# Game verification (2026-10-21)

Automated:
- Backend `npm test` 88/88 (spin curve/decay/cap/kick/side-bounce/angle bound; Reversi opening,
  flips, illegal/malformed, pass, end, draw), `tsc --noEmit` clean.
- Android `./gradlew assembleDebug lintDebug testDebugUnitTest` passed: `PongClientTest` (spin curve,
  seat-2 mirror, no `w` = no spin), `MatchTest` (Reversi strict decode, flip ranks), `GameMotionTest`
  (chess slide and Connect 4 drop timing).

Not verified: any device run, two-phone play, feel of spin constants, web ↔ Android play (web has no
Reversi). Manual steps: Xiaomi checklist in `docs/PROGRESS.md` (2026-10-21 entries).
