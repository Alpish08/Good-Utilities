import os
"""apply.py rel_path prefix "skip ids" : converts UI literals of one file to t(R.string.key[, args]).
Registry (text -> key) in registry.json; English resources in res/values/strings_gu.xml (regenerated each run)."""
import sys, json, re, os
sys.path.insert(0, os.path.dirname(__file__))
from kstr import scan, is_ui
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../app/src/main/")
REG = os.path.join(HERE, "registry.json")
reg = json.load(open(REG)) if os.path.exists(REG) else {"keys": {}, "texts": {}}   # key -> xml text ; text -> key

def slug(s):
    w = re.findall(r'[a-z0-9]+', s.lower())
    return '_'.join(w)[:38].strip('_') or 'text'

def xml_escape(kotlin_text):
    # Kotlin escapes (\" \n \\ \$) -> Android resource text
    t = kotlin_text.replace('\\$', '$').replace("\\'", "'")
    t = t.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    t = t.replace("'", "\\'")
    return t

rel, prefix = sys.argv[1], sys.argv[2]
skip = set(int(x) for x in sys.argv[3].split()) if len(sys.argv) > 3 and sys.argv[3].strip() else set()
path = ROOT + "java/com/tools/app/" + rel
src = open(path).read()
lits = [l for l in scan(src) if is_ui(l)]
edits = []; manual = []
for k, l in enumerate(lits):
    if k in skip: continue
    exprs = [p[1] for p in l['parts'] if p[0] == 'expr']
    if any(re.search(r'if \(', e) for e in exprs): manual.append((k, l['raw'])); continue
    text = ''; n = 0; args = []
    for kind, v in l['parts']:
        if kind == 'text':
            text += xml_escape(v).replace('%', '%%') if exprs else xml_escape(v)
        else:
            n += 1; text += f'%{n}$s'; args.append(v.strip())
    if text.startswith(' '): text = '\\u0020' + text[1:]
    if text.endswith(' '): text = text[:-1] + '\\u0020'
    key = reg["texts"].get(text)
    if not key:
        base = prefix + '_' + slug(''.join(p[1] for p in l['parts'] if p[0] == 'text')); key = base; i = 2
        while key in reg["keys"]: key = f"{base}_{i}"; i += 1
        reg["keys"][key] = text; reg["texts"][text] = key
    call = f"t(R.string.{key}" + ''.join(', ' + a for a in args) + ")"
    edits.append((l['start'], l['end'], call))
for s, e, call in sorted(edits, reverse=True):
    src = src[:s] + call + src[e:]
pkg = re.search(r'^package ([\w.]+)', src, re.M).group(1)
imports = []
if pkg != 'com.tools.app' and 'import com.tools.app.R\n' not in src: imports.append('import com.tools.app.R')
if pkg != 'com.tools.app.ui' and 'import com.tools.app.ui.t\n' not in src: imports.append('import com.tools.app.ui.t')
if edits and imports:
    m = re.search(r'^import .*$', src, re.M)
    if m: src = src[:m.start()] + '\n'.join(imports) + '\n' + src[m.start():]
    else:
        m = re.search(r'^package .*$', src, re.M); src = src[:m.end()] + '\n\n' + '\n'.join(imports) + src[m.end():]
open(path, 'w').write(src)
json.dump(reg, open(REG, 'w'), indent=0, ensure_ascii=False)
with open(ROOT + "res/values/strings_gu.xml", 'w') as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<!-- Good Utilities UI text (English, the source). Translations: values-fr, values-tr. -->\n<resources>\n')
    for k, v in reg["keys"].items():
        attr = ' formatted="false"' if ("%" in v and "$s" not in v) else ''
        f.write(f'    <string name="{k}"{attr}>{v}</string>\n')
    f.write('</resources>\n')
print(f"{rel}: {len(edits)} converted, manual: {len(manual)}")
for k, raw in manual: print(f"   MANUAL {k}: {raw[:120]}")
