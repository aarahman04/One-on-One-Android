# Codex task: permanently fix Ping Pong bat–ball contact (client + backend, one paired change)

Repos: Android `aarahman04/One-on-One-Android` (this repo, Kotlin/Compose) and backend
`aarahman04/One-on-One` (`../One-on-One`, Node/TypeScript). **The owner explicitly authorizes you to read AND edit
the backend for this task** (this overrides the "backend is READ-ONLY" line in `CLAUDE.md` for this task only).
Open one paired PR per repo, never merge, never deploy (the owner merges and the existing Railway pipeline
deploys). No migrations, no new dependencies, no signing/applicationId/versionCode-rule changes, no secrets.
Everything else in `CLAUDE.md` still applies (checks, PROGRESS/ARCHITECTURE updates, Xiaomi checklist, no false
device-test claims, stop and report when done).

The owner has spent weeks on this. Several earlier attempts tuned constants, added guards and freezes, and
made it worse. **Do not tune. Do not patch symptoms. Implement the design below, prove it with the harness, and
deliver once.** If a claim in this document turns out false when you check it, say so with evidence and adjust
the design; do not silently drop a requirement.

## 1. The only problem

Ball flight, speed, curve, bounce, spin/drift, scoring, serving, UI and sounds are accepted. Do not change them.
The defect is the **contact between bat and ball**:
1. The ball is visibly on/in the bat and passes straight through.
2. The ball visibly touches, then launches late.
3. The ball visibly jumps/"sticks" to a different point on the bat, then launches from there.
4. Different every time; worse on slower networks.

## 2. Verified root causes (from reading both repos — confirm each in Phase 1 before relying on it)

### A. No shared time: the server judges contact in a different moment than the player saw (dominant cause)
- Client sends bat positions as `match:paddle {matchId,x,y,released,touch}` at most every 33 ms
  (`MatchSession.paddle`, `PaddleIntervalMs = 33`). **There is no timestamp and no sequence.**
- Backend `PongSim.paddle()` (`backend/src/games/pong.ts`) applies the packet **immediately on arrival** and tests it
  against the ball's **current server position** (last 60 Hz tick). There is **no bat or ball history, no rewind.**
- The ball shown on the phone is the server state from ≈ one-way latency ago (`PongClient.receive/advance`:
  `frameAt = sentAt + minOffset`, `age` capped at .15 s). The packet that carries the bat to that ball arrives
  one more one-way later, plus up to 33 ms batching, plus up to one 16.7 ms server tick (Node `setInterval`, dt capped
  at 50 ms). **Net error ≈ full RTT + ~25 ms of ball travel.** Ball speed is ~1–2 table widths/s, so at 80 ms RTT the
  server's ball is ~0.1–0.2 widths from the one the player aimed at — the size of the bat head (`HeadR` .137).
  That is symptoms 1, 2 and 4.
- Frames are `volatile` at 30 Hz and may be dropped; `receive()` also re-anchors the clock to "fastest packet ever",
  which drifts as the network changes.

### B. Hit region is not what is painted
- `PongSim.touches()` and the `advance()` resolve step only accept a ball in the **front half** of the bat
  (`(y - padY) * forward >= -0.001`). The bat is painted as a **full circle**. A ball drawn inside the back half of
  the head cannot be hit → goes through. (Client `frontContact` / `y0 > py` has the same rule.)
- The judged radius is `HEAD_R + HEAD_OUTLINE/2 + 1.35 * ballRadius(h)` (≈ .195–.225), larger than the painted
  head + ball, and it varies with ball height, so a hit fires while a gap is visible.
- In `advance()` an overlapping ball is **teleported onto the front arc**:
  `y = padY + forward*sqrt(r² - across²)` with the ball's x unchanged. A ball that touched the side or back of the
  head is moved to the front rim before launching. That is symptom 3 (and the client preview draws yet another
  point).

