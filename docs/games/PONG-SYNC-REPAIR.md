# Ping Pong gameplay, sync and rendering repair - 2026-10-25

Owner request: repair racket-to-ball contact, opponent synchronization, sudden ball movements, the black
numbered circles and lag. The screen recording was **not available** to this session. The work below comes
from tracing the code and replaying two simulated players against the real server simulation.

Android PR #54 (branch `claude/ecstatic-pascal-txv258`, release candidate 2.0.14 / versionCode 21);
backend PR #131, merged as `4b9c73f`.

## 1. Existing architecture (as found)

| Question | Answer |
|---|---|
| Physics owner | Server `PongSim` (`backend/src/games/pong.ts`, `pongPhysics.ts`) at 60 Hz with 8 ms sub-steps. It owns collisions, flights, spin, bounces and scoring. |
| Client role | `PongClient` draws the server flight (a cubic `a` with its clock) and predicts only its **own** return locally. It never awards hits or points. |
| Racket input | `PongUi` → `GameViewModel` → `MatchSession.paddle` sends every 33 ms, or 16 ms near contact. Each sample has `ct` (client clock), `n` (sequence) and `v` (server time of the drawn ball), plus up to 32 prior samples. |
| Collision | Swept relative circles (`sweepContact`) between ball and racket segments. The server judges a timed sample against its 800 ms ball history at `v` (lag compensation, cap `clamp(RTT+100, 250, 500)` ms), then catches up. |
| Frames | `match:pong` at 30/s (volatile): ball, arc, both rackets (`p`, `r`), score, serve, countdown, hits `h`, bounces `j`, spin `sp`, `ack`, `t`. |
| Display | 50 ms interpolation on an estimated server clock, opponent racket interpolated from frames, 60 ms blend when a prediction is confirmed or rejected. |
| Packet issues | Older `t` is rejected. Lost frames are bridged by the arc. Paddle loss is recovered by `samples`. |
| Spin | Deterministic, captured at contact, so it does not cause the inconsistencies. The dark spin dot did read as a "black spot". |

## 2. Lifecycle of an opponent's shot and where it broke

1. The opponent draws the ball at server time `v` (their 50 ms buffer plus one-way latency behind) and swipes.
2. Their sample reaches the server about one-way later. `judge` finds contact at impact time `t <= v`, rewinds, launches the return from the rim at `t` and catches up to now.
3. The next frame (≤ 33 ms later) carries the new arc starting at `t`. It reaches the viewer after the viewer's one-way latency.
4. **Break A (ball):** the viewer draws at `newest - 50 ms`. By the time the post-hit frame arrived, the viewer had already drawn the old flight about 2 × one-way + 50 ms past `t`. The client then snapped onto the return mid-flight, so the ball went past the racket and jumped back.
5. **Break B (racket):** frames reported the opponent racket **as of its server arrival time**. The ball's turn sits at the hitter's ball time `t`, but the racket reaching the contact point was stamped up to their RTT + ~80 ms later. The ball reversed before the racket got there.
6. **Break C (events):** score, serve, countdown and bounce were applied when the packet arrived, ahead of the drawn ball. A point could appear before the miss was visible, and a reset teleported the ball.
7. **Break D (fragile frame pick):** the "post-impact frame" lookup used a 1 ms start-time tolerance with an early `break`. Any arc-clock rounding drift made it pick a newer frame of the same flight and miss the real return.

## 3. Repair

**Protocol (additive, backward compatible).** Paddle `w` is the sender's steady display time. It is cosmetic and never judged. Frame `pv` carries, for each seat, when the reported racket was sent, on that sender's axis. See the backend `API-CONTRACT.md` → "Ping Pong coherent display timeline".

**Client (`PongClient`, only when frames carry `pv`):**

- **Opponent racket:** drawn from its `pv`-stamped samples at `newest - F`. `F` is the largest recent `t - pv` plus 48 ms, in 50..500 ms. It covers their view lag, the frame interval, jitter and one lost frame. Past the newest sample it extrapolates at most 50 ms or 0.08 widths.
- **Ball:** drawn at `newest - D`. `D` moves to `F` while the ball goes to the opponent, so their hit, their racket and the turn happen together. It moves back to 50 ms while the ball comes to me, so my own hits rewind little. The change is spread over the flight and finishes by the landing where possible. The rate is capped at 75% faster coming in and 50% slower going out. A predicted local hit starts the change at contact.
- **Frame selection:** by flight identity (`h`, `j`) and start ≤ drawn time.
- **Events:** score, serve, countdown, round reset, trail reset, bounce ring and sound, and impact all happen at the drawn time.
- **Late frames:** a hit the buffer could not hide eases onto the new flight over 60 ms instead of jumping.
- **Sending:** `v` = drawn-ball time, so judging matches exactly what the player saw at any `D`. `w` = display time.
- **Frame stalls:** a local launch keeps moving at real time through a frame stall instead of jumping to the newest time.

