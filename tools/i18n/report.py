import os
import sys, json, re, os
sys.path.insert(0, os.path.dirname(__file__))
from kstr import scan, text_of, is_ui
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../app/src/main/java/com/tools/app/")
for rel in sys.argv[1:]:
    src = open(ROOT + rel).read()
    lits = [l for l in scan(src) if is_ui(l)]
    print(f"### {rel} ({len(lits)})")
    for k, l in enumerate(lits):
        line = src.count('\n', 0, l['start']) + 1
        before = src[max(0, l['start'] - 28):l['start']].replace('\n', ' ').strip()
        after = src[l['end']:l['end'] + 6].replace('\n', ' ')
        flags = []
        if re.search(r'(==|!=|startsWith\(|endsWith\(|contains\(|equals\(|in listOf\(|when \(.*\) \{)\s*$', before) or after.strip().startswith('->'): flags.append('CMP')
        if any(p[0] == 'expr' and re.search(r'if \(.*1\)', p[1]) for p in l['parts']): flags.append('PLURAL')
        if re.search(r'const val|Log\.[dewi]\(|@', before): flags.append('CODE')
        print(f"{k:3d} L{line:<4d} {' '.join(flags):10s} …{before[-24:]}  {text_of(l)[:110]!r}")
