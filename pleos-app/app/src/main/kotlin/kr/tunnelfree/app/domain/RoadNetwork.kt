package kr.tunnelfree.app.domain

import java.io.BufferedReader
import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.sqrt

enum class RoadClass(val code: Int, val speedKmh: Double) {
    MOTORWAY(0, 90.0), MOTORWAY_LINK(1, 50.0),
    TRUNK(2, 70.0), TRUNK_LINK(3, 45.0),
    PRIMARY(4, 55.0), PRIMARY_LINK(5, 40.0),
    SECONDARY(6, 45.0), SECONDARY_LINK(7, 35.0),
    TERTIARY(8, 40.0), TERTIARY_LINK(9, 30.0),
    UNCLASSIFIED(10, 35.0), RESIDENTIAL(11, 30.0), LIVING_STREET(12, 15.0);

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Int): RoadClass = byCode[code] ?: UNCLASSIFIED
    }
}

data class Bounds(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double) {
    fun contains(p: LatLng): Boolean = p.lat in minLat..maxLat && p.lng in minLng..maxLng

    companion object {
        fun of(points: Iterable<LatLng>): Bounds {
            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            var minLng = Double.MAX_VALUE; var maxLng = -Double.MAX_VALUE
            for (p in points) {
                if (p.lat < minLat) minLat = p.lat
                if (p.lat > maxLat) maxLat = p.lat
                if (p.lng < minLng) minLng = p.lng
                if (p.lng > maxLng) maxLng = p.lng
            }
            return if (minLat > maxLat) Bounds(0.0, 0.0, 0.0, 0.0) else Bounds(minLat, maxLat, minLng, maxLng)
        }
    }
}

class RoadEdge(
    val index: Int,
    val from: Int,
    val to: Int,
    val lengthM: Double,
    val roadClass: RoadClass,
    val oneway: Boolean,
    val tunnelLengthM: Double,
    val name: String,
    internal val shapeStart: Int,
    internal val shapeCount: Int,
) {
    val isTunnel: Boolean get() = tunnelLengthM > 0
    val travelSec: Double get() = lengthM / (roadClass.speedKmh / 3.6)
}

