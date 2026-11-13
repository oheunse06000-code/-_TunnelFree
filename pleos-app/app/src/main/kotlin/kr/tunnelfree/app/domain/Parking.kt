package kr.tunnelfree.app.domain

enum class ParkingKind(val label: String) {
    UNDERGROUND("지하"),
    MULTI_STOREY("주차 건물"),
    SURFACE("지상"),
    STREET("노상"),
    UNKNOWN("구조 미상");

    val enclosed: Boolean get() = this == UNDERGROUND || this == MULTI_STOREY

    companion object {
        fun fromName(name: String?): ParkingKind = entries.firstOrNull { it.name == name } ?: UNKNOWN
    }
}

data class ParkingLot(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val kind: ParkingKind,
    val capacity: Int = 0,
    val feeInfo: String = "",
) {
    val latLng: LatLng get() = LatLng(lat, lng)
    val displayName: String get() = name.ifBlank { "${kind.label} 주차장" }
}

data class ParkingCandidate(
    val lot: ParkingLot,
    val distanceM: Double,
    val score: Double,
)

data class ParkingPreference(
    val avoidEnclosed: Boolean,
    val radiusM: Double = ParkingRanker.DEFAULT_RADIUS_M,
)

data class ParkingAdvice(val lotId: String, val reason: String)

object ParkingDatabase {
    fun parse(text: String): List<ParkingLot> = text.lineSequence().mapNotNull { line ->
        if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
        val f = line.split(';')
        if (f.size < 8) return@mapNotNull null
        val lat = f[2].toDoubleOrNull() ?: return@mapNotNull null
        val lng = f[3].toDoubleOrNull() ?: return@mapNotNull null
        val fee = when (f[6]) { "yes" -> "유료"; "no" -> "무료"; else -> "" }
        ParkingLot(f[0], f[1], lat, lng, ParkingKind.fromName(f[4]), f[5].toIntOrNull() ?: 0, fee)
    }.toList()
}

object ParkingRanker {
    const val DEFAULT_RADIUS_M = 800.0
    const val MAX_CANDIDATES = 8

    private const val ENCLOSED_PENALTY = 500.0
    private const val OPEN_BONUS = 100.0
    private const val UNNAMED_PENALTY = 200.0
    private const val UNKNOWN_KIND_PENALTY = 200.0

    fun rank(lots: List<ParkingLot>, destination: LatLng, preference: ParkingPreference): List<ParkingCandidate> =
        lots.asSequence()
            .map { it to distanceMeters(destination, it.latLng) }
            .filter { (_, d) -> d <= preference.radiusM }
            .map { (lot, d) ->
                var score = d
                if (preference.avoidEnclosed && lot.kind.enclosed) score += ENCLOSED_PENALTY
                if (lot.kind == ParkingKind.SURFACE) score -= OPEN_BONUS
                if (lot.name.isBlank()) score += UNNAMED_PENALTY
                if (preference.avoidEnclosed && lot.kind == ParkingKind.UNKNOWN) score += UNKNOWN_KIND_PENALTY
                ParkingCandidate(lot, d, score)
            }
            .sortedBy { it.score }
            .take(MAX_CANDIDATES)
            .toList()

    fun ruleAdvice(candidates: List<ParkingCandidate>, preference: ParkingPreference): ParkingAdvice? {
        val best = candidates.firstOrNull() ?: return null
        val parts = buildList {
            add("목적지까지 ${formatDistance(best.distanceM)}")
            add(best.lot.kind.label)
            if (preference.avoidEnclosed && best.lot.kind.enclosed) add("닫힌 공간 주의")
            if (preference.avoidEnclosed && best.lot.kind == ParkingKind.UNKNOWN) add("지하 여부 확인 필요")
        }
        return ParkingAdvice(best.lot.id, parts.joinToString(" · "))
    }
}
