package kr.tunnelfree.app.ui

import android.app.Application
import android.os.SystemClock
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.tunnelfree.app.domain.AvoidanceLevel
import kr.tunnelfree.app.domain.BreathingCycle
import kr.tunnelfree.app.domain.ChallengeRecord
import kr.tunnelfree.app.domain.Command
import kr.tunnelfree.app.domain.CommandDebouncer
import kr.tunnelfree.app.domain.CommandParser
import kr.tunnelfree.app.domain.DayCondition
import kr.tunnelfree.app.domain.DemoData
import kr.tunnelfree.app.domain.Daylight
import kr.tunnelfree.app.domain.DriveProgress
import kr.tunnelfree.app.domain.GuidanceSpeech
import kr.tunnelfree.app.domain.HelpAction
import kr.tunnelfree.app.domain.HelpPerson
import kr.tunnelfree.app.domain.InTunnelAnnouncer
import kr.tunnelfree.app.domain.IncidentClassifier
import kr.tunnelfree.app.domain.IncidentKind
import kr.tunnelfree.app.domain.Korean
import kr.tunnelfree.app.domain.LatLng
import kr.tunnelfree.app.domain.ManeuverAnnouncer
import kr.tunnelfree.app.domain.MusicContext
import kr.tunnelfree.app.domain.MusicPolicy
import kr.tunnelfree.app.domain.MusicState
import kr.tunnelfree.app.domain.ParkingDatabase
import kr.tunnelfree.app.domain.ParkingLot
import kr.tunnelfree.app.domain.ParkingPreference
import kr.tunnelfree.app.domain.ParkingRanker
import kr.tunnelfree.app.domain.Place
import kr.tunnelfree.app.domain.PlaceSearch
import kr.tunnelfree.app.domain.Poi
import kr.tunnelfree.app.domain.PoiCategory
import kr.tunnelfree.app.domain.PoiDatabase
import kr.tunnelfree.app.domain.RestBeforeTunnel
import kr.tunnelfree.app.domain.RoadNetwork
import kr.tunnelfree.app.domain.RoadRoute
import kr.tunnelfree.app.domain.RouteAdvice
import kr.tunnelfree.app.domain.RouteChoice
import kr.tunnelfree.app.domain.RouteContext
import kr.tunnelfree.app.domain.RouteFailure
import kr.tunnelfree.app.domain.RoutePlan
import kr.tunnelfree.app.domain.RoutePlanner
import kr.tunnelfree.app.domain.RoutePolicy
import kr.tunnelfree.app.domain.RouteVerdict
import kr.tunnelfree.app.domain.SafeStopGuide
import kr.tunnelfree.app.domain.SafeStopMonitor
import kr.tunnelfree.app.domain.SituationPlanner
import kr.tunnelfree.app.domain.StopRoadType
import kr.tunnelfree.app.domain.TripUrgency
import kr.tunnelfree.app.domain.Tunnel
import kr.tunnelfree.app.domain.TunnelAheadAlert
import kr.tunnelfree.app.domain.TunnelAheadCheck
import kr.tunnelfree.app.domain.TunnelDatabase
import kr.tunnelfree.app.domain.TunnelEvent
import kr.tunnelfree.app.domain.TunnelMonitor
import kr.tunnelfree.app.domain.TunnelRating
import kr.tunnelfree.app.domain.VehicleContext
import kr.tunnelfree.app.domain.distanceMeters
import kr.tunnelfree.app.domain.spokenDistance
import kr.tunnelfree.app.platform.GeoPoint
import kr.tunnelfree.app.platform.GeoZone
import kr.tunnelfree.app.platform.PlatformFactory
import kr.tunnelfree.app.platform.PlatformServices
import kr.tunnelfree.app.platform.VehicleSignalListener
import java.time.LocalDate

class TunnelFreeViewModel(application: Application) : AndroidViewModel(application) {

