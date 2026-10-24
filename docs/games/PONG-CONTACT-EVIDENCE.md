# Contact evidence — 2026-10-24

Baseline: Android `6f8cd1c` (task branch adds only the prompt), backend `bf35268`.
Both origins fetched before work. Existing local CLAUDE.md edits and untracked
files are excluded from this change. Work order: Android, then backend.

## Findings before implementation

- A: confirmed. Paddle packets have no clock/sequence, collision uses the ball
  at arrival, and the client retains the minimum offset forever.
- B: front-half rejection and front-arc relocation confirmed. **The radius claim
  is false:** `PongUi` paints the black ball circle at `1.35 * ballRadius`; the
  server adds exactly that painted outer radius to the outlined head radius.
  Preserve this geometry, expressing the black edge explicitly rather than
  shrinking the painted ball or tuning the hitbox.
- C: confirmed. The fixed pending rim lasts 150 ms and cannot retry after expiry.
  The authoritative `l` flag controls return prediction.
- D: confirmed. The helper already supports relative ball/bat motion, but all
  paddle callers pass the same ball position twice. Stroke samples use arrival
  time despite the comment claiming the 50 ms window tolerates jitter.
- E: confirmed. Bounce/height eligibility has no visual indication.
- F: confirmed. A sliding 40/s bucket counts the two-packet release flush.

The specified 250 ms rewind cap cannot cover the specified 150 ms **one-way**
profile: 150 down + 50 interpolation + 150 up = 350 ms before jitter/batching.
The owner explicitly approved a larger bounded cap in this chat on 2026-10-24.
The implementation must retain validation/clamping and document its final bound.

## Final reproducible measurement

The BEFORE test commit is Android `6358728`, paired with backend regression
commit `ae56747`. Immediate-launch and back-half regressions failed before
production edits. Final comparisons run the same harness against original
backend `bf35268` (extracted directly with `git show` into a temporary directory)
and the paired candidate. The TypeScript baseline clock/curve path has a Kotlin
golden trace; the candidate has 28 generated action traces executed by the real
Kotlin client, including 60/90/120 Hz and the dropped-pre-bounce confirmation
regression (seed 67050). Identical contact/stroke/launch vectors are byte-for-byte
shared with backend tests. No simulator mock replaces PongSim.

72 profiles × 240 attempts = 17,280 attempts **per revision**, 34,560 total.
Links are seeded and ordered, with one-way latency 0/40/80/150 ms, jitter
±0/20/40 ms, zero loss or 10% volatile frame / 5% paddle loss, 30 Hz frames,
12–21 ms server ticks and 60/90/120 Hz drawing. The receiver aims at the drawn
ball: still block, slow sweep, hard sweep, left/right diagonals, back approach.
Contact uses rendered bat coordinates; native pointer scheduling and physical
screen latency remain device checks. Input coalesces at 33 ms, or 16 ms when
contact is possible, and flushes on release. A separate socket/session test
covers the real adaptive send and validated recovery path.

The original 3,456-attempt exploratory results remain in the first test commit.
The final table supersedes them: it measures visible confirmation on delivery
rather than comparing a delayed drawing against the server's current ball;
it also stops the gesture after release and checks the correction throughout
blending. No acceptance threshold was relaxed.

| One-way ms | Attempts each | Overlap miss % B → A | Ghost % B → A | Max snap/correction B → A | Worst profile launch p95 ms B → A |
|---:|---:|---:|---:|---:|---:|
| 0 | 4,320 | 0.50 → 0 | 1.92 → 0 | 0.7104 → 0.0026 | 102 → 0 |
| 40 | 4,320 | 32.58 → 0 | 17.13 → 0 | 0.5894 → 0.0064 | 179 → 0 |
| 80 | 4,320 | 70.86 → 0 | 16.46 → 0 | 0.6212 → 0.0067 | 244 → 0 |
| 150 | 4,320 | 84.98 → 0 | 34.10 → 0 | 0.7258 → 0.0192 | 373 → 0 |

The rates above average the 18 profiles at each latency; the complete per-profile
BEFORE/AFTER table is [pong-contact-profiles.md](pong-contact-profiles.md), with
raw values in `pong-contact-before.json` and `pong-contact-after.json`.
AFTER assertions pass every profile: overlap miss/ghost rates zero, no early
launch, contact-to-visible-launch p95 zero, no unconfirmed returns, stroke-target
error below 0.0000003 widths. Largest correction step 0.0192; largest per-profile
p99 below 0.02. These are deterministic simulation results, not device results.

### Metric definitions

- Overlap miss: at least two overlapping drawn frames but no visible return
  within 150 ms, or no eventual authoritative return. Denominator is attempts
  with visible contact (including a swept impact between draws). Every AFTER
  attempt has contact. Confirmation RTT is separately logged; it is not visible
  launch delay, since at 150 ms one-way an ack necessarily takes over 300 ms.
- Ghost: positive drawn gap over one base ball radius at the visible launch.
  BEFORE samples the incoming drawing immediately before first confirmation;
  AFTER samples the actual swept impact, including strokes with disjoint
  endpoints. Maximum gap is also asserted ≤1.5 ball radii; AFTER maximum is
  floating-point noise (<0.000000000000001 widths).
