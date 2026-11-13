import json, math, sys

src, dst = sys.argv[1], sys.argv[2]
d = json.load(open(src, encoding="utf-8"))

def dist(a, b):
    R = 6371000.0
    la1, lo1, la2, lo2 = map(math.radians, (a["lat"], a["lon"], b["lat"], b["lon"]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))

rows = []
for w in d.get("elements", []):
    if w.get("type") != "way":
        continue
    g = w.get("geometry") or []
    if len(g) < 2:
        continue
    length = sum(dist(g[i], g[i + 1]) for i in range(len(g) - 1))
    if length < 60:
        continue
    t = w.get("tags", {})
    name = t.get("name") or t.get("name:ko") or ""
    name = name.replace(";", " ").replace("|", " ").strip() or "이름 없는 터널·지하차도"
    hw = t.get("highway", "")
    pts = "|".join(f"{p['lat']:.6f},{p['lon']:.6f}" for p in g)
    rows.append((w["id"], name, hw, int(round(length)), pts))

rows.sort(key=lambda r: (-r[3], r[1]))
with open(dst, "w", encoding="utf-8", newline="\n") as f:
    f.write("# id;name;highway;length_m;lat,lng|lat,lng|...  (OpenStreetMap, ODbL, Daejeon bbox 36.20-36.48N 127.25-127.56E)\n")
    for r in rows:
        f.write(f"{r[0]};{r[1]};{r[2]};{r[3]};{r[4]}\n")

print("rows:", len(rows))
names = {}
for r in rows:
    names.setdefault(r[1], []).append(r[3])
for n, ls in sorted(names.items(), key=lambda kv: -max(kv[1]))[:25]:
    print(f"{n}: {len(ls)}개, 최장 {max(ls)} m")