    private val services: PlatformServices = PlatformFactory.create(application)
    private val prefs = application.getSharedPreferences("tunnelfree", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        UiState(
            platformName = services.platformName,
            naviAvailable = services.navi.isAvailable,
            simulated = services.platformName != "pleos",
            guardianPhone = prefs.getString(KEY_GUARDIAN, "").orEmpty(),
            avoidanceLevel = AvoidanceLevel.fromName(prefs.getString(KEY_AVOIDANCE, null)),
            autoMusic = prefs.getBoolean(KEY_AUTO_MUSIC, true),
            challengeMode = prefs.getBoolean(KEY_CHALLENGE, false),
            challengeCountToday = loadChallenge().countOn(LocalDate.now().toString()),
            challengeLongestM = loadChallenge().longestM,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _dialRequests = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val dialRequests: SharedFlow<String> = _dialRequests.asSharedFlow()

    private var driveJob: Job? = null
    private var breathJob: Job? = null
    private var incidentJob: Job? = null
    private var listenTimeoutJob: Job? = null
    private var speechWatchdog: Job? = null
    private var speechToken = 0L
    private var pendingGuidance: Pair<String, (() -> Unit)?>? = null
    private var naviHandedOff = false
    private var musicGraceJob: Job? = null
    private var musicFadeJob: Job? = null
    private var musicVolume = 1f
    private var routingJob: Job? = null
    private val maneuverAnnouncer = ManeuverAnnouncer()
    private val tunnelAheadCheck = TunnelAheadCheck()
    private val inTunnelAnnouncer = InTunnelAnnouncer()
    private var distressAtMs = 0L

    @Volatile private var roads: RoadNetwork = RoadNetwork.EMPTY
    @Volatile private var parkingLots: List<ParkingLot> = emptyList()
    @Volatile private var pois: List<Poi> = emptyList()
    private var ratings: List<TunnelRating> = emptyList()
    private var parkingSuggested = false

    private val tunnelDb: TunnelDatabase = runCatching {
        application.assets.open("tunnels_daejeon.csv").bufferedReader().use { TunnelDatabase.parse(it.readText()).mergeSegments() }
    }.getOrElse { TunnelDatabase.EMPTY }
    private val tunnelMonitor = TunnelMonitor(tunnelDb)
    private var zonesAnchor: LatLng? = null

    private val commandDebouncer = CommandDebouncer()

    init {
        _state.update {
            it.copy(
                mapTunnels = tunnelDb.tunnels,
                vehicle = it.vehicle.copy(connected = services.vehicleSignals.hasVehicleSignals),
            )
        }
        ratings = parseRatings(prefs.getString(KEY_RATINGS, "").orEmpty())
        _state.update {
            it.copy(
                ratingCount = ratings.size,
                recentPlaces = parseRecent(prefs.getString(KEY_RECENT, "").orEmpty()),
                tunnelLimitM = routeContext(it.avoidanceLevel).effectiveLimitM,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            parkingLots = readAsset("parking_daejeon.csv") { ParkingDatabase.parse(it) }.orEmpty()
            pois = readAsset("pois_daejeon.csv") { PoiDatabase.parse(it) }.orEmpty()
        }
        viewModelScope.launch {
            roads = withContext(Dispatchers.IO) {
                runCatching {
                    application.assets.open("roads_daejeon.txt").bufferedReader().use { RoadNetwork.parse(it) }
                }.getOrElse { e ->
                    android.util.Log.e("TunnelFree", "도로망 에셋 로딩 실패", e)
                    RoadNetwork.EMPTY
                }
            }
            _state.update { it.copy(roadsReady = !roads.isEmpty) }
            if (roads.isEmpty) message("도로망 데이터를 불러오지 못했습니다.")
        }
        services.locationSource.start { point -> onLocation(point) }
        services.vehicleSignals.start(object : VehicleSignalListener {
            override fun onHardDeceleration(deceleration: Double) = onHardStop(deceleration)

            override fun onBrakePedal(pressed: Boolean) = updateVehicle {
                it.copy(brakePressed = pressed, lastBrakeAt = if (pressed) System.currentTimeMillis() else it.lastBrakeAt)
            }

            override fun onSpeedEstimate(kmh: Double) = updateVehicle { it.copy(speedKmh = kmh) }

            override fun onSteeringAngle(degrees: Float) = updateVehicle {
                val changed = it.steeringAngle?.let { prev -> kotlin.math.abs(prev - degrees) > STEERING_CHANGE_DEG } ?: false
                it.copy(steeringAngle = degrees, lastSteeringChangeAt = if (changed) System.currentTimeMillis() else it.lastSteeringChangeAt)
            }

            override fun onTurnSignal(state: String) = updateVehicle { it.copy(turnSignal = state) }

            override fun onSeatBelt(buckled: Boolean) {
                updateVehicle { it.copy(seatBeltBuckled = buckled) }
                if (!buckled) evaluateIncident(deceleration = 0.0, beltReleased = true)
            }

            override fun onAirbagDeployed() = evaluateIncident(deceleration = 0.0, airbag = true)
        })

        viewModelScope.launch {
            state.map { musicContext(it) }.distinctUntilChanged().collect { applyMusic(MusicPolicy.decide(it)) }
        }
    }

    private fun updateVehicle(transform: (VehicleTelemetry) -> VehicleTelemetry) {
        _state.update { it.copy(vehicle = transform(it.vehicle).copy(connected = services.vehicleSignals.hasVehicleSignals)) }
    }

    private fun message(text: String) {
        _messages.tryEmit(text)
    }

    private fun say(text: String, onDone: (() -> Unit)? = null) {
        pendingGuidance = null
        speakNow(text, onDone)
    }

    private fun announce(text: String, onDone: (() -> Unit)? = null) {
        val s = _state.value
        if (s.emergencyActive) return
        if (s.speaking) pendingGuidance = text to onDone else say(text, onDone)
    }

    private fun speakNow(text: String, onDone: (() -> Unit)?) {
        services.speechInput.stop()
        val token = ++speechToken
        _state.update { it.copy(listening = false, speaking = true) }
        speechWatchdog?.cancel()
        speechWatchdog = viewModelScope.launch {
            delay(text.length * SPEECH_MS_PER_CHAR + SPEECH_MARGIN_MS)
            finishSpeech(token, onDone)
        }
        services.speechOutput.speak(text) {
            viewModelScope.launch { finishSpeech(token, onDone) }
        }
    }

    private fun finishSpeech(token: Long, onDone: (() -> Unit)?) {
        if (token != speechToken) return
        speechToken++
        speechWatchdog?.cancel()
        _state.update { it.copy(speaking = false) }
        val next = pendingGuidance
        if (next != null) {
            pendingGuidance = null
            speakNow(next.first, next.second ?: onDone)
        } else {
            onDone?.invoke()
        }
    }

    private fun stopSpeech() {
        speechToken++
        speechWatchdog?.cancel()
        pendingGuidance = null
        services.speechOutput.stop()
    }

    private fun musicContext(s: UiState) = MusicContext(
        autoPlayOnTunnel = s.autoMusic,
        insideTunnel = s.insideRealTunnel,
        exitGraceActive = s.musicExitGrace,
        distress = s.breathing,
        userRequested = s.musicUserRequested,
        userStoppedThisSession = s.musicUserStopped,
        emergencyActive = s.emergencyActive,
        voiceActive = s.speaking || s.listening,
    )

    private fun applyMusic(target: MusicState) {
        musicFadeJob?.cancel()
        when (target) {
            MusicState.PLAYING -> { musicVolume = 1f; services.music.setVolume(1f); services.music.play() }
            MusicState.DUCKED -> { musicVolume = MusicPolicy.DUCK_VOLUME; services.music.setVolume(musicVolume); services.music.play() }
            MusicState.OFF -> if (services.music.isPlaying) {
                musicFadeJob = viewModelScope.launch {
                    val start = musicVolume
                    for (i in 1..6) {
                        musicVolume = start * (1 - i / 6f)
                        services.music.setVolume(musicVolume)
                        delay(250)
                    }
                    services.music.pause()
                }
            }
        }
        _state.update { it.copy(musicState = target) }
    }

    private fun startMusicGrace() {
        musicGraceJob?.cancel()
        _state.update { it.copy(musicExitGrace = true) }
        musicGraceJob = viewModelScope.launch {
            delay(MusicPolicy.EXIT_GRACE_MS)
            _state.update { it.copy(musicExitGrace = false) }
        }
    }

    fun requestMusic() {
        _state.update { it.copy(musicUserRequested = true, musicUserStopped = false) }
    }

    fun stopMusic() {
        _state.update { it.copy(musicUserRequested = false, musicUserStopped = true) }
    }

    fun toggleMusic() {
        if (_state.value.musicState != MusicState.OFF) stopMusic() else requestMusic()
    }

    fun setAutoMusic(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_MUSIC, value).apply()
        _state.update { it.copy(autoMusic = value) }
    }

    private fun originPoint(): LatLng =
        RoutePlanner.origin(_state.value.gps?.let { LatLng(it.lat, it.lng) }, roads)

    private fun routeStart(): LatLng = _state.value.let { if (it.running) it.position else originPoint() }

    private fun computeRoutes(
        from: LatLng,
        destination: Place,
        via: Place?,
        level: AvoidanceLevel,
        forceShortest: Boolean = false,
        onDone: (RoutePlan) -> Unit,
    ) {
        routingJob?.cancel()
        _state.update { it.copy(routing = true) }
        routingJob = viewModelScope.launch {
            val context = routeContext(level)
            val limit = context.effectiveLimitM
            val (avoiding, shortest) = withContext(Dispatchers.Default) {
                val s = roads.route(from, destination.latLng, AvoidanceLevel.NONE, via?.latLng)
                val a = if (level.avoidsAny) roads.route(from, destination.latLng, limit, via?.latLng) else s
                a to s
            }
            val advice = RoutePolicy.decide(context, shortest, avoiding)
            val useShortest = forceShortest || advice.recommended == RouteChoice.SHORTEST
            val first = if (useShortest) shortest ?: avoiding else avoiding ?: shortest
            val second = if (first === shortest) avoiding else shortest
            val failure = if (first != null) null else RoutePlanner.failureOf(from, destination.latLng, shortest)
            _state.update { it.copy(routing = false, routeAdvice = advice, tunnelLimitM = limit) }
            onDone(RoutePlan(first, RoutePlanner.comparisonOf(first, second), failure))
        }
    }

    private fun applyRoute(from: LatLng, route: RoadRoute, comparison: RoadRoute?, destination: Place, via: Place?) {
        maneuverAnnouncer.reset()
        tunnelAheadCheck.reset()
        inTunnelAnnouncer.reset()
        _state.update {
            it.copy(
                origin = from, destination = destination, via = via, route = route, comparison = comparison, progressM = 0.0, arrived = false,
                tunnelAhead = null, restBeforeTunnel = restStopFor(route, it.routeAdvice, via),
            )
        }
        updateTunnelRelevance()
    }

    private fun restStopFor(route: RoadRoute, advice: RouteAdvice?, via: Place?): Place? {
        if (via != null || advice?.verdict != RouteVerdict.MUST_PASS) return null
        val longest = route.tunnels.maxByOrNull { it.lengthM } ?: return null
        return RestBeforeTunnel.find(route, longest, pois)?.place
    }

    fun addRestBeforeTunnel() {
        val place = _state.value.restBeforeTunnel ?: return
        addVia(place)
    }

    private fun updateTunnelRelevance() {
        val s = _state.value
        val route = s.route
        tunnelMonitor.relevance =
            if (s.running && route != null) { t -> route.hasTunnelNear(t.entry) || route.hasTunnelNear(t.exit) }
            else { _ -> true }
    }

    fun selectDestination(index: Int) = selectDestination(DemoData.places[index])

    fun selectPoint(lat: Double, lng: Double) = selectDestination(Place("지도에서 고른 지점", lat, lng))

    fun selectDestination(place: Place) = selectDestination(place, byVoice = false)

    private fun selectDestination(place: Place, byVoice: Boolean) {
        if (!_state.value.roadsReady) {
            message("도로망을 불러오는 중입니다. 잠시 후 다시 시도하세요.")
            return
        }
        val s = _state.value
        val from = routeStart()
        parkingSuggested = false
        computeRoutes(from, place, s.via, s.avoidanceLevel) { (route, comparison, failure) ->
            if (route == null) {
                message(
                    when (failure) {
                        RouteFailure.TOO_CLOSE -> "이미 목적지 근처입니다."
                        RouteFailure.UNREACHABLE -> "목적지까지 도로로 이어진 경로를 찾지 못했습니다."
                        RouteFailure.AVOIDANCE_BLOCKS, null -> "터널을 피하는 경로가 없습니다. 회피 강도를 낮춰 보세요."
                    }
                )
                return@computeRoutes
            }
            applyRoute(from, route, comparison, place, s.via)
            _state.update { it.copy(journeyStatus = "${place.name} 경로 준비", navScreen = NavScreen.MENU) }
            val ready = GuidanceSpeech.routeReady(place.name, route)
            if (byVoice) say("$ready ${GuidanceSpeech.START_HINT}") { listenForAnswer() } else say(ready)
        }
    }

    fun setAvoidanceLevel(level: AvoidanceLevel) {
        val s = _state.value
        if (s.avoidanceLevel == level) return
        prefs.edit().putString(KEY_AVOIDANCE, level.name).apply()
        _state.update { it.copy(avoidanceLevel = level) }
        val destination = s.destination
        if (destination == null) {
            sayLevel(level)
            return
        }
        val from = routeStart()
        computeRoutes(from, destination, s.via, level) { (route, comparison) ->
            if (route == null) {
                _state.update { it.copy(avoidanceLevel = s.avoidanceLevel) }
                message("이 회피 강도로는 경로가 없어 설정을 되돌렸습니다.")
                return@computeRoutes
            }
            applyRoute(from, route, comparison, destination, s.via)
            sayLevel(level)
        }
    }

    private fun routeContext(level: AvoidanceLevel): RouteContext {
        val s = _state.value
        return RouteContext(
            level, ratings, s.dayCondition, s.urgency, Daylight.isNight(System.currentTimeMillis()), s.hasPassenger, recentDistress(),
        )
    }

    private fun recentDistress(): Boolean =
        distressAtMs > 0 && System.currentTimeMillis() - distressAtMs < RoutePolicy.DISTRESS_WINDOW_MS

    private fun markDistress() {
        distressAtMs = System.currentTimeMillis()
        tunnelAheadCheck.reset()
        _state.update { it.copy(tunnelLimitM = routeContext(it.avoidanceLevel).effectiveLimitM) }
    }

    private fun recomputeRoute(forceShortest: Boolean = false) {
        val s = _state.value
        _state.update { it.copy(tunnelLimitM = routeContext(it.avoidanceLevel).effectiveLimitM, nightDrive = routeContext(it.avoidanceLevel).night) }
        val destination = s.destination ?: return
        if (s.running) return
        val from = routeStart()
        computeRoutes(from, destination, s.via, s.avoidanceLevel, forceShortest) { (route, comparison) ->
            if (route != null) applyRoute(from, route, comparison, destination, s.via)
        }
    }

    fun setDayCondition(value: DayCondition) {
        _state.update { it.copy(dayCondition = value) }
        recomputeRoute()
    }

    fun setUrgency(value: TripUrgency) {
        _state.update { it.copy(urgency = value) }
        recomputeRoute()
    }

    fun setPassenger(value: Boolean) {
        _state.update { it.copy(hasPassenger = value) }
        recomputeRoute()
    }

    fun sendToCarNavi() {
        val s = _state.value
        val destination = s.destination ?: return
        if (!services.navi.isAvailable) return
        naviHandedOff = true
        say("차량 내비로 보냅니다. 차량 내비 경로에는 터널이 있을 수 있습니다.") {
            services.navi.requestRoute(destination)
            s.via?.let { services.navi.addWaypoint(it) }
        }
    }

    private fun cancelCarNavi() {
        if (naviHandedOff) services.navi.cancelRoute()
        naviHandedOff = false
    }

    fun selectAlternativeRoute() {
        val s = _state.value
        val alt = s.comparison ?: return
        _state.update { it.copy(route = alt, comparison = s.route) }
    }

    fun rateTunnel(distress: Int) {
        val length = _state.value.pendingRatingM ?: return
        ratings = (ratings + TunnelRating(length, distress, accompanied = _state.value.hasPassenger)).takeLast(MAX_RATINGS)
        prefs.edit().putString(KEY_RATINGS, ratings.joinToString(",") { "${it.lengthM.toInt()}:${it.distress}:${if (it.accompanied) 1 else 0}" }).apply()
        _state.update { it.copy(pendingRatingM = null, ratingCount = ratings.size, tunnelLimitM = routeContext(it.avoidanceLevel).effectiveLimitM) }
        message("평가를 저장했습니다. 다음 경로 판단에 반영합니다.")
    }

    fun skipTunnelRating() = _state.update { it.copy(pendingRatingM = null) }

    private fun parseRatings(text: String): List<TunnelRating> = text.split(',').mapNotNull { part ->
        val f = part.split(':')
        val length = f.getOrNull(0)?.toDoubleOrNull()
        val distress = f.getOrNull(1)?.toIntOrNull()
        if (length != null && distress != null) TunnelRating(length, distress, accompanied = f.getOrNull(2) == "1") else null
    }

    private fun <T> readAsset(name: String, parse: (String) -> T): T? = runCatching {
        getApplication<Application>().assets.open(name).bufferedReader().use { parse(it.readText()) }
    }.getOrElse { e ->
        android.util.Log.e("TunnelFree", "$name 로딩 실패", e)
        null
    }

    fun openNav(screen: NavScreen) {
        _state.update { it.copy(navScreen = screen) }
        if (screen == NavScreen.PARKING) showParking(autoSpeak = false)
    }

    fun search(query: String) {
        val origin = _state.value.position
        val hits = PlaceSearch.search(query, origin, DemoData.destinationIndices.map { DemoData.places[it] }, pois, parkingLots)
        _state.update { it.copy(searchQuery = query, searchResults = hits) }
    }

    fun showNearby(category: PoiCategory?) {
        val origin = _state.value.position
        val hits = PlaceSearch.nearby(origin, pois) { if (category != null) it == category else it.emergency }
        _state.update { it.copy(nearbyCategory = category, searchResults = hits) }
    }

    private fun rememberRecent(place: Place) {
        val list = (listOf(place) + _state.value.recentPlaces.filter { it.name != place.name }).take(MAX_RECENT)
        saveRecent(list)
    }

    fun removeRecent(place: Place) = saveRecent(_state.value.recentPlaces.filter { it != place })

    private fun saveRecent(list: List<Place>) {
        prefs.edit().putString(KEY_RECENT, list.joinToString("\n") { "${it.name}|${it.lat}|${it.lng}" }).apply()
        _state.update { it.copy(recentPlaces = list) }
    }

    private fun parseRecent(text: String): List<Place> = text.lines().mapNotNull { line ->
        val f = line.split('|')
        val lat = f.getOrNull(1)?.toDoubleOrNull()
        val lng = f.getOrNull(2)?.toDoubleOrNull()
        if (lat != null && lng != null) Place(f[0], lat, lng) else null
    }

    private fun showParking(autoSpeak: Boolean) {
        val s = _state.value
        val target = s.destination?.latLng ?: s.position
        val name = s.destination?.name ?: "현재 위치"
        val preference = ParkingPreference(avoidEnclosed = s.avoidanceLevel.avoidsAny)
        val candidates = ParkingRanker.rank(parkingLots, target, preference)
        val advice = ParkingRanker.ruleAdvice(candidates, preference)
        _state.update {
            it.copy(
                navScreen = if (autoSpeak && it.tunnelAhead != null) it.navScreen else NavScreen.PARKING,
                parking = ParkingUi(name, candidates, advice, PARKING_NOTE),
            )
        }
        val lot = candidates.firstOrNull { it.lot.id == advice?.lotId }?.lot
        if (autoSpeak && lot != null) announce("목적지 근처 주차장을 안내합니다. ${lot.displayName}, ${lot.kind.label} 주차장입니다.")
    }

    fun openSituation(person: HelpPerson) {
        if (person == HelpPerson.DRIVER_ANXIOUS) markDistress()
        val s = _state.value
        val route = s.route
        val tunnel = route?.tunnelAt(s.progressM)
        val plan = SituationPlanner.plan(
            person,
            currentStopRoad(),
            tunnelExitM = tunnel?.let { it.endM - s.progressM },
            tunnelsAhead = route?.tunnelsAfter(s.progressM)?.isNotEmpty() == true,
            hasGuardian = s.guardianPhone.isNotBlank(),
        )
        _state.update {
            it.copy(situation = plan, urgency = if (person == HelpPerson.PASSENGER_SICK) TripUrgency.MEDICAL else it.urgency)
        }
        val choices = GuidanceSpeech.helpChoices(plan.actions)
        say("${plan.spoken} $choices".trim()) { if (choices.isNotEmpty()) listenForAnswer() }
    }

    fun closeSituation() {
        stopListening()
        _state.update { it.copy(situation = null) }
    }

    fun runHelpAction(action: HelpAction) {
        when (action) {
            HelpAction.NEAREST_REST -> goToNearest(PoiCategory.REST_AREA)
            HelpAction.NEAREST_HOSPITAL -> goToNearest(PoiCategory.HOSPITAL)
            HelpAction.AVOID_TUNNELS -> setAvoidanceLevel(AvoidanceLevel.FULL)
            HelpAction.BREATHING -> if (!_state.value.breathing) toggleBreathing()
            HelpAction.CALL_119 -> call119()
            HelpAction.CALL_ROADSIDE -> dial(SafeStopGuide.EXPRESSWAY_CALL_CENTER)
            HelpAction.CALL_GUARDIAN -> callGuardian()
        }
    }

    private fun goToNearest(category: PoiCategory) {
        val s = _state.value
        val from = routeStart()
        val candidates = PlaceSearch.nearby(from, pois) { it == category }.take(NEAREST_CANDIDATES)
        if (candidates.isEmpty()) {
            message("가까운 ${category.label} 정보를 찾지 못했습니다.")
            return
        }
        viewModelScope.launch {
            val best = withContext(Dispatchers.Default) {
                candidates.mapNotNull { hit -> roads.route(from, hit.place.latLng, s.avoidanceLevel)?.let { hit to it } }
                    .minByOrNull { (_, route) -> route.travelSec }?.first
            }
            if (best == null) message("${category.label}까지 가는 도로를 찾지 못했습니다.")
            else {
                closeSituation()
                rerouteTo(best.place)
            }
        }
    }

    private fun addNearestRest() {
        if (_state.value.destination == null) {
            goToNearest(PoiCategory.REST_AREA)
            return
        }
        val hit = PlaceSearch.nearby(routeStart(), pois) { it == PoiCategory.REST_AREA }.firstOrNull()
        if (hit == null) message("가까운 ${PoiCategory.REST_AREA.label} 정보를 찾지 못했습니다.") else addVia(hit.place)
    }

    fun goToParking(lot: ParkingLot) = rerouteTo(Place(lot.displayName, lot.lat, lot.lng))

    private fun rerouteTo(place: Place) {
        val wasRunning = _state.value.running
        val from = routeStart()
        parkingSuggested = true
        computeRoutes(from, place, null, _state.value.avoidanceLevel) { (route, comparison) ->
            if (route == null) {
                message("${place.name}까지 가는 도로를 찾지 못했습니다.")
                return@computeRoutes
            }
            applyRoute(from, route, comparison, place, null)
            _state.update { it.copy(navScreen = NavScreen.MENU, arrived = false, running = false) }
            say("${place.name}로 안내합니다. ${spokenDistance(route.lengthM)}입니다.")
            if (wasRunning) startDrive()
        }
    }

    private fun sayLevel(level: AvoidanceLevel) = say(GuidanceSpeech.avoidanceLevel(level))

    fun setAvoidTunnel(value: Boolean) = setAvoidanceLevel(if (value) AvoidanceLevel.FULL else AvoidanceLevel.NONE)

    fun addVia(index: Int) = addVia(DemoData.places[index])

    fun addVia(place: Place) {
        val s = _state.value
        val destination = s.destination
        if (destination == null) {
            message("먼저 목적지를 선택하세요.")
            return
        }
        val from = routeStart()
        computeRoutes(from, destination, place, s.avoidanceLevel) { (route, comparison) ->
            if (route == null) {
                message("경유지를 포함한 경로를 찾지 못했습니다.")
                return@computeRoutes
            }
            applyRoute(from, route, comparison, destination, place)
            if (naviHandedOff) services.navi.addWaypoint(place)
            say("${Korean.withObject(place.name)} 경유합니다.")
        }
    }

    fun removeVia() {
        val s = _state.value
        val destination = s.destination
        if (s.via == null || destination == null) return
        val from = routeStart()
        computeRoutes(from, destination, null, s.avoidanceLevel) { (route, comparison) ->
            if (route == null) return@computeRoutes
            applyRoute(from, route, comparison, destination, null)
            if (naviHandedOff) services.navi.removeWaypoint()
            say("경유지를 취소했습니다.")
        }
    }

    fun startDrive() {
        val s = _state.value
        val route = s.route
        if (s.destination == null || route == null) {
            message("먼저 목적지를 선택하세요.")
            return
        }
        if (s.running) {
            if (s.paused) togglePause()
            return
        }
        maneuverAnnouncer.reset()
        tunnelMonitor.reset()
        s.destination?.let { rememberRecent(it) }
        val simulate = DriveProgress.needsSimulation(
            hasVehicleGps = services.platformName == "pleos",
            gps = s.gps?.let { LatLng(it.lat, it.lng) },
            routeStart = route.pointAt(0.0),
        )
        _state.update {
            it.copy(simulated = simulate, running = true, navScreen = NavScreen.MENU, paused = false, arrived = false, progressM = 0.0, journeyStatus = if (simulate) STATUS_DRIVING else STATUS_GUIDING)
        }
        updateTunnelRelevance()
        if (simulate) startTimer()
        say(GuidanceSpeech.driveStarted(route))
    }

    private fun startTimer() {
        driveJob?.cancel()
        driveJob = viewModelScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(DriveProgress.SIM_TICK_MS)
                val now = SystemClock.elapsedRealtime()
                advance(DriveProgress.stepForElapsed(now - last))
                last = now
            }
        }
    }

