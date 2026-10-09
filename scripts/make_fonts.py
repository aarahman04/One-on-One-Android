# Builds the static TTFs in app/src/main/res/font from the web repo variable woff2 fonts.
# Usage: python scripts/make_fonts.py <web>/client/public/fonts app/src/main/res/font
# Needs: pip install fonttools brotli. Output is latin + latin-ext merged; Fraunces pinned wght 600 / opsz 28.
import sys, os
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer
from fontTools.merge import Merger
src, out = sys.argv[1], sys.argv[2]
jobs = [("fraunces-600", "fraunces_semibold", {"wght":600,"opsz":28}),
        ("figtree-400","figtree_regular",{"wght":400}),("figtree-500","figtree_medium",{"wght":500}),
        ("figtree-600","figtree_semibold",{"wght":600}),("figtree-700","figtree_bold",{"wght":700}),
        ("jetbrains-mono-400","jetbrains_mono_regular",{"wght":400}),("jetbrains-mono-500","jetbrains_mono_medium",{"wght":500}),
        ("jetbrains-mono-700","jetbrains_mono_bold",{"wght":700})]
for base, name, loc in jobs:
    parts=[]
    for sub in ("latin","latin-ext"):
        f=TTFont(os.path.join(src,f"{base}-{sub}.woff2")); f.flavor=None
        f=instancer.instantiateVariableFont(f, loc, updateFontNames=False)
        p=os.path.join(out,f"{name}-{sub}.ttf"); f.save(p); parts.append(p)
    m=Merger().merge(parts); m.flavor=None
    p=os.path.join(out,name+".ttf"); m.save(p)
    t=TTFont(p); print(name, os.path.getsize(p), len(t.getBestCmap()), 'fvar' in t)
