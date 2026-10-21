# Game mechanics (2026-10-21)

Confidence: **Verified** = our code + tests; **Inferred** = reference names only.

## Ping Pong rebuild (2026-10-21)

The owner rejected the earlier wall-bouncing game, supplied a 52 s reference video, and approved
replacement with an open table. This section supersedes the earlier sidespin implementation.

- Server-owned 3D cubic flights: two raised controls, below-table endpoint, native Hermite easing.
- Move the racket in two dimensions on your own half, including outside the baselines and sides.
  Swipe through the ball to aim; a forward stroke changes depth/speed; brushing sideways curves it.
  The displayed racket tilt follows the gesture up to 55 degrees and eases back after release.
  Stronger lateral strokes transfer spin at contact; gentle placements remain spin-free. Airborne
  drift is gentle (.4 gain), with a one-time lateral kick at the table bounce (spin ? .8). Spin
  persists through release/bounce and gradually fades (1.8 s time constant).
  Light brushing starts at .1 spin; a hard brush caps at .2, about twice as much. Normal blocks stay spin-free.
  Later racket motion cannot redirect an already-hit ball; this deliberately improves on the reference.
- Aim gain .36 leaves more room for angled shots to land; duration .88..1.06 s softens the pace.
- The ball must land on the table before the opponent can return it. Bounce follows its incoming
  3D direction. Sides/corners are open; no wall reflection. A table miss loses the point; landing
  followed by escape or another bounce wins for the last hitter. Net is decorative.
- Serve by touching the waiting ball after the countdown (3 s first, 1 s thereafter). Server changes
  every two total points. First to 7, no deuce. A rematch swaps seats and resets the game.
- Accepted hits launch outside the drawn bat along the outgoing shot. Confirmed contacts snap to
  the server flight, avoiding a blend through the head. A forward stroke meets the front face after
  a table landing, then follows through on the confirmed return; back-face contacts are rejected.
- Android renders actual server height/curve, a shadow, short trail, and one pulse per table bounce.
  It predicts for at most 150 ms; it never holds the ball at a racket or invents a bounce.
- Release/cancel clears the active stroke through the same 33 ms input throttle. New gestures replace
  pending input; pause, result, reset and rematch discard it. Backend rejects paused/invalid input.
- Protocol remains `pong`; optional y and additive height/curve fields are documented in contract ?12.
  Older clients decode it but need the new renderer/controls for faithful gameplay. Web is untouched.

## Reversi (Verified, `ReversiRules`)
- 8×8, seat 1 Black moves first; standard start: White d4/e5, Black d5/e4.
- A move must outflank ≥1 line in any of 8 directions; all outflanked discs flip.
- No move → automatic pass (`passed: true`, same player again); neither can move → end.
- Winner: most discs (`most_discs`); equal → draw (`equal_discs`).

## Connect 4 / tic-tac-toe / chess
Rules unchanged (see contract §12). Animations follow the server's `last`; nothing is drawn that the
server did not apply (own chess move is predicted with chesslib and reverted if rejected).

## Reference Ping Pong: native evidence (2026-10-21)

Both complete packages contain ARM64 `libil2cpp.so` and compatible metadata. The APKM is older
(3.6.4/code 2682) than the XAPK (3.15.3/code 3327). Their binaries differ, but the recovered Ping Pong
algorithms match. The newer XAPK was used for method maps plus ELF-mapped Capstone disassembly;
Il2CppDumper metadata maps and UnityPy scene data provide independent checks. Raw Cpp2IL disassembly
used the wrong file offset for these libraries and was not treated as instruction evidence.

**Verified from native instructions and scene data:** live per-frame touch delta; OnKeep gesture
normalization/power/spin; release clears parameters; cubic controls and duration; live endpoint spin;
post-table-bounce racket eligibility; bounce direction; floor fault/scoring; serve alternates every
two points; first to seven. Stored 30-sample/0.2 s/15-degree fields are not the active input formula.
Table is 12.16 ? 21.92; arc height 6; floor -6; direction gain 8; power gain 7; bounce length 30;
spin endpoint delta .3 per native fixed step; racket world x +/-9.242, own z 5..20, height 2.
No net collider or side walls exist. Supplied video independently confirms 2D racket travel,
contact-to-serve, arcing flight, shadow, open edges and scoring.

**Adaptations:** independent server code; table-width coordinates; 60 Hz conversion of gesture
velocity; .025 collision tolerance and <=8 ms substeps; bounded network prediction; original Canvas
art. Reference CPU difficulty behavior and shared-manager initial-server selection are not copied.
Initial server is random and the debug opponent is a simple bounded-speed bot.

The untrusted packages installed and opened in an isolated, account-free emulator; the Ping Pong
entry was not reached in the bounded runtime attempt. Do not describe the supplied video as a
recording made by this session. Full addresses, pseudocode, confidence and hashes are recorded in
`C:\Users\aarah\Game-Reverse-Engineering\reports\PINGPONG-NATIVE.md` and `APKM-XAPK-COMPARISON.md`.

## Reference turn games (same method)
- Connect 4: the disc is a physics body (`_pinSpeed`, `_pinBounciness` 0.15) — a small bounce on landing. Inferred.
- Reversi: disc appear scale 0.2 s (`_scalingDuration`), flip clip ≈ 0.33 s; legal squares are hinted
  (`mustTapHighlightedSquareToast`). Our 260 ms flip / dots match closely.
- Chess (`ControllerChess2`): draw offers, undo, bot think time. Nothing changes our server-ruled chess.
