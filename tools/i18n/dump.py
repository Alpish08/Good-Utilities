import json, sys, re
r = json.load(open(sys.argv[1]))
items = list(r["keys"].items())
a, b = int(sys.argv[2]), int(sys.argv[3])
for k, v in items[a:b]: print(f"{k}\t{v}")
print("TOTAL", len(items), file=sys.stderr)
