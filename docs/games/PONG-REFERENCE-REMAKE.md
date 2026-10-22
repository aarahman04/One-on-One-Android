# Ping Pong reference remake — 2026-10-22

The owner supplied an APK/APKM, decompiled folders and
`Downloads/Ref/ping pong ref video.mp4`, then requested a remake of the existing
Android game. This supersedes the 2026-10-21 mechanics/handoff. The implementation
uses Kotlin/TypeScript and procedural Canvas drawings; no reference textures,
audio or binaries are added to the app.

## Evidence and recovered behavior

Local recovery bundle:
`C:\Users\aarah\Downloads\APK game file\PingPong-Recovered`.
It contains readable `RecoveredPingPongCore.cs`, `SMOOTHNESS.md`, 68 decompiled
native functions, their assembly, method addresses and scene metadata. Analysis
and reference-video captures live in
`C:\Users\aarah\Game-Reverse-Engineering\apk-pingpong-2026-10-22`.
The extracted APK and APKM payloads were matched to version 3.15.3/code 3327;
ARM64 library SHA-256:
`80dbeb4bd8f5cd8e1f5c2590c410ac4a49defd56a5cd64a11dc455c18947d74e`.
The external reconstruction's 33 numeric checks passed. Decompiled output is an
algorithm reconstruction, not the original Unity project or original C# source.

- Racket follows halfway to the held touch per 60 Hz frame; Android converts the
  response to elapsed time for higher refresh rates. Stroke sampling uses the
  raw finger target, separately from the eased racket position.
- Table width is 12.16 native units. Power is forward world delta clamped
  [-.5,1], side parameter is clamped [-1,1], aim is normalized gesture x.
  Network sampling uses a 50 ms window; still touch, release and lost input clear
  the live stroke.
- Target x: `.5 + direction*(8/12.16)*(.7+.3*power)`; target y:
  `.9 + forwardSign*(5+7*power)/12.16`; target height: `-6/12.16`.
  Duration: `.9-.2*power` seconds. Cubic handles are 20% toward the endpoint and
  directly over the endpoint, both at height `6/12.16`. Time easing slopes are
  1.363 and .3368.
- Last hitter's live sideways parameter shifts the endpoint at
  `(0.3/12.16/0.02)*side` widths/second. Release stops drift; later movement can
  steer the same shot. Removed the earlier decay, random razor spin, bounce kick,
  incoming-spin bonus and deep-smash expansion.
- Bounce follows incoming 3D direction over `30/12.16` widths, ending one arc
  height below the contact height. Open sides, table landing between returns,
  contact-to-serve and alternating serves every two points remain. Existing
  configurable points, server ownership and lifecycle remain.

## Red-racket contact correction

The owner reported that pulling the red racket back then pushing it forward let
the ball cross its black outline. Earlier contact used nominal radii plus a
fixed tolerance, ignoring the painted strokes and height-scaled ball. A large
input step could also move a racket through the ball before the next physics
step, where back-face rejection then rejected the return.

Contact radius now equals `.137 + .025/2 + 1.35*ballRadius(height)`, with
`ballRadius = .033*(1+.7*clamp(height/(6/12.16),0,1))`. The same Kotlin helpers
drive drawing and client guarding. Both simulators clamp forward racket
movement at the front surface, preserve the requested finger motion for shot
power, and check existing contact before advancing. Incoming simulated contacts
resolve to the painted front surface. The Android renderer sweeps the incoming
ball against the local head, holding pending contact on the rim until authority
confirms the hit. It never awards a local hit, sound or score.

## Validation and delivery

The backend fixture and Android demo use identical input/time sequences to
check trajectory parity. Regression checks cover both-seat forward strokes,
pull-back/forward red returns, painted-rim rally contacts, whole-head display
crossings, side/back misses, live/released/still finger drift, cubic height,
refresh-rate response, packet jitter, seat rotation and retained hit trails.
`gradlew.bat assembleDebug lintDebug testDebugUnitTest` passed: 260 tests,
zero failures/errors. Backend `npm test` passed 124/124 on the final full rerun;
`npx tsc --noEmit` passed. An earlier full run exposed the existing
call-disconnect test's asynchronous event race (it waits for a saved row, then
immediately checks the client's event); isolated rerun and full rerun passed.
No call code or unrelated tests were changed. Both repos passed `git diff --check`.

The final debug APK was installed successfully on `emulator-5580` and
`.DemoChatActivity` was opened. No scripted gestures followed installation.
APK SHA-256:
`b9f1661a3cb423d35a592018ee4fe91f0737c346f4ae8ca5c7634d56d681503d`.

The separate `PongRemake_Check` emulator ran the demo; the owner confirmed the
game works before reporting the red-racket defect. Final physical-phone feel,
real-network latency and two-account multiplayer remain to be checked. See the
Xiaomi checklist in `../PROGRESS.md`. No FPS claim is made from unit tests.

Backend contract: sibling `One-on-One/docs/API-CONTRACT.md`. Optional `touch`
and `drift` are additive; missing fields retain legacy input/prediction behavior.
Deploy the paired backend with the matching Android release for the recovered
touch/flight behavior. No database migration, dependency, signing or version
change is required for this debug review. Web client files remain untouched.
