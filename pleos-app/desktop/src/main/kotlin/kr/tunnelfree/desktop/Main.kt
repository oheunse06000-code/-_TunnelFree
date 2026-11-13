package kr.tunnelfree.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kr.tunnelfree.app.domain.AvoidanceLevel
import kr.tunnelfree.app.domain.DayCondition
import kr.tunnelfree.app.domain.DemoData
import kr.tunnelfree.app.domain.HelpPerson
import kr.tunnelfree.app.domain.LatLng
import kr.tunnelfree.app.domain.ParkingDatabase
import kr.tunnelfree.app.domain.ParkingPreference
import kr.tunnelfree.app.domain.ParkingRanker
import kr.tunnelfree.app.domain.PlaceSearch
import kr.tunnelfree.app.domain.PoiCategory
import kr.tunnelfree.app.domain.PoiDatabase
import kr.tunnelfree.app.domain.RoadNetwork
import kr.tunnelfree.app.domain.RoadRoute
import kr.tunnelfree.app.domain.RouteChoice
import kr.tunnelfree.app.domain.RouteContext
import kr.tunnelfree.app.domain.RoutePolicy
import kr.tunnelfree.app.domain.SearchHit
import kr.tunnelfree.app.domain.SituationPlanner
import kr.tunnelfree.app.domain.StopRoadType
import kr.tunnelfree.app.domain.TripUrgency
import kr.tunnelfree.app.domain.TunnelExposure
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.Executors

private const val PORT = 8765

private fun stream(name: String) =
    requireNotNull(object {}.javaClass.classLoader.getResourceAsStream(name)) { "리소스 없음: $name" }

private fun resource(name: String): String = stream(name).bufferedReader().use { it.readText() }

private val roads by lazy { stream("roads_daejeon.txt").bufferedReader().use { RoadNetwork.parse(it) } }
private val parking by lazy { ParkingDatabase.parse(resource("parking_daejeon.csv")) }
private val pois by lazy { PoiDatabase.parse(resource("pois_daejeon.csv")) }
private val places = DemoData.destinationIndices.map { DemoData.places[it] }

private fun quote(s: String) = buildString {
    append('"')
    for (c in s) when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        else -> append(c)
    }
    append('"')
}

private fun hitJson(h: SearchHit) =
    """{"name":${quote(h.place.name)},"lat":${h.place.lat},"lng":${h.place.lng},"category":${quote(h.categoryLabel)},"distanceM":${h.distanceM.toInt()}}"""

private fun routeJson(id: String, r: RoadRoute): String {
    val e = TunnelExposure.of(r)
    val points = r.points.joinToString(",") { "[${it.lat},${it.lng}]" }
    val tunnels = r.tunnels.joinToString(",") { """{"name":${quote(it.name)},"lengthM":${it.lengthM.toInt()},"startM":${it.startM},"endM":${it.endM}}""" }
    val turns = r.maneuvers.joinToString(",") { """{"atM":${it.atM},"label":${quote(it.label)},"road":${quote(it.roadName)}}""" }
    val cum = r.cumulativeM.joinToString(",") { "%.1f".format(Locale.ROOT, it) }
    val segments = r.roadSegments.joinToString(",") {
        """{"startM":${it.startM},"endM":${it.endM},"road":"${StopRoadType.of(false, it.roadClass).name}"}"""
    }
    return """{"id":"$id","segments":[$segments],"minutes":${r.minutes},"lengthM":${r.lengthM.toInt()},"tunnelCount":${e.count},"longestM":${e.longestM.toInt()},"longestSec":${e.longestSec},"tunnels":[$tunnels],"points":[$points],"cum":[$cum],"maneuvers":[$turns]}"""
}

private fun routeResponse(q: Map<String, String>): String {
    val to = LatLng(q.getValue("lat").toDouble(), q.getValue("lng").toDouble())
    val level = AvoidanceLevel.fromName(q["level"])
    val context = RouteContext(
        level = level,
        condition = DayCondition.entries.firstOrNull { it.name == q["condition"] } ?: DayCondition.NORMAL,
        urgency = TripUrgency.entries.firstOrNull { it.name == q["urgency"] } ?: TripUrgency.NORMAL,
        night = q["night"] == "true",
        hasPassenger = q["passenger"] == "true",
    )
    val limit = context.effectiveLimitM
    val from = q["fromLat"]?.toDoubleOrNull()?.let { LatLng(it, q.getValue("fromLng").toDouble()) } ?: DemoData.START
    val shortest = roads.route(from, to, AvoidanceLevel.NONE)
    val avoiding = if (level.avoidsAny) roads.route(from, to, limit) else shortest
    val advice = RoutePolicy.decide(context, shortest, avoiding)
    val routes = buildList {
        val same = shortest != null && avoiding != null &&
            avoiding.tunnelCount == shortest.tunnelCount && kotlin.math.abs(avoiding.lengthM - shortest.lengthM) <= 1.0
        if (avoiding != null && !same) add(routeJson("avoiding", avoiding))
        if (shortest != null) add(routeJson("shortest", shortest))
    }.joinToString(",")
    val identical = shortest != null && avoiding != null && avoiding.tunnelCount == shortest.tunnelCount && kotlin.math.abs(avoiding.lengthM - shortest.lengthM) <= 1.0
    val recommended = if (advice.recommended == RouteChoice.SHORTEST || avoiding == null || identical) "shortest" else "avoiding"
    val reasons = advice.reasons.joinToString(",") { quote(it) }
    val limitText = if (limit > 1e9) "null" else limit.toInt().toString()
    return """{"verdict":${quote(advice.verdict.label)},"code":"${advice.verdict.name}","headline":${quote(advice.headline)},"reasons":[$reasons],"limitM":$limitText,"recommended":"$recommended","routes":[$routes]}"""
}

