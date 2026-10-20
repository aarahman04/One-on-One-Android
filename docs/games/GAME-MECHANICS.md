# Game mechanics (2026-10-21)

Confidence: **Verified** = our code + tests; **Inferred** = reference names only.

## Table tennis sidespin (Verified, `backend/src/games/pong.ts`)
- Return angle: contact point on the round head (up to 62°) + 30 % of racket sideways speed (unchanged).
- Spin `w` (units/s²) = 0.3 × racket sideways speed at contact, capped ±0.75.
- Flight: `vx += w·dt` each substep, `|vx| ≤ |vy|·tan 62°`; `w` decays (τ 0.6 s).
- Side-edge bounce sets `w = 0`; incoming spin turns the return by 0.25 rad per unit.
- Substeps < ball radius (no tunnelling, tested at max speed with 50 ms frames). First to 7, no deuce.
- Client extrapolates `x + vx·age + ½·w·age²` (≤150 ms) and rolls a seam at 900°/s per unit of spin.
- Reference (Inferred): Bézier flights, fake height by scale + shadow, swipe vector sampled over a short
  window with power/direction/spin modifiers; CPU with random speed and aim offset. Constants unknown.

## Reversi (Verified, `ReversiRules`)
- 8×8, seat 1 Black moves first; standard start: White d4/e5, Black d5/e4.
- A move must outflank ≥1 line in any of 8 directions; all outflanked discs flip.
- No move → automatic pass (`passed: true`, same player again); neither can move → end.
- Winner: most discs (`most_discs`); equal → draw (`equal_discs`).

## Connect 4 / tic-tac-toe / chess
Rules unchanged (see contract §12). Animations follow the server's `last`; nothing is drawn that the
server did not apply (own chess move is predicted with chesslib and reverted if rejected).