class RoadNetwork private constructor(
    private val vLat: DoubleArray,
    private val vLng: DoubleArray,
    private val pLat: DoubleArray,
    private val pLng: DoubleArray,
    val edges: List<RoadEdge>,
) {
    val vertexCount: Int get() = vLat.size
    val isEmpty: Boolean get() = edges.isEmpty()
    val bounds: Bounds

    private val adjacency: Array<IntArray>
    private val incident: IntArray

    init {
        val n = vLat.size
        val counts = IntArray(n)
        incident = IntArray(n)
        for (e in edges) {
            counts[e.from]++
            if (!e.oneway) counts[e.to]++
            incident[e.from]++
            incident[e.to]++
        }
        val adj = Array(n) { IntArray(counts[it]) }
        val fill = IntArray(n)
        for (e in edges) {
            adj[e.from][fill[e.from]++] = e.index * 2 + 1
            if (!e.oneway) adj[e.to][fill[e.to]++] = e.index * 2
        }
        adjacency = adj
        bounds = if (n == 0) Bounds(0.0, 0.0, 0.0, 0.0) else Bounds(vLat.min(), vLat.max(), vLng.min(), vLng.max())
    }

    fun vertex(i: Int): LatLng = LatLng(vLat[i], vLng[i])
    fun isJunction(v: Int): Boolean = incident[v] >= 3
    fun contains(p: LatLng): Boolean = bounds.contains(p)

    fun nearestVertex(p: LatLng): Int {
        val kLng = cos(Math.toRadians(p.lat))
        var best = -1
        var bestD = Double.MAX_VALUE
        for (i in vLat.indices) {
            val dy = vLat[i] - p.lat
            val dx = (vLng[i] - p.lng) * kLng
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    fun edgePoints(e: RoadEdge, forward: Boolean = true): List<LatLng> {
        val list = ArrayList<LatLng>(e.shapeCount + 2)
        list.add(vertex(e.from))
        for (i in 0 until e.shapeCount) list.add(LatLng(pLat[e.shapeStart + i], pLng[e.shapeStart + i]))
        list.add(vertex(e.to))
        if (!forward) list.reverse()
        return list
    }

    fun route(from: LatLng, to: LatLng, level: AvoidanceLevel, via: LatLng? = null): RoadRoute? =
        route(from, to, level.maxTunnelLengthM, via)

    fun route(from: LatLng, to: LatLng, maxTunnelM: Double, via: LatLng? = null): RoadRoute? {
        if (isEmpty) return null
        val s = snapCandidates(from)
        val t = snapCandidates(to)
        val legs = ArrayList<Leg>(2)
        if (via != null) {
            val first = shortestPath(s, snapCandidates(via), maxTunnelM) ?: return null
            val second = shortestPath(listOf(first.end to 0.0), t, maxTunnelM) ?: return null
            legs.add(first)
            legs.add(second)
        } else {
            legs.add(shortestPath(s, t, maxTunnelM) ?: return null)
        }
        return assemble(legs)
    }

    private class Step(val edge: RoadEdge, val forward: Boolean) {
        val endVertex: Int get() = if (forward) edge.to else edge.from
    }

    private class Leg(val steps: List<Step>, val start: Int, val end: Int)

    private fun snapCandidates(p: LatLng): List<Pair<Int, Double>> {
        val kLng = cos(Math.toRadians(p.lat))
        val maxDeg2 = (SNAP_RADIUS_M / DEG_M).let { it * it }
        val found = ArrayList<Pair<Int, Double>>()
        var best = -1
        var bestD2 = Double.MAX_VALUE
        for (i in vLat.indices) {
            val dy = vLat[i] - p.lat
            val dx = (vLng[i] - p.lng) * kLng
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) { bestD2 = d2; best = i }
            if (d2 <= maxDeg2) found.add(i to sqrt(d2) * DEG_M)
        }
        if (found.isEmpty() && best >= 0) found.add(best to sqrt(bestD2) * DEG_M)
        found.sortBy { it.second }
        return found.take(SNAP_MAX_CANDIDATES)
    }

    private fun shortestPath(sources: List<Pair<Int, Double>>, targets: List<Pair<Int, Double>>, maxTunnelM: Double): Leg? {
        val n = vLat.size
        val dist = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val prev = IntArray(n) { -1 }
        val origin = IntArray(n) { -1 }
        val done = BooleanArray(n)
        val queue = PriorityQueue<Long>()
        for ((v, snapM) in sources) {
            val c = snapM / SNAP_SPEED_MPS
            if (c < dist[v]) {
                dist[v] = c
                origin[v] = v
                queue.add(pack(c, v))
            }
        }
        val targetPenalty = HashMap<Int, Double>()
        for ((v, snapM) in targets) {
            val c = snapM / SNAP_SPEED_MPS
            val old = targetPenalty[v]
            if (old == null || c < old) targetPenalty[v] = c
        }
        var bestTarget = -1
        var bestTotal = Double.POSITIVE_INFINITY
        while (queue.isNotEmpty()) {
            val packed = queue.poll()
            val u = (packed and VERTEX_MASK).toInt()
            if (done[u]) continue
            done[u] = true
            if (dist[u] >= bestTotal) break
            val penalty = targetPenalty[u]
            if (penalty != null && dist[u] + penalty < bestTotal) {
                bestTotal = dist[u] + penalty
                bestTarget = u
            }
            for (a in adjacency[u]) {
                val e = edges[a shr 1]
                if (e.isTunnel && e.tunnelLengthM > maxTunnelM) continue
                val forward = (a and 1) == 1
                val v = if (forward) e.to else e.from
                val nd = dist[u] + e.travelSec
                if (nd < dist[v]) {
                    dist[v] = nd
                    prev[v] = a
                    origin[v] = origin[u]
                    queue.add(pack(nd, v))
                }
            }
        }
        if (bestTarget < 0) return null
        val start = origin[bestTarget]
        val steps = ArrayList<Step>()
        var cur = bestTarget
        while (cur != start) {
            val a = prev[cur]
            val e = edges[a shr 1]
            val forward = (a and 1) == 1
            steps.add(Step(e, forward))
            cur = if (forward) e.from else e.to
        }
        steps.reverse()
        return Leg(steps, start, bestTarget)
    }

    private fun pack(cost: Double, v: Int): Long = ((cost * 1000).toLong() shl VERTEX_BITS) or v.toLong()

    private fun assemble(legs: List<Leg>): RoadRoute? {
        val points = ArrayList<LatLng>()
        val cum = ArrayList<Double>()
        val tunnels = ArrayList<RouteTunnel>()
        val junctions = ArrayList<Pair<Int, String>>()
        val segments = ArrayList<RoadSegment>()
        var travelSec = 0.0
        var viaM: Double? = null
        var lastTunnelEdge: RoadEdge? = null
        for ((legIdx, leg) in legs.withIndex()) {
            if (legIdx > 0) viaM = cum.lastOrNull() ?: 0.0
            for ((i, step) in leg.steps.withIndex()) {
                val pts = edgePoints(step.edge, step.forward)
                val startM = cum.lastOrNull() ?: 0.0
                if (points.isEmpty()) { points.add(pts[0]); cum.add(0.0) }
                for (k in 1 until pts.size) {
                    val d = cum.last() + distanceMeters(points.last(), pts[k])
                    points.add(pts[k])
                    cum.add(d)
                }
                val endM = cum.last()
                travelSec += step.edge.travelSec
                val lastSegment = segments.lastOrNull()
                if (lastSegment != null && lastSegment.roadClass == step.edge.roadClass) {
                    segments[segments.size - 1] = lastSegment.copy(endM = endM)
                } else {
                    segments.add(RoadSegment(startM, endM, step.edge.roadClass))
                }
                if (step.edge.isTunnel) {
                    val name = step.edge.name.ifEmpty { "터널" }
                    val last = tunnels.lastOrNull()
                    if (last != null && last.name == name && last.endM >= startM - 0.5 && step.edge !== lastTunnelEdge) {
                        tunnels[tunnels.size - 1] = last.copy(endM = endM, lengthM = maxOf(last.lengthM, step.edge.tunnelLengthM))
                    } else {
                        tunnels.add(RouteTunnel(name, step.edge.tunnelLengthM, startM, endM))
                    }
                    lastTunnelEdge = step.edge
                }
                val next = leg.steps.getOrNull(i + 1) ?: legs.getOrNull(legIdx + 1)?.steps?.firstOrNull()
                if (next != null && isJunction(step.endVertex)) junctions.add(points.size - 1 to next.edge.name)
            }
        }
        if (points.size < 2) return null
        val cumArr = cum.toDoubleArray()
        val base = RoadRoute(points, cumArr, travelSec, tunnels, emptyList(), viaM, segments)
        val maneuvers = ArrayList<Maneuver>()
        maneuvers.add(Maneuver(0.0, TurnType.DEPART, legs.firstOrNull()?.steps?.firstOrNull()?.edge?.name.orEmpty()))
        for ((idx, name) in junctions) {
            val at = cumArr[idx]
            val angle = normalizeAngle(base.bearingAfter(at) - base.bearingBefore(at))
            val type = TurnType.fromAngle(angle)
            if (type != TurnType.STRAIGHT) maneuvers.add(Maneuver(at, type, name))
        }
        maneuvers.add(Maneuver(cumArr.last(), TurnType.ARRIVE, ""))
        return base.withManeuvers(maneuvers)
    }

    companion object {
        private const val VERTEX_BITS = 20
        private const val VERTEX_MASK = (1L shl VERTEX_BITS) - 1
        private const val DEG_M = 111_320.0
        private const val SNAP_RADIUS_M = 400.0
        private const val SNAP_MAX_CANDIDATES = 12
        private const val SNAP_SPEED_MPS = 20.0 / 3.6

        val EMPTY = RoadNetwork(DoubleArray(0), DoubleArray(0), DoubleArray(0), DoubleArray(0), emptyList())

        fun parse(text: String): RoadNetwork = parse(text.reader().buffered())

        fun parse(reader: BufferedReader): RoadNetwork {
            var vLat = DoubleArray(0); var vLng = DoubleArray(0)
            var pLat = DoubleArray(0); var pLng = DoubleArray(0)
            val edges = ArrayList<RoadEdge>()
            var section = ' '
            var remaining = 0
            var cursor = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty() || line[0] == '#') continue
                if (remaining == 0) {
                    val sp = line.indexOf(' ')
                    if (sp < 0) continue
                    section = line[0]
                    remaining = line.substring(sp + 1).trim().toInt()
                    cursor = 0
                    when (section) {
                        'V' -> { vLat = DoubleArray(remaining); vLng = DoubleArray(remaining) }
                        'P' -> { pLat = DoubleArray(remaining); pLng = DoubleArray(remaining) }
                        'E' -> edges.ensureCapacity(remaining)
                    }
                    continue
                }
                when (section) {
                    'V' -> {
                        val sp = line.indexOf(' ')
                        vLat[cursor] = line.substring(0, sp).toInt() / 1e6
                        vLng[cursor] = line.substring(sp + 1).toInt() / 1e6
                    }
                    'P' -> {
                        val sp = line.indexOf(' ')
                        pLat[cursor] = line.substring(0, sp).toInt() / 1e6
                        pLng[cursor] = line.substring(sp + 1).toInt() / 1e6
                    }
                    'E' -> {
                        val f = line.split(' ', limit = 9)
                        edges.add(
                            RoadEdge(
                                index = cursor,
                                from = f[0].toInt(),
                                to = f[1].toInt(),
                                lengthM = f[2].toInt() / 10.0,
                                roadClass = RoadClass.fromCode(f[3].toInt()),
                                oneway = f[4] == "1",
                                tunnelLengthM = f[5].toDouble(),
                                shapeStart = f[6].toInt(),
                                shapeCount = f[7].toInt(),
                                name = if (f.size > 8 && f[8] != "-") f[8].trim() else "",
                            )
                        )
                    }
                }
                cursor++
                remaining--
            }
            return RoadNetwork(vLat, vLng, pLat, pLng, edges)
        }
    }
}
