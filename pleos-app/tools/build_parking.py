import sys

import osmium

MIN_LAT, MAX_LAT, MIN_LNG, MAX_LNG = 36.20, 36.48, 127.25, 127.56
KINDS = {
    "underground": "UNDERGROUND",
    "multi-storey": "MULTI_STOREY",
    "rooftop": "MULTI_STOREY",
    "surface": "SURFACE",
    "lane": "STREET",
    "street_side": "STREET",
    "on_kerb": "STREET",
    "half_on_kerb": "STREET",
}


def clean(s):
    return s.replace(";", " ").replace("\n", " ").strip()


def poi_category(tags):
    amenity = tags.get("amenity", "")
    highway = tags.get("highway", "")
    if amenity == "hospital":
        return "HOSPITAL"
    if amenity == "police":
        return "POLICE"
    if amenity == "fire_station":
        return "FIRE"
    if amenity == "fuel":
        return "FUEL"
    if amenity == "restaurant":
        return "RESTAURANT"
    if highway in ("services", "rest_area"):
        return "REST_AREA"
    return None


class Handler(osmium.SimpleHandler):
    def __init__(self):
        super().__init__()
        self.rows = []
        self.pois = []

    def poi(self, oid, tags, lat, lng):
        category = poi_category(tags)
        name = clean(tags.get("name", ""))
        if category and name and MIN_LAT <= lat <= MAX_LAT and MIN_LNG <= lng <= MAX_LNG:
            self.pois.append(f"{oid};{name};{lat:.6f};{lng:.6f};{category}")

    def add(self, oid, tags, lat, lng):
        if not (MIN_LAT <= lat <= MAX_LAT and MIN_LNG <= lng <= MAX_LNG):
            return
        access = tags.get("access", "-")
        if access in ("private", "no"):
            return
        cap = tags.get("capacity", "0")
        cap = cap if cap.isdigit() else "0"
        kind = KINDS.get(tags.get("parking", ""), "UNKNOWN")
        name = clean(tags.get("name", ""))
        self.rows.append(f"{oid};{name};{lat:.6f};{lng:.6f};{kind};{cap};{tags.get('fee', '-')};{access}")

    def node(self, n):
        if not n.location.valid():
            return
        if n.tags.get("amenity") == "parking":
            self.add(f"n{n.id}", n.tags, n.location.lat, n.location.lon)
        self.poi(f"n{n.id}", n.tags, n.location.lat, n.location.lon)

    def way(self, w):
        parking = w.tags.get("amenity") == "parking"
        if not parking and poi_category(w.tags) is None:
            return
        pts = [(nd.location.lat, nd.location.lon) for nd in w.nodes if nd.location.valid()]
        if not pts:
            return
        lat, lng = sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts)
        if parking:
            self.add(f"w{w.id}", w.tags, lat, lng)
        self.poi(f"w{w.id}", w.tags, lat, lng)


def main(src, out):
    h = Handler()
    h.apply_file(src, locations=True, idx="flex_mem")
    h.rows.sort()
    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write("# OpenStreetMap contributors (ODbL). id;이름;위도;경도;유형;면수;유료;공개범위\n")
        f.write("\n".join(h.rows) + "\n")
    h.pois.sort()
    poi_path = out.replace("parking_daejeon.csv", "pois_daejeon.csv")
    with open(poi_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("# OpenStreetMap contributors (ODbL). id;이름;위도;경도;분류\n")
        f.write("\n".join(h.pois) + "\n")
    print("rows", len(h.rows), "pois", len(h.pois))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