    private fun advance(stepM: Double) {
        val s = _state.value
        val route = s.route ?: return
        if (!s.running || s.paused || s.helpOpen) return

        val step = DriveProgress.simulateStep(route, s.progressM, viaPending = s.via != null, stepM = stepM)
        val progress = step.progressM
        val reachedVia = step.reachedVia

        _state.update {
            it.copy(
                progressM = progress,
                via = if (reachedVia) null else it.via,
                paused = if (reachedVia) true else it.paused,
                journeyStatus = if (reachedVia) "경유지 도착 · 다시 출발을 눌러주세요" else it.journeyStatus,
            )
        }
        trackTunnels(route.pointAt(progress))
        announceManeuvers(route, progress)

        if (reachedVia) {
            driveJob?.cancel()
            say("경유지에 도착했습니다. 준비되면 다시 출발하세요.")
        }
        if (step.arrived) finishArrival()
    }

    private fun announceManeuvers(route: RoadRoute, progress: Double) {
        if (!parkingSuggested && route.remainingM(progress) <= PARKING_SUGGEST_M) {
            parkingSuggested = true
            showParking(autoSpeak = true)
        }
        val s = _state.value
        val now = System.currentTimeMillis()
        tunnelAheadCheck.update(route, progress, now, s.effectiveSpeedKmh, routeContext(s.avoidanceLevel).effectiveLimitM, recentDistress())
            ?.let { onTunnelAhead(it) }
        val canSpeak = !s.speaking && !s.emergencyActive
        val turn = maneuverAnnouncer.update(route, progress, canSpeak)
        if (turn != null) {
            say(turn) { if (_state.value.tunnelAhead?.detour != null) listenForAnswer() }
        } else {
            inTunnelAnnouncer.update(route, progress, now, canSpeak)?.let { say(it) }
        }
    }

