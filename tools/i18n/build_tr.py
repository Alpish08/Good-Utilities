import os
"""Builds values-fr / values-tr from tr/*.txt ("key | fr | tr"; plurals as "key#one" / "key#other").
Checks placeholders (%N$s, %N$d, %d, %%, \\n) match the English text."""
import json, re, glob, os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../app/src/main/res/")
reg = json.load(open(os.path.join(HERE, "registry.json")))["keys"]
# English of the hand-written file and strings.xml, for checking
eng = dict(reg)
for f in ("values/strings_gu_manual.xml", "values/strings.xml"):
    s = open(RES + f).read()
    for k, v in re.findall(r'<string name="([^"]+)"[^>]*>(.*?)</string>', s, re.S): eng[k] = v
    for k, body in re.findall(r'<plurals name="([^"]+)">(.*?)</plurals>', s, re.S):
        for q, v in re.findall(r'<item quantity="(\w+)">(.*?)</item>', body, re.S): eng[f"{k}#{q}"] = v
tr = {}
for f in sorted(glob.glob(os.path.join(HERE, "translations", "*.txt"))):
    for n, line in enumerate(open(f), 1):
        line = line.rstrip("\n")
        if not line.strip() or line.startswith("#"): continue
        parts = [p.strip() for p in line.split(" | ")]
        if len(parts) != 3: sys.exit(f"{f}:{n}: need 3 parts: {line[:80]}")
        k = parts[0]; fr, tk = parts[1], parts[2]
        e = eng.get(k, "")
        BS = chr(92) + "u0020"
        def keep(v):
            v = v.replace(BS, " ").strip()
            if e.startswith(BS): v = BS + v
            if e.endswith(BS) and not v.endswith(BS): v = v + BS
            return v
        tr[k] = (keep(fr), keep(tk))
def ph(s): return sorted(re.findall(r'%\d\$[sd]|%%|%d|%s|\\n', s))
def esc(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = re.sub(r"(?<!\\)'", r"\\'", s)
    s = re.sub(r'(?<!\\)"', r'\\"', s)
    return s
bad = []
for k, (fr, tk) in tr.items():
    if k not in eng: bad.append(f"unknown key {k}"); continue
    e = eng[k].replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
    for lang, v in (("fr", fr), ("tr", tk)):
        if ph(v) != ph(e): bad.append(f"{k} [{lang}] placeholders {ph(v)} != {ph(e)}")
if bad: print("\n".join(bad)); sys.exit(1)
missing = [k for k in eng if k not in tr]
for lang, i in (("fr", 0), ("tr", 1)):
    os.makedirs(RES + f"values-{lang}", exist_ok=True)
    strings = []; plurals = {}
    for k, v in tr.items():
        if "#" in k:
            base, q = k.split("#"); plurals.setdefault(base, []).append((q, v[i]))
        else: strings.append((k, v[i]))
    with open(RES + f"values-{lang}/strings_gu.xml", "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
        for k, v in strings:
            attr = ' formatted="false"' if ("%" in v and not re.search(r'%\d\$|%d', v)) else ''
            f.write(f'    <string name="{k}"{attr}>{esc(v)}</string>\n')
        for k, items in plurals.items():
            f.write(f'    <plurals name="{k}">\n')
            qs = dict(items)
            if lang == "fr" and "many" not in qs and "other" in qs: items = items + [("many", qs["other"])]
            for q, v in items: f.write(f'        <item quantity="{q}">{esc(v)}</item>\n')
            f.write('    </plurals>\n')
        f.write('</resources>\n')
print(f"translated {len(tr)}; missing {len(missing)}")
if "-v" in sys.argv: print("\n".join(missing[:40]))
