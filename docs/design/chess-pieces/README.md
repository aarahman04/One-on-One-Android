# Chess pieces (original artwork, 2026-10-20)

Drawn for One on One in a rounded Staunton style (chunky silhouettes, single rounded base, soft
side shading, dark outline). No third-party artwork; no licence attribution needed.

Regenerate: `python3 make.py` (writes `svg/*.svg` and `board.html`), then for each piece
`python3 svg2vd.py svg/wK.svg ../../../app/src/main/res/drawable/chess_wk.xml` (and so on for all 12).
`preview.png` is the starting position rendered from the SVGs.