    private fun onTunnelAhead(alert: TunnelAheadAlert) {
        val s = _state.value
        val destination = s.destination ?: return
        val from = routeStart()
        val limit = minOf(routeContext(s.avoidanceLevel).effectiveLimitM, alert.tunnel.lengthM - 1.0).coerceAtLeast(0.0)
        viewModelScope.launch {
            val detour = withContext(Dispatchers.Default) {
                roads.route(from, destination.latLng, limit, s.via?.latLng)
                    ?.takeIf { r -> r.tunnels.none { it.name == alert.tunnel.name && kotlin.math.abs(it.lengthM - alert.tunnel.lengthM) < 1.0 } }
            }
            val text = GuidanceSpeech.tunnelAhead(alert, detour != null)
            val title = if (alert.congested) "터널 앞 정체" else "다음 터널 다시 확인"
            _state.update { it.copy(tunnelAhead = TunnelAheadUi(title, text, detour), navScreen = NavScreen.MENU) }
            announce(text) { if (detour != null) listenForAnswer() }
        }
    }

    fun acceptTunnelDetour() {
        val s = _state.value
        val detour = s.tunnelAhead?.detour ?: return
        val destination = s.destination ?: return
        applyRoute(s.position, detour, null, destination, s.via)
        say("터널을 피하는 길로 바꿨습니다.")
    }