private fun parkingResponse(q: Map<String, String>): String {
    val target = LatLng(q.getValue("lat").toDouble(), q.getValue("lng").toDouble())
    val preference = ParkingPreference(avoidEnclosed = q["avoid"] == "true")
    val ranked = ParkingRanker.rank(parking, target, preference)
    val advice = ParkingRanker.ruleAdvice(ranked, preference)
    val list = ranked.joinToString(",") {
        """{"id":${quote(it.lot.id)},"name":${quote(it.lot.displayName)},"kind":${quote(it.lot.kind.label)},"enclosed":${it.lot.kind.enclosed},"lat":${it.lot.lat},"lng":${it.lot.lng},"distanceM":${it.distanceM.toInt()}}"""
    }
    return """{"bestId":${advice?.lotId?.let(::quote)},"reason":${quote(advice?.reason.orEmpty())},"lots":[$list]}"""
}

private fun situationResponse(q: Map<String, String>): String {
    val plan = SituationPlanner.plan(
        HelpPerson.valueOf(q.getValue("person")),
        StopRoadType.valueOf(q["road"] ?: "UNKNOWN"),
        tunnelExitM = q["exitM"]?.toDoubleOrNull(),
        tunnelsAhead = q["ahead"] == "true",
    )
    val steps = plan.steps.joinToString(",") { quote(it) }
    val actions = plan.actions.joinToString(",") { """{"id":"${it.name}","label":${quote(it.label)}}""" }
    return """{"title":${quote(plan.title)},"spoken":${quote(plan.spoken)},"steps":[$steps],"actions":[$actions]}"""
}

private fun query(exchange: HttpExchange): Map<String, String> =
    exchange.requestURI.rawQuery.orEmpty().split('&').filter { it.contains('=') }.associate {
        val (k, v) = it.split('=', limit = 2)
        k to URLDecoder.decode(v, "UTF-8")
    }

private fun send(exchange: HttpExchange, status: Int, type: String, body: String) {
    val bytes = body.toByteArray()
    exchange.responseHeaders.add("Content-Type", "$type; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun json(exchange: HttpExchange, body: String) = send(exchange, 200, "application/json", body)

private fun handle(exchange: HttpExchange) {
    val q = query(exchange)
    val start = DemoData.START
    try {
        when (exchange.requestURI.path) {
            "/" -> send(exchange, 200, "text/html", resource("web/index.html"))
            "/api/places" -> json(exchange, "[" + places.joinToString(",") { hitJson(SearchHit(it, "장소", 0.0)) } + "]")
            "/api/search" -> json(exchange, "[" + PlaceSearch.search(q["q"].orEmpty(), start, places, pois, parking).joinToString(",", transform = ::hitJson) + "]")
            "/api/nearby" -> {
                val category = PoiCategory.fromName(q["category"])
                val near = q["lat"]?.toDoubleOrNull()?.let { LatLng(it, q.getValue("lng").toDouble()) } ?: start
                val hits = PlaceSearch.nearby(near, pois) { if (category != null) it == category else it.emergency }
                json(exchange, "[" + hits.joinToString(",", transform = ::hitJson) + "]")
            }
            "/api/route" -> json(exchange, routeResponse(q))
            "/api/parking" -> json(exchange, parkingResponse(q))
            "/api/situation" -> json(exchange, situationResponse(q))
            "/api/people" -> json(exchange, "[" + HelpPerson.entries.joinToString(",") { """{"id":"${it.name}","label":${quote(it.label)},"hint":${quote(it.hint)}}""" } + "]")
            else -> send(exchange, 404, "text/plain", "없음")
        }
    } catch (e: Exception) {
        send(exchange, 500, "application/json", """{"error":${quote(e.message ?: "오류")}}""")
    }
}

fun main() {
    println("도로망을 읽는 중입니다…")
    check(!roads.isEmpty) { "도로망 에셋을 읽지 못했습니다" }
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", PORT), 0)
    server.createContext("/") { handle(it) }
    server.executor = Executors.newFixedThreadPool(4)
    server.start()
    val url = "http://localhost:$PORT"
    println("터널프리 데스크톱 확인 도구: $url")
    runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) }
}