### C. Preview and authority disagree, then snap
- Client freezes the ball at its own predicted rim point for ≤150 ms (`localContact`, `visibleBallPosition`),
  server later launches from its own point → snap. If the server disagrees, the ball snaps back through the bat and
  `localContactUsed` blocks retries until the next hit.
- Client eligibility (`returnReady` from the `l` flag) lags the server's `landed` by a frame + latency, so client
  and server disagree on when the ball is hittable.

### D. Contact math ignores ball motion and has four copies
- `sweepContact` is called with the ball as a **stationary point** both in `PongClient.paddle`, `DemoPong.paddle`
  and `PongSim.paddle`; real ball motion is only handled indirectly by the 8 ms sub-steps and `touches()`.
- Copies: `PongClient` (+ `frontContact`), `DemoPong` (debug), `PongSim`, `pongPhysics.ts` / `PongPhysics.kt`.
- Stroke speed for power/aim is computed from **server arrival time** over a 50 ms window
  (`PongSim.paddle`, `STROKE_WINDOW_MS`); network jitter bunches packets and changes the same swipe's strength/aim.

### E. Eligibility rules are invisible
Return only legal after a table bounce on your side (`landed`), and only while `z >= -BALL_R`. A ball drawn over
the bat before its bounce, or already below hittable height, silently passes.

### F. Rate limit
`match:paddle` is limited to 40/s per socket (`withinPaddleLimit`) and silently dropped. Client sends ≤30/s plus
releases (the release path emits two packets). Bunching can drop the **release** or the key sample. Never rely on a
single packet being delivered.

## 3. Required design (implement exactly this; deviate only with measured evidence)

Principle: **the server stays authoritative, but it judges every bat sample against the ball state that the player
was looking at when the sample was made (lag compensation), and the client predicts the identical result and
reconciles without a visible snap.**

### 3.1 Protocol (additive, optional; old clients keep working unchanged)
Add to `match:paddle` (document in `docs/API-CONTRACT.md` §12 and keep legacy behaviour when absent):
- `ct`: client monotonic time of the sample, integer ms, strictly increasing per socket.
- `v`: the **server time (the frame `t` axis) of the ball state the client was displaying** when the sample was made
  (`v = frame.t + ageMs`). Server clamps it to `[now - cap, now]`, never trusting the client. `cap` is per seat:
  `clamp(measuredRttMs + 100, 250, 500)`, with a hard maximum `MAX_COMP_MS = 500` (measure RTT server-side, e.g. from
  the socket.io ping or a sync probe; do not take it from the client). The worst required profile (150 ms each way)
  needs ≈ 400 ms (150 down + render delay + 150 up + 33 batching + 16 tick), so 250 ms is NOT enough. Beyond the cap
  the hit is not rewound; report that as a known limit, do not raise it silently.
- `n`: per-match increasing sequence number so reordered/duplicated packets are ignored and the server can detect gaps.
The existing `x,y,released,touch` fields keep their meaning. `match:pong` frames already carry `t`; also add the
server's current monotonic `t` precision needed for `v` (ms is enough) and the per-seat last processed `n` (`ack`)
so the client can reconcile predictions.

### 3.2 Backend (`PongSim`)
- Keep a ring buffer (≥ 700 ms, i.e. the hard cap plus margin) of **ball state per 8 ms sub-step**: `{t, x, y, z, landed, lastHit, hits,
  flightId}` and the previous packet per seat `{n, v, x, y}`. Reuse the existing 8 ms stepping so history is
  gap-free even when the 60 Hz timer jitters.
- On each paddle packet for seat S with `v`: build the bat segment from S's previous accepted packet to this one,
  and the ball segment from `ball(v_prev)` to `ball(v)` by interpolating the history. Run **one** swept test with
  **both moving** (relative-motion swept circle; same function used by the client). Eligibility
  (`landed`, `lastHit === other(S)`, `z >= -BALL_R`, not already struck this flight, no outcome) is evaluated **at the
  impact time inside the history**, not at "now". Reject if the ball already scored/was struck in the meantime and
  the history says it was not eligible at `v`.
