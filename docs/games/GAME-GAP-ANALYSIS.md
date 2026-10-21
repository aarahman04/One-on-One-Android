# Game gap analysis (2026-10-21)

All games are server-authoritative (`backend/src/games`); the app renders `match:*` state.

| Game | Before | Verdict | Change this round |
|---|---|---|---|
| Chess | Full FIDE rules on server (pins, castling, e.p., promotion, mate/stalemate, insufficient material, threefold/50-move **claims**, fivefold/75-move **automatic**) | Preserve | Smoother motion only (PR #40) |
| Table tennis | Flat top-down sim, swing angles the return, no spin | Improve | Sidespin that curves the flight |
| Connect 4 | Correct rules; one spring for every drop height | Polish | Gravity-timed drop, hop, win ring after landing |
| Tic-tac-toe | Correct rules | Polish | Win line after the last mark |
| Dots & Boxes | Correct | Replace in hub | Still decodes for older apps |
| Reversi | — | New | Server rules + board |

## Ping Pong: reference data vs ours (2026-10-21)
| | Reference (data) | Ours | Worth changing? |
|---|---|---|---|
| Table ratio | 1 : 1.80 | 1 : 1.8 | Same |
| Win score | 7 | 7 | Same |
| Net | visual only | visual only | Same |
| Side walls | none (ball can leave the table) | ball bounces off side edges | **Proposed** (owner decision): out over a side edge = point to the other player; rackets may reach past the edges (as the reference, 1.5 × half-width); needs a smaller max angle (≈30°) and a narrower serve so play stays on the table |
| Shot model | timed Bézier flight from a swipe vector | continuous sim, contact point + swing + spin | No: ours is server-authoritative and works for two phones |
| Racket follow | smoothed (lerp 0.5) | exact finger | No: exact is more responsive over the network |
| Spin | modifier 0.3 on the swipe | swing at contact → curve | Ours is already equivalent in spirit |

Other games: no rule differences found; animations now match the reference's intent (disc bounce, flip).
