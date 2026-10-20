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

Reference vs ours: the reference's exact constants, win score and net rules are **unknown** (no code).