- On a hit at historical time `t_i`: place the ball on the rim along the contact normal **at `t_i`** (no teleport to
  a "front arc"), compute the stroke (3.4), build the new flight starting at that rim point, then **fast-forward it
  by `(now - t_i)`** so position/elapsed are consistent with server time. Hit counters, `landed=false`, score, etc. as today.
- Remove the front-half gate. Once the ball is struck, `lastHit` makes it ineligible for the same seat, which is what
  prevents follow-through re-hits. The launch direction comes from the stroke, not the contact normal, so a hit from
  any side of the head launches forward. Keep a single explicit named tolerance `CONTACT_FORGIVENESS` (start ≤ one
  ball radius) on top of `HEAD_R + HEAD_OUTLINE/2 + ballRadius(h)`; delete the `1.35` factor everywhere. Painting and
  judging must use the same radius.
- Keep the 60 Hz `touches()`/sub-step check for packets WITHOUT `v` (legacy) and for a still bat the ball runs into,
  but resolve it with the same shared function and same rim rule.
- A still bat that the ball runs into must hit; a bat that sweeps through a still ball must hit; a fast diagonal
  swipe whose endpoints both miss but whose path crosses the ball must hit. These cases use the ONE shared function.
- Missing packets: if the server did not receive the packet that contained contact (gap in `n`), the next packet's
  segment (from the previous accepted position) still covers the path. A dropped `released` must not lose the hit:
  the next packet or the 120 ms idle rule must finalize it. Raise/rework the 40/s bucket so one coalesced
  release+sample is never dropped (e.g. 60/s with burst allowance); document the new limit.

### 3.3 Client (Android)
- Build `v` from the exact state being drawn: `v = frame.t + ageMs`. Replace the "fastest packet ever" anchoring in
  `receive()` with a bounded, smoothed offset estimate (e.g. median of the lowest-latency recent samples with slow
  decay) so `v` does not jump. Render the ball with a small fixed interpolation delay (≈ 1.5 packet intervals)
  rather than unbounded extrapolation; `v` must follow whatever you render.
- Use ONE shared contact function (Kotlin `PongPhysics` + TS `pongPhysics.ts`, with identical golden test vectors in
  both repos) for prediction and for the server.
- **Predict the launch**, do not freeze the ball: when the shared function says contact on the drawn ball and the
  drawn bat, immediately start the predicted return curve from the rim using the same stroke algorithm (3.4) and the
  same flight maths, and draw it. When the authoritative flight arrives, blend from the drawn position to the server
  curve over ≤ 60 ms (never teleport). If the server rejects (no hit/`hits` unchanged after the `ack` of that
  sample), resume the original server curve with the same ≤ 60 ms blend and allow another attempt (clear
  `localContactUsed`). Remove the 150 ms frozen-rim preview.
- Hit sound/haptic fire exactly once per authoritative `hits` increment (as today); prediction itself makes no sound
  or score.
- Sample touch input at pointer-event level using historical samples so a fast swipe is not under-sampled at
  60/90/120 Hz; send bat samples at ≥ 60/s when a contact is possible (ball within 2× contact radius and
  eligible), 30/s otherwise, always sending the latest and always a final `released`. No main-thread allocation or
  blocking in the per-frame path.
- Make the bat painting match the hit region (head circle + outline); if eligibility is not met (before the bounce,
  below hittable height), the player must be able to tell (e.g. ball dims/ring), never ambiguous.

### 3.4 Stroke strength/aim must not depend on packet arrival time
Compute the 50 ms stroke window from `ct` (client sample time), not from server arrival time, so jitter and
bunching cannot change power/aim for the same swipe. Mirror this in the client predictor with golden vectors so
predicted and authoritative launches are identical within rounding (`r4` rounding in frames).

