package kr.tunnelfree.app.domain

enum class PoiCategory(val label: String, val emergency: Boolean = false) {
    HOSPITAL("병원", true),
    FIRE("소방서", true),
    POLICE("경찰", true),
    REST_AREA("휴게소"),
    FUEL("주유소"),
    RESTAURANT("음식점");

    companion object {
        fun fromName(name: String?): PoiCategory? = entries.firstOrNull { it.name == name }
    }
}

data class Poi(val id: String, val name: String, val lat: Double, val lng: Double, val category: PoiCategory) {
    val latLng: LatLng get() = LatLng(lat, lng)
    val place: Place get() = Place(name, lat, lng)
}

data class SearchHit(val place: Place, val categoryLabel: String, val distanceM: Double)

object PoiDatabase {
    fun parse(text: String): List<Poi> = text.lineSequence().mapNotNull { line ->
        if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
        val f = line.split(';')
        if (f.size < 5) return@mapNotNull null
        val lat = f[2].toDoubleOrNull() ?: return@mapNotNull null
        val lng = f[3].toDoubleOrNull() ?: return@mapNotNull null
        val category = PoiCategory.fromName(f[4]) ?: return@mapNotNull null
        Poi(f[0], f[1], lat, lng, category)
    }.toList()
}

object PlaceSearch {
    const val MAX_RESULTS = 20

    private fun normalize(s: String) = s.replace(" ", "").lowercase()

    fun search(
        query: String,
        origin: LatLng,
        places: List<Place>,
        pois: List<Poi>,
        parking: List<ParkingLot>,
    ): List<SearchHit> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val all = buildList {
            places.forEach { add(it to "장소") }
            pois.forEach { add(it.place to it.category.label) }
            parking.filter { it.name.isNotBlank() }.forEach { add(Place(it.name, it.lat, it.lng) to "주차장") }
        }
        return all.asSequence()
            .filter { (place, _) -> normalize(place.name).contains(q) }
            .map { (place, label) -> SearchHit(place, label, distanceMeters(origin, place.latLng)) }
            .sortedWith(compareBy({ !normalize(it.place.name).startsWith(q) }, { it.distanceM }))
            .distinctBy { it.place.name to it.categoryLabel }
            .take(MAX_RESULTS)
            .toList()
    }

    fun nearby(origin: LatLng, pois: List<Poi>, filter: (PoiCategory) -> Boolean): List<SearchHit> =
        pois.asSequence()
            .filter { filter(it.category) }
            .map { SearchHit(it.place, it.category.label, distanceMeters(origin, it.latLng)) }
            .sortedBy { it.distanceM }
            .take(MAX_RESULTS)
            .toList()
}