**UI (`PongUi`):**

- Impact pulse on the racket that made the drawn contact: 140 ms, head +8%, a white ring. Drawing only.
- Hit sound and haptic at that moment.
- The dark 68 dp numbered countdown disc, `bgRaised` at 90% (black in dark theme), is replaced by a notice: "Your serve in 2" / "Sam serves in 2".
- A point notice explains the point ("Point to you · Sam missed") for 1.4 s.
- The spin dot is a soft grey instead of near-black ink.

No physics, judging, scoring or tuning value changed. Frames without `pv` (an old backend) use the previous display path unchanged; the golden display traces still pass.

## 4. Evidence

`scripts/pong-sync-trace.ts` runs two players against the real `PongSim` for 40 s per profile.

- **Links:** jitter, and 0/2/3/5% frame loss at 50/100/200/300 ms RTT.
- **What each player sees:** only frames its link delivered, drawn with the same timeline rules as the app.
- **Play:** each player tracks and swipes through incoming balls with varied speed and side spin, and deliberately misses about 30%.

`PongSyncReplayTest` feeds each seat's received frames and touches to the real `PongClient` at 60 Hz (and 120 Hz), once with `pv` and once with it stripped ("before").

| RTT | seat | before: ball jumps | now: ball jumps | before: turn gap p95 / max | now: turn gap p95 / max | before: racket jumps | now: racket jumps |
|---|---|---|---|---|---|---|---|
| 50 | 1 | 13 | 0 | .147 / .167 | -.002 / .000 | 0 | 0 |
| 50 | 2 | 10 | 0 | .111 / .168 | -.013 / -.008 | 0 | 0 |
| 100 | 1 | 11 | 0 | .024 / .048 | -.015 / -.013 | 2 | 0 |
| 100 | 2 | 12 | 0 | .003* / .114 | .003 / .055 | 0 | 0 |
| 200 | 1 | 13 | 0 | .023 / .383 | -.003 / .002 | 2 | 0 |
| 200 | 2 | 12 | 0 | -.009 / -.003 | -.012 / -.002 | 4 | 0 |
| 300 | 1 | 15 | 1 | .188 / .885 | .047 / .058 | 9 | 0 |
| 300 | 2 | 12 | 0 | .209 / .269 | .058 / .080 | 12 | 0 |

- **Ball jump:** more than 0.12 widths in one 60 Hz frame outside a point reset. Before the fix these were the snaps after every opponent hit.
- **Turn gap:** distance from the ball to the opponent's drawn racket minus the contact radius, at the frame where the ball turns back on their side. ≤ 0 means it visibly touched.
- **\* Before, small gaps hide the defect:** the old turn happened late, only once the racket had caught up, after the ball had overshot. That is what the jump count measures.

In every run, both phones showed each server point in order, from their own side, and never before the ball had passed the player who lost it.

Re-run with these commands:

```sh
cd ../One-on-One/backend && npx tsx ../../One-on-One-Android/scripts/pong-sync-trace.ts
./gradlew testDebugUnitTest --tests '*PongSyncReplayTest*'
```

## 5. Limits and what is not verified

- **No device or emulator play:** the cloud session has no Android SDK or emulator (dl.google.com is blocked), so nothing was played on a device. Gradle build, lint and the full unit suite run in PR CI. The Pong logic was compiled and tested locally with a standalone Kotlin compiler.
- **Simulated players:** the replay drives the real server and the real client, but the players are scripted. A player's sample `v` comes from a TypeScript mirror of the client timeline, not from the Kotlin client itself.
- **High latency speeds the ball up and down:** at 300 ms RTT the incoming ball may draw up to 75% faster for part of its flight, and the outgoing ball up to 50% slower. Up to about 200 ms the needed change is about 20–40%.
- **Racket gaps after lost frames:** two consecutive lost frames during a very fast swing can still produce one racket step after the 50 ms extrapolation.
- **Rejected predictions:** a local prediction the server rejects still eases back over 60 ms, by design, because the server decides.
- **Older apps:** old Android builds and the web client keep the old behaviour until they update.
- **Railway not checked from here:** the deployment could not be verified from this session, because the network policy denies the Railway host.
