# SVG -> Android VectorDrawable for the "celtic" piece set: paths, CSS classes, linear gradients (affine
# axis-aligned gradientTransform, xlink:href stops), opacity, even-odd fill.
import re, sys, xml.etree.ElementTree as ET
NS = '{http://www.w3.org/2000/svg}'; XL = '{http://www.w3.org/1999/xlink}href'
PROPS = ['fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'stroke-miterlimit', 'fill-rule', 'opacity', 'fill-opacity', 'stroke-opacity']
def color(c, alpha=1.0):
    c = c.strip().lower()
    c = {'white': '#fff', 'black': '#000'}.get(c, c)
    if len(c) == 4: c = '#' + ''.join(ch * 2 for ch in c[1:])
    return '#%02X%s' % (round(alpha * 255), c[1:].upper())
def parse_css(root):
    rules = {}
    for st in root.iter(NS + 'style'):
        for sel, body in re.findall(r'\.([\w-]+)\s*\{([^}]*)\}', st.text or ''):
            rules[sel] = dict((k.strip(), v.strip()) for k, v in (d.split(':', 1) for d in body.split(';') if ':' in d))
    return rules
def matrix(t):
    if not t: return (1, 0, 0, 1, 0, 0)
    m = re.match(r'matrix\(([^)]*)\)', t)
    if m:
        a, b, c, d, e, f = [float(x) for x in re.split(r'[\s,]+', m.group(1).strip())]
        assert b == 0 and c == 0, t
        return (a, 0, 0, d, e, f)
    tr = re.search(r'translate\(([^)]*)\)', t); sc = re.search(r'scale\(([^)]*)\)', t)
    tx, ty = ([float(x) for x in re.split(r'[\s,]+', tr.group(1).strip())] + [0])[:2] if tr else (0, 0)
    s = [float(x) for x in re.split(r'[\s,]+', sc.group(1).strip())] if sc else [1]
    sx, sy = s[0], s[-1]
    assert t.strip().startswith('translate') or not tr, t  # translate(...)scale(...): p' = s*p + t
    return (sx, 0, 0, sy, tx, ty)
def gradient(root, gid):
    grads = {g.get('id'): g for g in root.iter(NS + 'linearGradient')}
    g = grads[gid]; stops_src = g
    while not list(stops_src.iter(NS + 'stop')) and stops_src.get(XL): stops_src = grads[stops_src.get(XL)[1:]]
    a, _, _, d, e, f = matrix(g.get('gradientTransform'))
    x1, y1, x2, y2 = (float(g.get(k, 0)) for k in ('x1', 'y1', 'x2', 'y2'))
    stops = [(float(s.get('offset')), color(s.get('stop-color'), float(s.get('stop-opacity', 1)))) for s in stops_src.iter(NS + 'stop')]
    return (a * x1 + e, d * y1 + f, a * x2 + e, d * y2 + f, stops)
def walk(el, style, css, root, out):
    style = dict(style)
    # Presentation attributes first; CSS class rules override them (SVG cascade).
    for k in PROPS:
        if el.get(k) is not None: style[k] = el.get(k)
    for cls in (el.get('class') or '').split():
        style.update({k: v for k, v in css.get(cls, {}).items() if k in PROPS})
    d = None
    if el.tag == NS + 'path': d = el.get('d')
    elif el.tag == NS + 'circle':
        cx, cy, r = (float(el.get(k)) for k in ('cx', 'cy', 'r'))
        d = f'M{cx - r},{cy}a{r},{r} 0 1,0 {2 * r},0a{r},{r} 0 1,0 {-2 * r},0z'
    elif el.tag == NS + 'rect':
        x, y, w, h = (float(el.get(k)) for k in ('x', 'y', 'width', 'height')); rx = min(float(el.get('rx', 0)), w / 2, h / 2)
        d = (f'M{x + rx},{y}h{w - 2 * rx}a{rx},{rx} 0 0,1 {rx},{rx}v{h - 2 * rx}a{rx},{rx} 0 0,1 {-rx},{rx}'
             f'h{-(w - 2 * rx)}a{rx},{rx} 0 0,1 {-rx},{-rx}v{-(h - 2 * rx)}a{rx},{rx} 0 0,1 {rx},{-rx}z')
    if d:
        op = float(style.get('opacity', 1))
        attrs = [f'android:pathData="{d}"']
        if style.get('fill-rule') == 'evenodd': attrs.append('android:fillType="evenOdd"')
        fill = style.get('fill', '#000'); child = ''
        if fill.startswith('url('):
            x1, y1, x2, y2, stops = gradient(root, fill[5:-1])
            items = ''.join(f'\n                <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
            child = f'''
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="{x1:.2f}" android:startY="{y1:.2f}" android:endX="{x2:.2f}" android:endY="{y2:.2f}">{items}
            </gradient>
        </aapt:attr>'''
            if op != 1: attrs.append(f'android:fillAlpha="{op}"')
        elif fill != 'none':
            attrs.append(f'android:fillColor="{color(fill)}"')
            if op != 1: attrs.append(f'android:fillAlpha="{op}"')
        if style.get('stroke', 'none') != 'none':
            attrs += [f'android:strokeColor="{color(style["stroke"])}"', f'android:strokeWidth="{style.get("stroke-width", "1")}"']
            if style.get('stroke-linecap', 'butt') != 'butt': attrs.append(f'android:strokeLineCap="{style["stroke-linecap"]}"')
            if style.get('stroke-linejoin', 'miter') != 'miter': attrs.append(f'android:strokeLineJoin="{style["stroke-linejoin"]}"')
            if 'stroke-miterlimit' in style: attrs.append(f'android:strokeMiterLimit="{style["stroke-miterlimit"]}"')
            if op != 1: attrs.append(f'android:strokeAlpha="{op}"')
        out.append('    <path\n        ' + '\n        '.join(attrs) + (f'>{child}\n    </path>' if child else ' />'))
    for c in el:
        if c.tag not in (NS + 'defs', NS + 'style'): walk(c, style, css, root, out)
src, dst = sys.argv[1], sys.argv[2]
root = ET.parse(src).getroot(); css = parse_css(root)
vb = root.get('viewBox').split(); out = []
walk(root, {}, css, root, out)
open(dst, 'w').write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- One on One chess piece: original artwork for this app (generated by docs/design/chess-pieces/make.py). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="{vb[2]}"
    android:viewportHeight="{vb[3]}">
''' + '\n'.join(out) + '\n</vector>\n')
