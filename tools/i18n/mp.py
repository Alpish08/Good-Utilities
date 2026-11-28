import os
"""mp.py: hand edits from a JSON spec list: [{"file","old","new"}, ...] plus resources to add to strings_gu_manual.xml.
Spec: {"edits":[[file, old, new], ...], "xml": "<plurals …/>…"}. Adds the tp/t imports when used."""
import sys, json, re
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../app/src/main/")
spec = json.load(open(sys.argv[1]))
for f, old, new in spec.get("edits", []):
    p = ROOT + "java/com/tools/app/" + f; s = open(p).read()
    n = s.count(old)
    if n != 1: sys.exit(f"{f}: found {n}x: {old[:80]!r}")
    s = s.replace(old, new, 1)
    pkg = re.search(r'^package ([\w.]+)', s, re.M).group(1)
    for fn in ("t", "tp"):
        if re.search(r'\b%s\(R\.' % fn, s) and pkg != "com.tools.app.ui" and f"import com.tools.app.ui.{fn}\n" not in s and "import com.tools.app.ui.*" not in s:
            m = s.index("\nimport ") + 1; s = s[:m] + f"import com.tools.app.ui.{fn}\n" + s[m:]
    if "R." in new and pkg != "com.tools.app" and "import com.tools.app.R\n" not in s:
        m = s.index("\nimport ") + 1; s = s[:m] + "import com.tools.app.R\n" + s[m:]
    open(p, "w").write(s)
if spec.get("xml"):
    p = ROOT + "res/values/strings_gu_manual.xml"; s = open(p).read()
    s = s.replace("</resources>", spec["xml"].rstrip() + "\n</resources>"); open(p, "w").write(s)
print("ok", len(spec.get("edits", [])))