- Snap: immediate receipt discontinuity plus the largest per-draw correction
  introduced during the ≤60 ms blend, excluding ordinary travel on the curve.
  Candidate receipt discontinuity is zero. Blend starts from the predicted
  curve evaluated at the current draw time, so it does not add a one-frame pause.
  If pre-bounce return frames are all lost, keep the predicted launch until the
  confirmed curve is valid on the displayed time axis, then blend for 60 ms;
  never blend back to an older incoming flight. No physics speed is tuned.
- Stroke mismatch: initial predicted vs actual PongSim strike target/duration,
  before ongoing steering drift. Probe exists only in the harness. Identical
  client-time input is replayed through both algorithms; r4 tolerance is 0.0001.
  BEFORE has no predicted flight target, so this metric is unavailable there.

### Approved deviations, bounds and compatibility

1. `MAX_COMP_MS=500`, history 800 ms / 160 bounded entries, per-seat
   `cap=clamp(serverMeasuredRttMs+100,250,500)`. The required worst
   profile needs ≥350 ms before jitter/coalescing; the owner approved a larger
   bounded cap. Rewind eligibility, current flight identity, clamp and replay
   checks remain mandatory. A scored point is never rolled back.
   Final fetch also incorporated prompt update `e60c049`, which requires the
   adaptive per-seat cap. The server measures OPEN→namespace CONNECT and existing
   Engine.IO PING→PONG elapsed time, retains the maximum of eight recent probes,
   and uses 250 ms until a measurement exists. It never reads a client RTT value.
   The harness exercises measured caps with eight seeded round-trip probes per
   trial. Input beyond the cap is clamped and may miss; this is a known limit,
   and the cap is never silently extended.
2. Optional `samples` window of at most 32 prior tuples, validated completely
   before mutation; the current sample stays in the original outer fields.
   Each tuple is `[n,ct,v,x,y,touchX,touchY,released01]`. Lost out-and-back contact
   and release samples are recovered by the next packet; duplicates are skipped.
   ct/v/n alone lost the stroke predecessor under 5% drops: target disagreement
   0.5756 widths and correction 0.203 widths. The owner approved redundancy after
   that measured failure. Only paddle payloads get an 8192-byte limit; other
   match events retain 2048 bytes. Token bucket is 64/s, capacity 96, allowing
   62.5/s near-contact sampling and coalesced release bursts while bounding floods.
3. Cause B's geometry claim was false: black ball paint already extends 35%
   beyond white. Preserve the exact painted outer radius as `1 + BALL_EDGE`
   (`BALL_EDGE=.35`, `CONTACT_FORGIVENESS=0`), rather than resize ball or hitbox.

Legacy x-only and untimed 2D inputs keep their existing control/serve path, now
using the same full-circle impact-time rim rule. Browser code is unchanged and
continues using that legacy path. Existing clients keep working with the additive backend fields. Deploy the
companion backend before the new Android client for timed contact and the larger
validated paddle payload. Client prediction never awards score/sound/haptic: only authoritative
hit increments trigger the existing UI feedback. The owner approved the working contact on the emulator, then requested the ball
remain white throughout play. The eligibility dimming cue and its accessibility
text were removed on 2026-10-24; contact eligibility itself is unchanged.

### Existing assertions legitimately changed

No existing test case was removed. Client tests that required a fixed 150 ms rim,
expiry back to the incoming ball, or an immediate confirmation teleport now
require a moving predicted launch, unchanged receipt position and reconciliation.
The old front/back rejection check now covers the entire head. Both-seat,
follow-through, trail, scoring, speed, spin, bounce and lifecycle cases remain.
Backend/demo endpoint-rim assertions now inspect impact-time origin instead of
the final bat centre; a sub-step may already have advanced the new curve.
The shared trajectory fixture changes only x/y origin-derived values
`(.723,.3359,.2378)` → `(.5318,.3168,.2378)` because contact no longer relocates to
the final head position. Duration/height, cubic coefficients, stroke/aim, drift,
bounce formula and scoring rules stay the same.

### Reproduction and checks

From `../One-on-One/backend` (uses its installed tsx; no added dependencies):

```powershell
node --import tsx ../../One-on-One-Android/scripts/pong-contact-harness.ts ../../One-on-One-Android/docs/games/pong-contact-before.json
node --import tsx ../../One-on-One-Android/scripts/pong-contact-harness.ts ../../One-on-One-Android/docs/games/pong-contact-after.json --modern
node --import tsx ../../One-on-One-Android/scripts/pong-contact-harness.ts --trace
npm test
npm run build
```

From Android: `./gradlew.bat assembleDebug lintDebug testDebugUnitTest --console=plain --max-workers=1`.
Final build results are recorded in PROGRESS. An early pair of concurrent Gradle
runs conflicted in generated test classes; all later Android checks are serial.
No physical-device run, merge, release or deployment was performed.

### Xiaomi checklist (owner; debug APK with paired backend)

- [ ] Still bat, ball runs into it ×20.
- [ ] Slow sweep through ball ×20.
- [ ] Fast diagonal swipe ×20.
- [ ] Hit left/right/front/back of the head ×10 each.
- [ ] Repeat on Wi-Fi and mobile data, from both players' seats.
- [ ] Repeat at 60 Hz and 120 Hz if available; check a consistently white ball,
  continuous follow-through and one authoritative sound/haptic per hit.
- [ ] Any miss: note the time, run `adb logcat -s PongContact`, and send the log.
  Debug logs include v, ct, impact drawn gap, prediction/confirmation, delay and
  raw reconciliation offset (ordinary flight travel excluded). Release logger is
  a no-op; no identifiers, message content or touch history are logged.
