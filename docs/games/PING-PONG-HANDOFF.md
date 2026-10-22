# Ping Pong handoff to Claude Code - 2026-10-21

Historical handoff: the owner's subsequent remake request resumed development.
See [2026-10-22 reference remake](PONG-REFERENCE-REMAKE.md) for current behavior.

The owner asked Codex to wrap up, update docs and hand over. Work has stopped. This
handoff supersedes older instructions to keep testing or finish Android CI/merge.
No further gameplay changes, emulator input or merge were made during wrap-up.

## Delivered

- Replaced the old Table Tennis game with Ping Pong: two-dimensional racket
  movement, stroke-directed shots, actual height-bearing flights and table bounces,
  open sides/corners, contact-to-serve, first-to-seven scoring and rematches.
- Retained the owner's accepted speed and eased bat rotation. Intentional brushing
  gives light spin around .1, rising to at most .2 for a hard brush before decay;
  neutral blocks stay spin-free. Spin persists after release and adds a small
  sideways kick at the table bounce.
- Addressed the reported edge/forward-stroke overlap with a client front-face
  guard, server back-face rejection, surface-clear launches and prompt confirmed
  contact display. Follow-through is allowed after the confirmed return.
- Replaced the `/games` and result-screen glyph with an original racket-and-ball
  vector. Updated mechanics, architecture, progress and backend API docs.
- Compared both APK bundles, recovered native mechanics and reviewed the owner's
  video. No reference code or art was copied. See the external reports below.

## Git, checks and deployment

- Android branch: `codex/pong-native-mechanics`.
  [PR 43](https://github.com/aarahman04/One-on-One-Android/pull/43) is **open**.
  Gameplay commit: `ca63c3e1b834fd0ea403715a560c38c924fa2df2`.
- Local gameplay validation passed: **214/214 Android tests**, `assembleDebug`
  and `lintDebug`. Backend: **94/94 tests** and TypeScript checking passed.
- Android CI run `39538900347` passed debug build/lint/unit checks, then failed
  at `:app:packageReleaseBundle` with Java heap exhaustion. Pushed fix
  `84c12b5ef466444aa1c9b08d77275f2f83f58589` splits release assembly and bundle
  packaging into separate Gradle invocations and moves unchanged demo tests to
  `src/testDebug`, matching their debug-only dependencies. It changes no gameplay.
  Retry `39540116953` was **in progress at the last check**; success is unverified.
- Backend [PR 121](https://github.com/aarahman04/One-on-One/pull/121) is merged
  after green checks. Merge: `3d0c1482fa93298291dce9333b268cb88cb88542`.
  Railway deployment `c0e900a6-d6fc-4e6a-81b3-230b0c4bbf66` reported SUCCESS;
  production `/health` returned `{"status":"ok"}`. Backend local branch is `main`.
- These handoff docs are a local docs-only commit after the pushed CI fix; push
  them when resuming. Preserve the owner's separate `CLAUDE.md` change and all
  unrelated untracked files. The web client was untouched. No Play upload,
  migration, signing change or production configuration change was made.

## Installed build and emulator

The latest gameplay APK was reinstalled at **08:59:58 IST on 2026-10-21** on the
visible **OneOnOne_D3 / emulator-5556** and left open. Local
`app/build/outputs/apk/debug/app-debug.apk` and installed `base.apk` hashes match:

`c8701aaa1b2203098c4a996e274a3e08d7304e452597e9a65622a8a014ce0962`

Display is 720x1600, density 280; restore with `adb shell wm size reset` and
`adb shell wm density reset` if needed. Gradle was stopped. Scripted gestures
competed with the owner's play, so all input automation was stopped. **Leave
controls to the owner unless they explicitly hand them back.**

Earlier touch recordings show rotated brush hits, table spin, scoring and rematches,
but predate final tuning. Final read-only observation shows a neutral rally and
clear front-side departures. The exact final edge/forward case has focused client
and server regression coverage; full visual/feel confirmation is still needed.
Emulator timing improved at the reduced resolution with compilation/recording
stopped; it is not a physical-phone performance result.

## Remaining for Claude

1. Inspect the latest PR 43 checks. Confirm the moved debug tests still execute
   (214 total at the validated gameplay revision), release APK/AAB packaging,
   release lint and native-library/16 KiB checks. Resolve failures before any merge.
2. Finish Android PR delivery when checks and owner testing allow it. Android has
   **not** been merged. Reconcile local docs without staging the owner's changes.
3. Have the owner confirm smooth movement, front-face contact when advancing at
   the edge, and reactable light/hard spin at the bounce on the installed build.
4. Test two independently authenticated Android accounts on physical phones:
   both seats, invites, serve, edge returns, spin, latency, background/resume,
   scoring and rematch. The debug opponent and Socket.IO fixtures are not this test.

## Where to continue

- Product details: [GAME-MECHANICS.md](GAME-MECHANICS.md),
  [ARCHITECTURE.md](../ARCHITECTURE.md), [PROGRESS.md](../PROGRESS.md).
- Production client: `app/src/main/java/app/web/oneonone/data/games/PongGame.kt`
  and `app/src/main/java/app/web/oneonone/ui/games/PongUi.kt`.
- Debug mirror: `app/src/debug/java/app/web/oneonone/DemoPong.kt`.
- Backend: `C:\Users\aarah\One-on-One\backend`; authoritative contract:
  `C:\Users\aarah\One-on-One\docs\API-CONTRACT.md`, section 12.
- Full investigation/evidence: `C:\Users\aarah\Game-Reverse-Engineering\reports\`:
  `SESSION-RESULTS.md`, `APKM-XAPK-COMPARISON.md`, `PINGPONG-NATIVE.md` and
  `ACTIVE-REBUILD-HANDOFF.md`. Evidence is under the adjacent `evidence/` directory.
- Reference runtime limitation: both bundles opened menus in an isolated AVD,
  but original Ping Pong gameplay was not reached/recorded. The gameplay video
  used for comparison was supplied by the owner.
