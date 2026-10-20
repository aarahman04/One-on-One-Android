# Game implementation plan (2026-10-21)

Done in order: 1 chess motion (Android #40) · 2 sidespin (backend #119 + Android) · 3 Reversi
(backend #119 + Android) · 4 Connect 4 / tic-tac-toe polish.

Design: rules/physics stay in the backend (`rules.ts`, `pong.ts`), pure and unit-tested; Android adds
read-only views in `data/games/Boards.kt` / `PongGame.kt` and Canvas boards in `ui/games`. No new
dependencies, no new transport: everything rides `match:*` through `InternetTransport`.

Later (not started): tune spin constants from reference evidence; Checkers as a 6th game; web client
support for `reversi` and `w` (web repo, read-only for this round).