    fun dismissTunnelAhead() {
        stopListening()
        _state.update { it.copy(tunnelAhead = null) }
    }

    private fun finishArrival() {
        driveJob?.cancel()
        stopBreathing()
        val wasInTunnel = _state.value.insideRealTunnel
        _state.update {
            val longestTunnelM = it.route?.tunnels?.maxOfOrNull { t -> t.lengthM }
            it.copy(
                running = false, paused = false, arrived = true, progressM = it.route?.lengthM ?: it.progressM, journeyStatus = "목적지에 도착했습니다",
                pendingRatingM = longestTunnelM,
                navScreen = if (it.navScreen == NavScreen.PARKING && longestTunnelM == null) NavScreen.PARKING else NavScreen.MENU,
            )
        }
        updateTunnelRelevance()
        if (wasInTunnel) startMusicGrace()
        cancelCarNavi()
        if (_state.value.pendingRatingM != null) {
            say("목적지에 도착했습니다. 수고하셨습니다. ${GuidanceSpeech.RATING_QUESTION}") { listenForAnswer() }
        } else {
            say("목적지에 도착했습니다. 수고하셨습니다.")
        }
    }

    fun togglePause() {
        val s = _state.value
        if (!s.running) return
        val paused = !s.paused
        _state.update {
            it.copy(paused = paused, journeyStatus = if (paused) "모의 주행 일시정지" else STATUS_DRIVING)
        }
        if (paused) driveJob?.cancel() else if (s.simulated) startTimer()
    }

