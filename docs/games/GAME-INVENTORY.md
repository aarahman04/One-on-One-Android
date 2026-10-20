# Game inventory (reference APK, 2026-10-21)

Source: `Offline Games.apk` (analysis outside this repo, `C:\Users\aarah\Game-Reverse-Engineering`).
Unity IL2CPP, **base split only**: no `lib/`, so `libil2cpp.so` (all game code) is absent. Only class,
field and method names were recoverable from `global-metadata.dat`; nothing was decompiled, copied or run.

| Game (id) | Players | Notes for One on One |
|---|---|---|
| Chess (`CHESS2`) | 2P + bot | Ours is server-ruled (chess.js); kept |
| Ping Pong (`PINGPONG`) | 2P/bot | Names suggest Bézier shot flight, swipe-sampled power/spin |
| Tic-tac-toe (`TRIS`), Connect 4, Dots & Boxes | 2P + bot | Ours are server-ruled |
| **Reversi** | 2P + bot | **Chosen 5th game** (turn-based, one tap, low effort) |
| Checkers/Dama, Mancala, Backgammon, Domino, Battleship, Pool, Darts, Archery, Yatzy, Ludo, Soccer Stars, Guess The Person | 2P | Later candidates; Checkers next best |
| Solitaires, Sudoku, Wordle, sort/puzzle games | 1P | Out of scope |

Replacement shortlist: Reversi (low effort, same turn protocol) > Checkers (multi-jump UI) > Mancala >
Battleship (hidden state). All are public-domain rules with our own drawings.

Tooling: REA 6.7.0 works; its Android tools need `REA_JADX_MCP_JAR` (jadx-headless-mcp 0.7.1), not set.
Deeper evidence needs the APK's native split (pulled from a phone) or a gameplay recording.
