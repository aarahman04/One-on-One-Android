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

## Final contact and strength handling (2.0.11 / code 18)

This supersedes the earlier racket-stopping guard. Testing with the owner found
four distinct problems: endpoint-only collision missed diagonal swipes; clamping
the bat caused a pause on a hard swing; moving the bat out of an interpolated
ball made it jump backward; and repeatedly rebasing a flight or projecting its
display around a bat could recapture an outgoing ball at either corner.

Contact radius is `.137 + .025/2 + 1.35*ballRadius(height)`, with
`ballRadius = .033*(1+.7*clamp(height/(6/12.16),0,1))`. Shared relative swept-circle
math catches the complete bat path, including endpoints that both miss. Only
the server's eligible serving player or receiver after a table landing can
record a contact. The contact resolves the ball to the bat rim and captures its
stroke once. The bat continues to its requested position. Further input before
the next physics tick cannot move that impact again; the tick consumes the
captured contact even if the bat has already followed through. Once launched,
the last hitter cannot recapture or restart the outgoing flight.

The recovered forward parameter still controls the existing target formula;
aiming, live sideways endpoint drift, cubic handles/easing and table-bounce
math remain. Speed power is now a separate `clamp(forwardFingerSpeed/10,0,1)`;
flight duration is `1.15-.45*speedPower` seconds. Light or neutral touches take
1.15 s and a hard hit retains the .7 s maximum-speed duration. Strength is
captured at contact before release clears live spin, so lifting a finger does
not turn a hard hit into a neutral one. The recovered .9-.2*forward duration
above describes the reference, not this owner-adjusted release setting.

Android predicts one fixed world-space impact for at most 150 ms. A confirmed
hit or round reset clears it; expiry cannot re-arm the same pending contact.
The preview does not follow later bat movement. After confirmation the visible
ball uses only the server flight, without circular ball-to-bat display
projection. Input, network snapshots and rendering never push the local bat
backward or pause it at contact. Neither a preview nor touch awards a local
hit, sound or score. The ball and its tapered white trail are drawn before the
bats, so a bat can briefly cover the ball at contact as in the reference.

One pointer owns a stroke. Its final up position is applied; cancellation uses
the actual bat position, a second finger cannot steal control, and pause/new
rounds cancel the held gesture. Release immediately flushes any pending held
sample before its final position. Trail points expire by age after .3 s,
including when motion or frame updates stop. Bounds still include the orange
area; scoring, custom target, first-to-7, serve alternation and UI layout stay.

## Validation and delivery

Final local checks: `gradlew.bat assembleDebug lintDebug testDebugUnitTest`
passed with **276 tests**, zero failures/errors/skips. Backend `npm test` passed
**130/130** and `npx tsc --noEmit` passed. The simulator and Android demo share a
rounded trajectory fixture. Swept math covers slow, medium and extreme swipes,
several angles, moving/resting balls and 30/60/120 Hz steps. End-to-end backend
socket tests retain identical authoritative frames for two clients.

The key regressions compare hard and slow corner follow-through at both sides
against an untouched control flight with identical raw stroke inputs: moving
the bat cannot change ball position, restart the curve or add another hit.
Additional checks cover several packets before one tick, free bat movement,
strength on release, fixed pending previews, confirmed-flight rendering,
pointer ownership/cancel, trail expiry, open sides, serve and scoring behavior.

The latest debug APK was installed on `emulator-5580` (`PongRemake_Check`) and
`.DemoChatActivity` launched successfully without a startup crash. Physical
Xiaomi feel and real-network two-account latency remain owner checks; no FPS
claim is made from unit tests. See the checklist in `../PROGRESS.md`.

Delivery uses Android [#50](https://github.com/aarahman04/One-on-One-Android/pull/50)
and backend [#127](https://github.com/aarahman04/One-on-One/pull/127). Manual release
workflow dispatch is deferred at the owner's request. When requested later, build
signed `android-build` from merged main for **18 / 2.0.11**. Download that run's
`android-release` artifact for the AAB, APK and R8 mapping; building does not
upload to Play. The existing application ID and signing certificate remain.

Backend contract: sibling `One-on-One/docs/API-CONTRACT.md`. Optional `touch`
and `drift` remain additive; message formats and legacy x-only input behavior
are unchanged. The paired backend is required for the contact/strength fixes;
use the existing Railway deployment pipeline. No migration, dependency or
website-client change is needed.