## 4. Phase 0/1 — evidence first (deliverable: failing tests + before-numbers)
1. Confirm causes A–F by reading the code and by measurement; list any that are false.
2. Build a **deterministic harness** running the REAL `PongSim` (Node) and a TS model of the client display path
   (age/offset/render/`v`, validated against Kotlin `PongClient` with golden traces), with a simulated link:
   one-way latency 0/40/80/150 ms, jitter ±0/20/40 ms, 30 Hz bat sends (coalescing like `MatchSession`), 30 Hz
   volatile frame drops 0–10 %, 0–5 % paddle drops, server tick jitter, 60/90/120 Hz client frames. A scripted
   player moves the bat to the ball **as drawn** and swings (still bat, slow/medium/fast, diagonal, edge/back/front
   approach). Seeded, thousands of hits.
3. Metrics: `visibleOverlapMissRate` (bat head and drawn ball overlap ≥ 2 frames, no hit within 150 ms),
   `ghostHitRate` (hit while drawn gap > 1 ball radius), `launchSnapDistance` (drawn ball just before vs after
   confirmation), `contactToLaunchMs`, `strokeMismatch` (predicted vs authoritative target).
4. Run on the **current** code and record the table in the PR (expected to be bad). Commit tests that fail now.
5. Add a **debug-only** contact log (debug source set only; no release behaviour change) printing, per attempted
   hit: `v`, `ct`, drawn gap, predicted/confirmed, confirmation delay, snap distance — so the owner can reproduce on the
   Xiaomi and send logcat output if anything still misbehaves.

## 5. Acceptance (all must hold for every profile above)
- `visibleOverlapMissRate` ≤ 0.5 % (≤ 2 % at 150 ms latency with 5 % paddle loss and 10 % frame drops).
- `ghostHitRate` ≤ 0.5 %; no hit with a drawn gap > 1.5 ball radii.
- `launchSnapDistance` p99 ≤ 0.02 widths, max ≤ 0.05; no teleports.
- Visible launch never precedes visible overlap; with prediction, visible launch within 1 frame of overlap in
  ≥ 95 % of hits at ≤ 80 ms latency.
- `strokeMismatch` ≤ rounding for identical inputs; identical swipes with different jitter produce the same shot.
- Back-half, side and front-rim contacts all hit and launch forward; no double hit from follow-through.
- Anti-abuse: `v` outside `[now-cap, now]` (cap ≤ 500 ms) is clamped; packets with decreasing `n` ignored; a client claiming
  `v` in the past cannot hit a ball that was not eligible then, and cannot hit after a point has been scored
  unless the history shows the ball was live and eligible at `v`.
- Legacy path (no `v`/`ct`/`n`) still passes all existing backend and Android tests.
- Existing suites green (Android 276+, backend 130+), plus new tests for each of causes A–F.

## 6. Hard do-nots
Do not change ball trajectory/duration/curve/bounce/spin/scoring/serve/countdown/layout/sounds/table geometry.
Do not freeze or stop the bat or ball, do not push the bat back, do not enlarge hitboxes to hide gaps, do not add
timers/grace windows as fixes, do not make the client authoritative for score or ball, do not invent endpoints other
than the additive optional fields above, do not remove existing passing tests (adjust only where the contact
definition legitimately changed, and explain each).

## 7. Delivery
- Android branch + PR and backend branch + PR; link them to each other. Update `docs/games/PONG-REFERENCE-REMAKE.md`
  (contact section), `docs/PROGRESS.md`, `docs/ARCHITECTURE.md` (additive Mermaid sequence diagram: sample →
  `v`/`ct` → rewind judgement → ack → predicted blend), backend `docs/API-CONTRACT.md` §12 and its tests.
- PR descriptions: BEFORE/AFTER harness table per network profile, commands run and results, what is
  build-verified vs device-verified (no device claims), deployment order (backend first, then Android), and
  remaining gaps.
- Xiaomi checklist: (1) still bat, ball runs into it ×20; (2) slow sweep through ball ×20; (3) fast diagonal swipe
  ×20; (4) hit with left/right/front/back of the head ×10 each; (5) Wi-Fi and mobile data; (6) 60 Hz and 120 Hz
  screen if available; (7) any miss: note the time, run `adb logcat -s PongContact` and send the log.
- Then stop and wait for the owner.
