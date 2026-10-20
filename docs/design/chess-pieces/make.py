# Original One on One chess pieces: rounded Staunton style, soft side shading, dark outline. viewBox 0 0 100 100.
import os
OUT = 'svg'
os.makedirs(OUT, exist_ok=True)
SW = 3.2
BASE = ['<rect x="18" y="79" width="64" height="12" rx="5"/>']
def body(top, wtop, wbot=25, y=80):
    # concave flare from a narrow neck (half-width wtop at y=top) to the plinth
    return f'<path d="M{50-wtop} {top} C{50-wtop} {top+10},{50-wbot} {y-8},{50-wbot} {y} L{50+wbot} {y} C{50+wbot} {y-8},{50+wtop} {top+10},{50+wtop} {top} Z"/>'
def band(y, hw, h=7):
    return f'<rect x="{50-hw}" y="{y}" width="{2*hw}" height="{h}" rx="3"/>'
P = {
 'P': [body(55, 11), band(49, 17), '<circle cx="50" cy="32" r="15"/>'],
 'R': [body(48, 16, 26), band(42, 22), '<path d="M26 14 H37 V21 H44 V14 H56 V21 H63 V14 H74 V36 Q74 43 67 43 H33 Q26 43 26 36 Z"/>'],
 'B': [body(60, 11), band(54, 18), '<path d="M50 14 C29 25,25 43,33 55 H67 C75 43,71 25,50 14 Z"/>',
       '<circle cx="50" cy="11" r="6"/>', '<path class="cut" d="M63 24 L46 42"/>'],
 'Q': [body(60, 14, 26), band(54, 22), '<path d="M29 55 L15 28 L34 42 L32 19 L45 38 L50 13 L55 38 L68 19 L66 42 L85 28 L71 55 Z"/>'] +
      [f'<circle cx="{x}" cy="{y}" r="6"/>' for x, y in [(15, 27), (32, 18), (50, 11), (68, 18), (85, 27)]],
 'K': [body(59, 14, 26), band(53, 22), '<path d="M28 54 C16 42,22 24,38 26 C44 27,48 30,50 34 C52 30,56 27,62 26 C78 24,84 42,72 54 Z"/>',
       '<path d="M46 2 H54 V8 H60 V16 H54 V25 H46 V16 H40 V8 H46 Z"/>'],
 'N': ['<path d="M71 80 C76 62,80 42,70 27 C65 19,57 14,49 13 L46 4 L39 14 C29 19,22 29,16 41 C13 47,16 53,22 53 L33 52 C38 51,42 49,45 46 C43 57,36 67,31 80 Z"/>',
       '<path class="line" d="M58 18 C70 29,73 47,69 66"/>', '<circle class="dot" cx="39" cy="27" r="2.8"/>', '<circle class="dot" cx="21" cy="46" r="1.5"/>'],
}
COLORS = {'w': ('#FFFFFF', '#D2D2D2', '#2F2F2F', '#2F2F2F'), 'b': ('#6E6E6E', '#3C3C3C', '#232323', '#232323')}
for side, (c1, c2, line, detail) in COLORS.items():
    for k, shapes in P.items():
        parts = []
        for s in BASE + shapes:
            if 'class="cut"' in s:
                parts.append(s.replace('/>', f' fill="none" stroke="{detail}" stroke-width="{SW * 2.2}" stroke-linecap="round"/>'))
                parts.append(s.replace('/>', f' fill="none" stroke="{c1}" stroke-width="{SW * 0.9}" stroke-linecap="round"/>'))
            elif 'class="line"' in s:
                parts.append(s.replace('/>', f' fill="none" stroke="{detail}" stroke-width="{SW}" stroke-linecap="round"/>'))
            elif 'class="dot"' in s:
                parts.append(s.replace('/>', f' fill="{detail}"/>'))
            else:
                parts.append(s.replace('/>', f' fill="url(#g)" stroke="{line}" stroke-width="{SW}" stroke-linejoin="round"/>'))
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><linearGradient id="g" gradientUnits="userSpaceOnUse" x1="30" y1="0" x2="76" y2="0">'
               f'<stop offset="0" stop-color="{c1}"/><stop offset="1" stop-color="{c2}"/></linearGradient></defs>' + ''.join(parts) + '</svg>')
        open(f'{OUT}/{side}{k}.svg', 'w').write(svg)
order = 'RNBQKBNR'
cells = []
for r in range(8):
    for c in range(8):
        bg = '#EBECD0' if (r + c) % 2 == 0 else '#739552'
        p = ('b' + order[c]) if r == 0 else 'bP' if r == 1 else 'wP' if r == 6 else ('w' + order[c]) if r == 7 else None
        img = f'<img src="svg/{p}.svg" width="100" height="100">' if p else ''
        cells.append(f'<div style="width:100px;height:100px;background:{bg}">{img}</div>')
open('board.html', 'w').write('<html><body style="margin:0"><div style="display:grid;grid-template-columns:repeat(8,100px)">' + ''.join(cells) + '</div></body></html>')
