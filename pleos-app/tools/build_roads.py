import collections, json, math, sys

CLASS = {"motorway": 0, "motorway_link": 1, "trunk": 2, "trunk_link": 3, "primary": 4, "primary_link": 5,
         "secondary": 6, "secondary_link": 7, "tertiary": 8, "tertiary_link": 9,
         "unclassified": 10, "residential": 11, "living_street": 12}

src, dst = sys.argv[1], sys.argv[2]
d = json.load(open(src, encoding="utf-8"))

def dist(a, b):
    k_lat = 111320.0
    k_lng = 111320.0 * math.cos(math.radians((a[0] + b[0]) / 2))
    return math.hypot((b[0] - a[0]) * k_lat, (b[1] - a[1]) * k_lng)

ways = []
for w in d.get("elements", []):
    if w.get("type") != "way":
        continue
    t = w.get("tags", {})
    hw = t.get("highway")
    if hw not in CLASS or t.get("area") == "yes":
        continue
    acc, mv = t.get("access"), t.get("motor_vehicle")
    if acc in ("private", "no") and mv not in ("yes", "designated"):
        continue
    if mv in ("no", "private"):
        continue
    nodes, geom = w.get("nodes") or [], w.get("geometry") or []
    if len(nodes) < 2 or len(nodes) != len(geom):
        continue
    geom = [(g["lat"], g["lon"]) for g in geom]
    ow = t.get("oneway")
    oneway = ow in ("yes", "true", "1") or (ow is None and (hw == "motorway" or t.get("junction") in ("roundabout", "circular")))
    if ow == "-1":
        nodes, geom, oneway = nodes[::-1], geom[::-1], True
    name = (t.get("name") or t.get("name:ko") or t.get("ref") or "").replace("\n", " ").strip()
    ways.append(dict(id=w["id"], nodes=nodes, geom=geom, cls=CLASS[hw], oneway=oneway,
                     tunnel=t.get("tunnel") == "yes", name=name,
                     length=sum(dist(geom[i], geom[i + 1]) for i in range(len(geom) - 1))))

use = collections.Counter()
for w in ways:
    for n in w["nodes"]:
        use[n] += 1
    use[w["nodes"][0]] += 1
    use[w["nodes"][-1]] += 1

tunnel_ways = [w for w in ways if w["tunnel"]]
parent = {w["id"]: w["id"] for w in tunnel_ways}
def find(x):
    while parent[x] != x:
        parent[x] = parent[parent[x]]
        x = parent[x]
    return x
by_end = collections.defaultdict(list)
for w in tunnel_ways:
    by_end[w["nodes"][0]].append(w)
    by_end[w["nodes"][-1]].append(w)
for group in by_end.values():
    for i in range(len(group)):
        for j in range(i + 1, len(group)):
            a, b = group[i], group[j]
            if a["name"] and b["name"] and a["name"] != b["name"]:
                continue
            parent[find(a["id"])] = find(b["id"])
chain_len = collections.Counter()
for w in tunnel_ways:
    chain_len[find(w["id"])] += w["length"]
chain_name = {}
for w in tunnel_ways:
    r = find(w["id"])
    if w["name"] and not chain_name.get(r):
        chain_name[r] = w["name"]

vid = {}
verts = []
def vertex(n, pt):
    if n not in vid:
        vid[n] = len(verts)
        verts.append(pt)
    return vid[n]

pool = []
edges = []
for w in ways:
    nodes, geom = w["nodes"], w["geom"]
    cut = [i for i, n in enumerate(nodes) if use[n] >= 2]
    if cut[0] != 0:
        cut.insert(0, 0)
    if cut[-1] != len(nodes) - 1:
        cut.append(len(nodes) - 1)
    tun = 0
    name = w["name"]
    if w["tunnel"]:
        r = find(w["id"])
        tun = int(round(chain_len[r]))
        name = name or chain_name.get(r, "")
    for i, j in zip(cut, cut[1:]):
        if i == j:
            continue
        u = vertex(nodes[i], geom[i])
        v = vertex(nodes[j], geom[j])
        length = sum(dist(geom[k], geom[k + 1]) for k in range(i, j))
        gstart = len(pool)
        pool.extend(geom[i + 1:j])
        edges.append((u, v, int(round(length * 10)), w["cls"], 1 if w["oneway"] else 0, tun, gstart, j - i - 1, name or "-"))

with open(dst, "w", encoding="utf-8", newline="\n") as f:
    f.write("# 대전 도로망 (OpenStreetMap, ODbL). V 정점 / P 중간 기하점 / E 간선: u v len_dm class oneway tunnel_m gstart gcount name\n")
    f.write(f"V {len(verts)}\n")
    for la, lo in verts:
        f.write(f"{int(round(la * 1e6))} {int(round(lo * 1e6))}\n")
    f.write(f"P {len(pool)}\n")
    for la, lo in pool:
        f.write(f"{int(round(la * 1e6))} {int(round(lo * 1e6))}\n")
    f.write(f"E {len(edges)}\n")
    for e in edges:
        f.write(" ".join(str(x) for x in e) + "\n")

tun_edges = sum(1 for e in edges if e[5] > 0)
print(f"ways {len(ways)}, vertices {len(verts)}, shape points {len(pool)}, edges {len(edges)}, tunnel edges {tun_edges}")
print("tunnel chains:", len(chain_len), "longest:", sorted(((int(v), chain_name.get(k, '?')) for k, v in chain_len.items()), reverse=True)[:8])
