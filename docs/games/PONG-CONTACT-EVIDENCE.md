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

## Baseline measurement

`scripts/pong-contact-harness.ts` runs the real sibling `PongSim` with a scripted
receiver and a TypeScript copy of the existing Kotlin display path. Its clock /
curve trace is shared with `PongContactContractTest`. It uses seeded links,
ordered delivery, 30 Hz volatile frames and bat coalescing, 0/40/80/150 ms one-way
latency, ±0/20/40 ms jitter, 60/90/120 Hz rendering, zero loss or 10% frame + 5%
bat loss, and jittered 12–21 ms server ticks. 72 profiles × 48 trials = 3,456
attempts: still, slow, medium/fast, diagonal from either side, and back approach.
Full profile rows are in `pong-contact-before.json`.

| One-way ms | Attempts | Overlap misses % | Ghost hits % | Max confirmation snap (widths) | Worst profile launch p95 ms |
|---:|---:|---:|---:|---:|---:|
| 0 | 864 | 0.69 | 0.58 | .5180 | 117.0 |
| 40 | 864 | 28.47 | 12.38 | .5851 | 182.5 |
| 80 | 864 | 32.75 | 32.64 | .6036 | 246.5 |
| 150 | 864 | 49.42 | 24.31 | .7270 | 385.5 |

These initial rates use all scripted attempts as denominator; per-profile raw
overlap counts are retained. They are simulation evidence, not device results.
An overlap miss means ≥2 drawn overlap frames and no server return within 150 ms;
ghost means a server hit with a drawn gap exceeding one base ball radius. Snap
is the display discontinuity on the first confirming frame, not later travel.
This baseline does not yet measure stroke mismatch and does not establish final
acceptance. The final harness must report that metric and its acceptance checks.

Before production changes, both new immediate-launch/back-half Android checks
failed. Backend `npm test` passed 130/130; `npm run build` passed. An overlapping
pair of Android Gradle runs interfered in generated test classes; subsequent
Android runs are serialized. No device testing, merge, release or deployment.
