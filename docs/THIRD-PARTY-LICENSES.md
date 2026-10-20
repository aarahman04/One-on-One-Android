# Third-party licenses

## Games

- [chesslib 1.3.7](https://github.com/bhlangonijr/chesslib/tree/1.3.7),
  Copyright 2017 Ben-Hur Carlos Vieira Langoni Junior, Apache-2.0.
  Unmodified dependency, resolved through the group-restricted JitPack repository.
  Its license and attribution ship in `app/src/main/assets/licenses/chesslib/`.
- Apache Commons Lang 3.18.0 (chesslib transitive dependency), Apache-2.0.
  Upstream license and NOTICE ship in `app/src/main/assets/licenses/commons-lang3/`.
- Chess pieces: "Chessnut" piece set by Alexis Luengas
  (https://github.com/LexLuengas/chessnut-pieces), Apache-2.0. Converted from SVG to Android
  vector drawables (`res/drawable/chess_*.xml`, paths unchanged). License in
  `app/src/main/assets/licenses/chessnut/`.
- Game sounds (`res/raw/chess_*.ogg`, `game_end.ogg`) were synthesized for One on One (no third-party audio).

Existing sticker attribution remains in `app/src/main/assets/stickers/NOTICE.txt`;
font attribution remains in `docs/design/fonts-LICENSE.txt`.

## Server-side speech models (not shipped in the APK)
- Kokoro-82M (hexgrad), Apache-2.0 — letter narration, voice `af_heart`.
- OpenAI Whisper base.en (int8 ONNX export by sherpa-onnx), MIT — speech-to-text since 2.0.7 (replaced NVIDIA Parakeet TDT 0.6B v3, whose CC-BY-4.0 attribution is no longer needed).