    fun endDrive() {
        driveJob?.cancel()
        incidentJob?.cancel()
        musicGraceJob?.cancel()
        stopBreathing()
        services.speechInput.stop()
        stopSpeech()
        cancelCarNavi()
        _state.update {
            it.copy(
                running = false,
                paused = false,
                listening = false,
                speaking = false,
                destination = null,
                via = null,
                route = null,
                comparison = null,
                routeAdvice = null,
                pendingRatingM = null,
                situation = null,
                navScreen = NavScreen.MENU,
                parking = ParkingUi(),
                progressM = 0.0,
                arrived = false,
                origin = originPoint(),
                incident = IncidentState.None,
                journeyStatus = "목적지를 선택하세요",
                musicUserRequested = false,
                musicUserStopped = false,
                musicExitGrace = false,
            )
        }
        updateTunnelRelevance()
    }

    fun toggleBreathing() {
        if (_state.value.breathing) {
            stopBreathing()
            return
        }
        _state.update { it.copy(breathing = true, breathText = BreathingCycle.FIRST_TEXT) }
        say("호흡 안내를 켰습니다. 크게 들이마시지 말고 천천히 내쉬세요. 불편하면 중지하세요.")
        breathJob?.cancel()
        breathJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                delay(1000)
                tick = BreathingCycle.next(tick)
                _state.update { it.copy(breathText = BreathingCycle.text(tick)) }
            }
        }
    }

    fun stopBreathing() {
        breathJob?.cancel()
        breathJob = null
        _state.update { it.copy(breathing = false, breathText = "") }
    }

    fun openHelp() {
        if (_state.value.helpOpen) return
        markDistress()
        driveJob?.cancel()
        incidentJob?.cancel()
        stopBreathing()
        _state.update {
            it.copy(helpOpen = true, paused = it.running, incident = IncidentState.None)
        }
        say("도움 요청 화면을 열었습니다. 자동 신고는 실행되지 않습니다. ${GuidanceSpeech.HELP_HINT}") { listenForAnswer() }
    }

    fun closeHelp() {
        stopListening()
        _state.update { it.copy(helpOpen = false) }
    }

    fun dismissIncident() {
        incidentJob?.cancel()
        services.speechInput.stop()
        if (_state.value.incident !is IncidentState.None) {
            _state.update { it.copy(incident = IncidentState.None, listening = false) }
            say("알겠습니다. 안내를 계속합니다.")
        }
    }

    fun callGuardian() = dial(_state.value.guardianPhone)
    fun call119() = dial("119")

    private fun dial(raw: String) {
        val number = raw.filter { it.isDigit() || it == '+' }
        if (number.isEmpty()) {
            message("설정에서 보호자 연락처를 입력하세요.")
            return
        }
        _dialRequests.tryEmit(number)
    }

    fun saveGuardianPhone(phone: String) {
        prefs.edit().putString(KEY_GUARDIAN, phone).apply()
        _state.update { it.copy(guardianPhone = phone) }
    }

    fun setChallengeMode(value: Boolean) {
        prefs.edit().putBoolean(KEY_CHALLENGE, value).apply()
        _state.update { it.copy(challengeMode = value) }
        if (value) message("도전 모드: 통과한 터널을 기록합니다. 전문가 치료를 대체하지 않습니다.")
    }

    private fun onHardStop(deceleration: Double) = evaluateIncident(deceleration)

    private fun evaluateIncident(deceleration: Double, airbag: Boolean = false, beltReleased: Boolean = false) {
        val s = _state.value
        if (!s.running || s.helpOpen || s.incident !is IncidentState.None) return

        val now = System.currentTimeMillis()
        val v = s.vehicle
        val ctx = VehicleContext(
            decelerationMps2 = deceleration,
            brakePressedAgoMs = if (v.lastBrakeAt > 0) now - v.lastBrakeAt else null,
            steeringChangedAgoMs = if (v.lastSteeringChangeAt > 0) now - v.lastSteeringChangeAt else null,
            turnSignalOn = v.turnSignal != "OFF",
            airbagDeployed = airbag,
            seatBeltBuckled = v.seatBeltBuckled,
            speedKmh = if (beltReleased) s.effectiveSpeedKmh else v.speedKmh,
            hasVehicleSignals = services.vehicleSignals.hasVehicleSignals,
        )
        val assessment = IncidentClassifier.assess(ctx)
        if (assessment.kind == IncidentKind.NONE) return

        _state.update { it.copy(incident = IncidentState.Checking(assessment.cause, assessment.kind)) }

        if (assessment.openHelpImmediately) {
            say(
                when (assessment.kind) {
                    IncidentKind.CRASH -> "사고가 감지되었습니다. 도움 화면을 엽니다."
                    else -> "주행 중 안전벨트가 풀렸습니다. 도움 화면을 엽니다."
                }
            )
            openHelp()
            return
        }

        incidentJob?.cancel()
        incidentJob = viewModelScope.launch {
            say("급정거가 감지되었습니다. 괜찮으세요?") { listenForIncidentReply() }
            delay(REPLY_TIMEOUT_MS)
            if (_state.value.incident is IncidentState.Checking) startSafeStop(assessment.cause, assessment.kind)
        }
    }

    private fun startSafeStop(cause: String, kind: IncidentKind) {
        val road = currentStopRoad()
        services.speechInput.stop()
        _state.update { it.copy(incident = IncidentState.SafeStop(cause, kind, SafeStopGuide.steps(road)), listening = false) }
        incidentJob?.cancel()
        incidentJob = viewModelScope.launch {
            say(SafeStopGuide.spoken(road)) { listenForIncidentReply() }
            val monitor = SafeStopMonitor(startedAtMs = System.currentTimeMillis())
            while (isActive && _state.value.incident is IncidentState.SafeStop) {
                delay(1000)
                if (monitor.tick(System.currentTimeMillis(), _state.value.effectiveSpeedKmh)) {
                    services.speechInput.stop()
                    openHelp()
                    break
                }
            }
        }
    }

    private fun currentStopRoad(): StopRoadType {
        val s = _state.value
        val route = s.route
        val inside = s.insideRealTunnel || route?.tunnelAt(s.progressM) != null
        return StopRoadType.of(inside, route?.roadClassAt(s.progressM))
    }

    private fun listenForIncidentReply() {
        services.speechInput.start(
            onResult = { text ->
                _state.update { it.copy(heard = text) }
                val stage = _state.value.incident
                if (stage is IncidentState.None) return@start
                if (CommandParser.isOkayReply(text)) dismissIncident() else openHelp()
            },
            onEnd = { _state.update { it.copy(listening = false) } },
            onError = { },
        )
        _state.update { it.copy(listening = true) }
    }

    fun simulateHardStop() = onHardStop(6.0)

    fun simulateBrakedHardStop() {
        updateVehicle { it.copy(brakePressed = true, lastBrakeAt = System.currentTimeMillis()) }
        onHardStop(6.0)
    }

    private fun onLocation(point: GeoPoint) {
        val p = LatLng(point.lat, point.lng)
        _state.update { it.copy(gps = point) }
        val s = _state.value
        if (s.running && s.simulated) return
        val route = s.route
        if (s.running && route != null) {
            val progress = DriveProgress.snapToRoute(route, p, s.progressM)
            if (progress != null) {
                _state.update { it.copy(progressM = progress) }
                announceManeuvers(route, progress)
                if (DriveProgress.isArrived(route, progress)) {
                    finishArrival()
                    return
                }
            }
        }
        trackTunnels(p)
    }

    private fun trackTunnels(p: LatLng) {
        refreshZones(p)
        val events = tunnelMonitor.update(p, _state.value.effectiveSpeedKmh)
        _state.update { it.copy(tunnelStatus = tunnelMonitor.status) }
        events.forEach { handleTunnelEvent(it) }
    }

    private fun refreshZones(p: LatLng) {
        val anchor = zonesAnchor
        if (anchor != null && distanceMeters(anchor, p) < 2_000) return
        zonesAnchor = p
        val zones = tunnelDb.entriesWithin(p, 5_000.0).take(10).map { (t, _) ->
            GeoZone("tunnel-${t.id}", t.entry.lat, t.entry.lng, 300f)
        }
        services.locationSource.watchZones(zones) { zoneId, entered ->
            val id = zoneId.removePrefix("tunnel-").toLongOrNull() ?: return@watchZones
            val t = tunnelDb.tunnels.firstOrNull { it.id == id } ?: return@watchZones
            if (entered && tunnelMonitor.status.inside == null) handleTunnelEvent(TunnelEvent.Approaching(t, 300))
        }
    }

    private fun handleTunnelEvent(event: TunnelEvent) {
        val s = _state.value
        when (event) {
            is TunnelEvent.Approaching -> {
                val onRoute = s.running && s.route != null
                announce(GuidanceSpeech.tunnelApproaching(event.tunnel, event.distanceM, s.avoidanceLevel, onRoute))
            }
            is TunnelEvent.Entered -> {
                announce(GuidanceSpeech.tunnelEntered(event.tunnel, s.effectiveSpeedKmh))
            }
            is TunnelEvent.Exited -> {
                startMusicGrace()
                recordChallenge(event.tunnel)
                val count = if (s.challengeMode) _state.value.challengeCountToday else null
                announce(GuidanceSpeech.tunnelExited(event.tunnel, count))
            }
        }
    }

    private fun recordChallenge(t: Tunnel) {
        if (!_state.value.challengeMode) return
        val r = loadChallenge().afterPassing(LocalDate.now().toString(), t.lengthM)
        prefs.edit().putString(KEY_CHALLENGE_DATE, r.date).putInt(KEY_CHALLENGE_COUNT, r.count).putInt(KEY_CHALLENGE_LONGEST, r.longestM).apply()
        _state.update { it.copy(challengeCountToday = r.count, challengeLongestM = r.longestM) }
    }

    private fun loadChallenge() = ChallengeRecord(
        date = prefs.getString(KEY_CHALLENGE_DATE, "").orEmpty(),
        count = prefs.getInt(KEY_CHALLENGE_COUNT, 0),
        longestM = prefs.getInt(KEY_CHALLENGE_LONGEST, 0),
    )

    fun simulateTunnelPass() {
        val s = _state.value
        if (s.running || s.tunnelDemoRunning) {
            message("안내 중에는 경로상 위치로 터널을 판정합니다.")
            return
        }
        if (!s.roadsReady) {
            message("도로망을 불러오는 중입니다.")
            return
        }
        val tunnel = RoutePlanner.demoTunnel(tunnelDb, DemoData.places) ?: run {
            message("터널 데이터가 없습니다.")
            return
        }
        val (origin, end) = RoutePlanner.tunnelPassEndpoints(tunnel, DEMO_BEFORE_M, DEMO_AFTER_M)
        val destination = Place("${tunnel.name} 통과 체험", end.lat, end.lng)
        tunnelMonitor.reset()
        _state.update { it.copy(tunnelDemoRunning = true) }
        computeRoutes(origin, destination, null, AvoidanceLevel.NONE) { (route) ->
            _state.update { it.copy(tunnelDemoRunning = false) }
            if (route == null || route.tunnelCount == 0) {
                message("${tunnel.name} 통과 경로를 만들지 못했습니다.")
                return@computeRoutes
            }
            applyRoute(origin, route, null, destination, null)
            message("${tunnel.name} 통과 시연: 터널 허용 경로로 주행합니다.")
            startDrive()
        }
    }

    fun toggleListening() {
        if (services.speechInput.isListening) {
            services.speechInput.stop()
            _state.update { it.copy(listening = false) }
            return
        }
        stopSpeech()
        _state.update { it.copy(speaking = false) }
        startListening(onError = { error -> message(error) })
    }

    private fun listenForAnswer() {
        if (services.speechInput.isListening) return
        startListening(onError = { })
    }

    private fun startListening(onError: (String) -> Unit) {
        _state.update { it.copy(listening = true, heard = "") }
        armListenTimeout(LISTEN_STARTUP_LIMIT_MS)
        services.speechInput.start(
            onReady = { armListenTimeout(LISTEN_TIMEOUT_MS) },
            onResult = { text ->
                viewModelScope.launch {
                    stopListening()
                    _state.update { it.copy(heard = text) }
                    handleCommand(text)
                }
            },
            onEnd = {
                listenTimeoutJob?.cancel()
                _state.update { it.copy(listening = false) }
            },
            onError = onError,
        )
    }

    private fun armListenTimeout(limitMs: Long) {
        listenTimeoutJob?.cancel()
        listenTimeoutJob = viewModelScope.launch {
            delay(limitMs)
            stopListening()
        }
    }

    private fun stopListening() {
        listenTimeoutJob?.cancel()
        services.speechInput.stop()
        _state.update { it.copy(listening = false) }
    }

    private fun answerPendingQuestion(raw: String): Boolean {
        val s = _state.value
        s.situation?.let { plan ->
            CommandParser.helpActionReply(raw, plan.actions)?.let {
                runHelpAction(it)
                return true
            }
        }
        if (s.tunnelAhead?.detour != null) {
            when (CommandParser.detourReply(raw)) {
                true -> { acceptTunnelDetour(); return true }
                false -> { dismissTunnelAhead(); say("지금 길로 계속 안내합니다."); return true }
                null -> Unit
            }
        }
        if (s.pendingRatingM != null && !s.running) {
            val reply = CommandParser.ratingReply(raw) ?: return false
            val distress = reply.distress
            if (distress != null) rateTunnel(distress) else skipTunnelRating()
            say(GuidanceSpeech.ratingSaved(reply))
            return true
        }
        return false
    }

    private fun chooseOtherRoute() {
        val s = _state.value
        val destination = s.destination
        val other = s.comparison
        if (s.running || destination == null || other == null) {
            say("지금은 고를 수 있는 다른 경로가 없습니다.")
            return
        }
        selectAlternativeRoute()
        say("${GuidanceSpeech.routeReady(destination.name, other)} ${GuidanceSpeech.START_HINT}") { listenForAnswer() }
    }

    private fun goToSpokenPlace(raw: String) {
        val query = CommandParser.destinationQuery(raw)
        val hit = query?.let { PlaceSearch.search(it, routeStart(), DemoData.places, pois, parkingLots).firstOrNull() }
        when {
            hit != null -> selectDestination(hit.place, byVoice = true)
            query != null -> say("${Korean.withObject(query)} 찾지 못했습니다. 지금은 대전 안의 장소만 찾을 수 있습니다.")
            else -> {
                message("명령을 확인하지 못했습니다. 예: 대전역으로 가 줘 / 안내 시작 / 음악 틀어 줘 / 도와줘")
                say(GuidanceSpeech.NOT_UNDERSTOOD)
            }
        }
    }

    private fun close() {
        if (_state.value.situation != null) closeSituation() else closeHelp()
    }

    fun handleCommand(raw: String) {
        if (!commandDebouncer.accept(raw, System.currentTimeMillis())) return
        dispatchCommand(raw)
    }

    fun runCommandButton(phrase: String) {
        stopListening()
        _state.update { it.copy(heard = phrase) }
        dispatchCommand(phrase)
    }

    private fun dispatchCommand(raw: String) {
        _state.update { if (it.navScreen == NavScreen.VOICE) it.copy(navScreen = NavScreen.MENU) else it }
        if (answerPendingQuestion(raw)) return

        val command = CommandParser.parse(raw)
        val allowedInHelp = command is Command.Help || command is Command.Situation ||
            command is Command.Call119 || command is Command.CallGuardian || command is Command.Close
        if (_state.value.helpOpen && !allowedInHelp) return

        when (command) {
            Command.Help -> openHelp()
            is Command.Situation -> openSituation(command.person)
            Command.EndDrive -> endDrive()
            Command.Pause -> if (_state.value.running && !_state.value.paused) togglePause()
            Command.StartDrive -> startDrive()
            is Command.SetAvoidance -> setAvoidanceLevel(command.level)
            Command.AddRestStop -> if (_state.value.restBeforeTunnel != null) addRestBeforeTunnel() else addNearestRest()
            Command.AddRestArea -> addNearestRest()
            Command.RemoveVia -> removeVia()
            Command.BreathingOn -> if (!_state.value.breathing) toggleBreathing()
            Command.BreathingOff -> stopBreathing()
            Command.MusicOn -> { requestMusic(); message("안심 음악을 켭니다.") }
            Command.MusicOff -> { stopMusic(); message("안심 음악을 끕니다.") }
            is Command.Destination -> selectDestination(DemoData.places[command.index], byVoice = true)
            Command.OtherRoute -> chooseOtherRoute()
            Command.Call119 -> call119()
            Command.CallGuardian -> callGuardian()
            Command.Close -> close()
            Command.Unknown -> goToSpokenPlace(raw)
        }
    }

    override fun onCleared() {
        driveJob?.cancel()
        breathJob?.cancel()
        incidentJob?.cancel()
        musicGraceJob?.cancel()
        musicFadeJob?.cancel()
        routingJob?.cancel()
        services.release()
        super.onCleared()
    }

    companion object {
        private const val KEY_GUARDIAN = "guardian_phone"
        private const val KEY_AVOIDANCE = "avoidance_level"
        private const val KEY_AUTO_MUSIC = "auto_music"
        private const val KEY_RATINGS = "tunnel_ratings"
        private const val KEY_RECENT = "recent_places"
        private const val MAX_RATINGS = 30
        private const val MAX_RECENT = 10
        private const val NEAREST_CANDIDATES = 3
        private const val PARKING_SUGGEST_M = 1_000.0
        private const val PARKING_NOTE = "빈자리는 현장에서 확인하세요. 공모전 SDK 에 실시간 주차 정보가 없습니다"
        private const val KEY_CHALLENGE = "challenge_mode"
        private const val KEY_CHALLENGE_DATE = "challenge_date"
        private const val KEY_CHALLENGE_COUNT = "challenge_count"
        private const val KEY_CHALLENGE_LONGEST = "challenge_longest"
        private const val STEERING_CHANGE_DEG = 15f
        private const val REPLY_TIMEOUT_MS = 12_000L
        private const val LISTEN_TIMEOUT_MS = 10_000L
        private const val LISTEN_STARTUP_LIMIT_MS = 60_000L
        private const val SPEECH_MS_PER_CHAR = 250L
        private const val SPEECH_MARGIN_MS = 8_000L
        private const val STATUS_DRIVING = "모의 주행 중 · 실제 도로 경로"
        private const val STATUS_GUIDING = "경로 안내 중"
        private const val DEMO_BEFORE_M = 1000.0
        private const val DEMO_AFTER_M = 600.0
    }
}
