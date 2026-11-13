import json, sys, time, urllib.request, urllib.error

out = sys.argv[1]
MIRRORS = [
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass-api.de/api/interpreter",
    "https://lz4.overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
]
MAJOR = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link"
MINOR = "unclassified|residential|living_street"
FULL = f"""[out:json][timeout:240];
(
  way["highway"~"^({MAJOR})$"](36.20,127.25,36.48,127.56);
  way["highway"~"^({MINOR})$"](36.27,127.29,36.43,127.48);
);
out geom;"""
MAJOR_ONLY = f"""[out:json][timeout:240];
way["highway"~"^({MAJOR})$"](36.20,127.25,36.48,127.56);
out geom;"""

def fetch(url, query):
    req = urllib.request.Request(url, data=("data=" + urllib.parse.quote(query)).encode(), headers={"User-Agent": "TunnelFree-contest/1.0 (student project; contact via github wotmdwlq33-a11y)"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=300) as r:
        raw = r.read()
    d = json.loads(raw)
    n = len(d.get("elements", []))
    print(f"  ok {len(raw)/1e6:.1f} MB, {n} ways, {time.time()-t0:.0f}s", flush=True)
    if n < 500:
        raise RuntimeError("too few ways")
    return raw

import urllib.parse
for label, q in (("full", FULL), ("major-only", MAJOR_ONLY)):
    for m in MIRRORS:
        print(f"[{label}] {m}", flush=True)
        try:
            raw = fetch(m, q)
            open(out, "wb").write(raw)
            print(f"saved {label} -> {out}", flush=True)
            sys.exit(0)
        except Exception as e:
            print(f"  fail: {type(e).__name__}: {str(e)[:120]}", flush=True)
            time.sleep(3)
print("ALL FAILED"); sys.exit(1)
