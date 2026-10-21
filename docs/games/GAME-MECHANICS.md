# Game mechanics (2026-10-21)

Confidence: **Verified** = our code + tests; **Inferred** = reference names only.

## Table tennis sidespin (Verified, `backend/src/games/pong.ts`)
- Return angle: contact point on the round head (up to 62°) + 30 % of racket sideways speed (unchanged).
- Spin `w` (units/s²) = 0.3 × racket sideways speed at contact, capped ±0.75.
- Flight: `vx += w·dt` each substep, `|vx| ≤ |vy|·tan 62°`; `w` decays (τ 0.6 s).
- Side-edge bounce sets `w = 0`; incoming spin turns the return by 0.25 rad per unit.
- Substeps < ball radius (no tunnelling, tested at max speed with 50 ms frames). First to 7, no deuce.
- Client extrapolates `x + vx·age + ½·w·age²` (≤150 ms) and rolls a seam at 900°/s per unit of spin.
- Reference: see "Reference Ping Pong" below.

## Reversi (Verified, `ReversiRules`)
- 8×8, seat 1 Black moves first; standard start: White d4/e5, Black d5/e4.
- A move must outflank ≥1 line in any of 8 directions; all outflanked discs flip.
- No move → automatic pass (`passed: true`, same player again); neither can move → end.
- Winner: most discs (`most_discs`); equal → draw (`equal_discs`).

## Connect 4 / tic-tac-toe / chess
Rules unchanged (see contract §12). Animations follow the server's `last`; nothing is drawn that the
server did not apply (own chess move is predicted with chesslib and reverted if rejected).

## Reference Ping Pong (2026-10-21, JADX + IL2CPP metadata + Unity serialized data)

Evidence: field order per class from `global-metadata.dat` (v31), serialized values of the scene's
MonoBehaviours, Transforms and colliders from `data.unity3d`. No method bodies exist in the APK
(native split missing), so **every rule about what the code does with these values is inferred**.
Values marked *serialized* are verified as stored data; their meaning comes from the Italian field name.

| Item | Value | Status |
|---|---|---|
| Scene | 3D: x across, y height, z along the table | Verified (transforms) |
| Table | box 12.16 wide × 21.92 long (ratio 1 : 1.80), top at y = 0 | Verified (BoxCollider) |
| Net | sprites only (`ReteBordo`, `ReteInterno`), **no collider** | Verified (scene tree) |
| Side walls | **none**; only colliders: table, two rackets, ball, two flat "player half" triggers (30 × 25) | Verified |
| Rackets | at z = ±12 (past the table ends ±10.96), box 4 × 10 × 4.5 + capsule r 1.67 | Verified |
| Racket travel | `vincoloSX/DX` at x = ±9.24, i.e. 1.52 × the table half-width (beyond the edges) | Serialized |
| Racket follow | `lerpVelocitaRacchetta` 0.5 (smoothing towards the finger) | Serialized; use inferred |
| Racket rotation | no rotation field; `angoloMassimoConsentitoGesture` 15° | Serialized; meaning inferred (swipe angle cap) |
| Shot input | swipe sampled: 30 samples (`numeroSampleVettoreTiro`) over 0.2 s | Serialized; use inferred |
| Ball flight | cubic Bézier template (start z −6, handles at height 3.99 / 2.86, end z 7, x +1.5), 0.9 s per shot (`tempoAnimazione`), eased by a curve with slopes 1.36 → 0.34 (fast off the racket, slowing) | Serialized; use inferred |
| Shot modifiers | direction 8, spin 0.3, power 7, bounce coefficient 30, arc height 6 | Serialized; formulas unknown |
| Ball | sphere trigger r 0.5 (visual r 0.4), height faked by shadow + scale | Verified / serialized |
| Score | `scoreToWin` 7 (shared `LevelController`) | Serialized; win-by-two unknown |
| Bot | speed range 0.15–0.20, difficulty bands easy 0.3–0.5, medium 0.4–0.6, hard 0.85–1.0, ±20 % random | Serialized; use inferred |

Needs gameplay observation (not possible: the APK cannot be installed without its native split):
serve rules, whether the ball must land on the far half, net/edge handling, deuce, how spin bends the curve.

## Reference turn games (same method)
- Connect 4: the disc is a physics body (`_pinSpeed`, `_pinBounciness` 0.15) — a small bounce on landing. Inferred.
- Reversi: disc appear scale 0.2 s (`_scalingDuration`), flip clip ≈ 0.33 s; legal squares are hinted
  (`mustTapHighlightedSquareToast`). Our 260 ms flip / dots match closely.
- Chess (`ControllerChess2`): draw offers, undo, bot think time. Nothing changes our server-ruled chess.
