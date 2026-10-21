# Game inventory (reference APK, 2026-10-21)

Source: `Offline Games.apk` (analysis outside this repo, `C:\Users\aarah\Game-Reverse-Engineering`).
`com.JindoBlu.OfflineGames` 3.15.3, Unity IL2CPP, **base split only**: manifest
`requiredSplitTypes="base__abi"`, no `lib/`, so `libil2cpp.so` (all game code) and `libunity.so` are in
a split this file lacks; `adb install` fails with `INSTALL_FAILED_MISSING_SPLIT`. JADX 1.5.6 decompiles
the DEX (36 848 classes): Unity bootstrap, ad/analytics SDKs, `com.junglefrog.shared` (activity, log,
ANR watcher) — no game logic. Recoverable without code: names (metadata v31) and serialized scene data
(`data.unity3d`). Nothing was copied into the app.

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

To recover actual game code: the `split_config.arm64_v8a.apk` from a phone with the game installed
(`adb shell pm path com.JindoBlu.OfflineGames`), then Il2CppDumper (metadata + libil2cpp.so) and
Ghidra/IDA for method bodies. Or observe gameplay on that phone.
