package kr.tunnelfree.app.domain

import kotlin.math.cos
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

data class Tunnel(
    val id: Long,
    val name: String,
    val highway: String,
    val lengthM: Int,
    val points: List<LatLng>,
) {
    val entry: LatLng get() = points.first()
    val exit: LatLng get() = points.last()
}

fun distanceMeters(a: LatLng, b: LatLng): Double {
    val kLat = 111_320.0
    val kLng = 111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2))
    val dy = (b.lat - a.lat) * kLat
    val dx = (b.lng - a.lng) * kLng
    return sqrt(dx * dx + dy * dy)
}

fun distanceToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double {
    val kLat = 111_320.0
    val kLng = 111_320.0 * cos(Math.toRadians(p.lat))
    val ax = (a.lng - p.lng) * kLng; val ay = (a.lat - p.lat) * kLat
    val bx = (b.lng - p.lng) * kLng; val by = (b.lat - p.lat) * kLat
    val dx = bx - ax; val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0.0) 0.0 else ((-ax * dx - ay * dy) / len2).coerceIn(0.0, 1.0)
    val cx = ax + t * dx; val cy = ay + t * dy
    return sqrt(cx * cx + cy * cy)
}

fun Tunnel.distanceTo(p: LatLng): Double {
    var best = Double.MAX_VALUE
    for (i in 0 until points.size - 1) {
        best = minOf(best, distanceToSegmentMeters(p, points[i], points[i + 1]))
    }
    return best
}

class TunnelDatabase(val tunnels: List<Tunnel>) {

    fun nearest(p: LatLng, maxDistanceM: Double = Double.MAX_VALUE, filter: (Tunnel) -> Boolean = { true }): Pair<Tunnel, Double>? {
        var best: Tunnel? = null
        var bestD = maxDistanceM
        for (t in tunnels) {
            if (!filter(t)) continue
            val d = t.distanceTo(p)
            if (d < bestD) { bestD = d; best = t }
        }
        return best?.let { it to bestD }
    }

    fun entriesWithin(p: LatLng, radiusM: Double): List<Pair<Tunnel, Double>> =
        tunnels.map { it to distanceMeters(p, it.entry) }
            .filter { it.second <= radiusM }
            .sortedBy { it.second }

    fun within(minLat: Double, maxLat: Double, minLng: Double, maxLng: Double): List<Tunnel> =
        tunnels.filter { t -> t.points.any { it.lat in minLat..maxLat && it.lng in minLng..maxLng } }

    fun mergeSegments(maxGapM: Double = MERGE_GAP_M): TunnelDatabase {
        val pending = tunnels.toMutableList()
        val out = ArrayList<Tunnel>(tunnels.size)
        while (pending.isNotEmpty()) {
            var cur = pending.removeAt(0)
            while (true) {
                val prev = pending.firstOrNull { continues(it, cur, maxGapM) } ?: break
                pending.remove(prev)
                cur = joined(prev, cur)
            }
            while (true) {
                val next = pending.firstOrNull { continues(cur, it, maxGapM) } ?: break
                pending.remove(next)
                cur = joined(cur, next)
            }
            out.add(cur)
        }
        return if (out.size == tunnels.size) this else TunnelDatabase(out)
    }

    private fun continues(a: Tunnel, b: Tunnel, maxGapM: Double): Boolean =
        a.name.isNotEmpty() && a.name == b.name && distanceMeters(a.exit, b.entry) < maxGapM &&
            kotlin.math.abs(normalizeAngle(bearingDegrees(a.entry, a.exit) - bearingDegrees(b.entry, b.exit))) < 90.0

    private fun joined(a: Tunnel, b: Tunnel): Tunnel =
        a.copy(lengthM = a.lengthM + b.lengthM, points = a.points + b.points.drop(1))

    companion object {
        const val MERGE_GAP_M = 1.0

        val EMPTY = TunnelDatabase(emptyList())

        fun parse(csv: String): TunnelDatabase {
            val list = ArrayList<Tunnel>()
            for (raw in csv.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val f = line.split(';')
                if (f.size < 5) continue
                val pts = f[4].split('|').mapNotNull { pair ->
                    val c = pair.split(',')
                    if (c.size != 2) null else {
                        val lat = c[0].toDoubleOrNull(); val lng = c[1].toDoubleOrNull()
                        if (lat == null || lng == null) null else LatLng(lat, lng)
                    }
                }
                if (pts.size < 2) continue
                list.add(
                    Tunnel(
                        id = f[0].toLongOrNull() ?: continue,
                        name = f[1],
                        highway = f[2],
                        lengthM = f[3].toIntOrNull() ?: 0,
                        points = pts,
                    )
                )
            }
            return TunnelDatabase(list)
        }
    }
}
