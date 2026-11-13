package kr.tunnelfree.app.domain

object GuidanceSpeech {

    fun routeReady(placeName: String, route: RoadRoute): String =
        if (route.tunnelCount == 0) "${placeName}까지 터널 없는 경로입니다. ${spokenDistance(route.lengthM)}, 약 ${route.minutes}분 걸립니다."
        else "${placeName}까지 경로에 터널이 ${route.tunnelCount}개 있습니다. ${spokenDistance(route.lengthM)}, 약 ${route.minutes}분 걸립니다."

    fun driveStarted(route: RoadRoute): String =
        "안내를 시작합니다. ${spokenDistance(route.lengthM)}, 약 ${route.minutes}분 예상입니다."

    fun avoidanceLevel(level: AvoidanceLevel): String = when (level) {
        AvoidanceLevel.FULL -> "모든 터널을 피하는 경로로 안내합니다."
        AvoidanceLevel.SHORT_OK -> "300미터 이하의 짧은 터널은 허용합니다."
        AvoidanceLevel.MEDIUM_OK -> "1킬로미터 이하의 터널은 허용합니다."
        AvoidanceLevel.LONG_OK -> "3킬로미터 이하의 터널은 허용합니다."
        AvoidanceLevel.NONE -> "터널을 포함한 경로를 허용합니다."
    }

    fun tunnelApproaching(tunnel: Tunnel, distanceM: Int, level: AvoidanceLevel, onRoute: Boolean): String = when {
        onRoute ->
            "전방 ${distanceM}미터 ${tunnel.name}, 길이 ${tunnel.lengthM}미터, 약 ${tunnelPassSeconds(tunnel, null)}초 걸립니다. " +
                "$BODY_NOTE 천천히 호흡하세요."
        !level.shouldAvoid(tunnel.lengthM.toDouble()) && level.avoidsAny ->
            "전방 ${distanceM}미터에 ${tunnel.name}, 길이 ${tunnel.lengthM}미터입니다. 허용 범위 안의 짧은 구간이니 그대로 지나가도 괜찮습니다. 천천히 호흡하세요."
        level.avoidsAny ->
            "전방 ${distanceM}미터에 ${tunnel.name}이 있습니다. 길이 ${tunnel.lengthM}미터입니다. 회피 경로를 확인하세요."
        else ->
            "전방 ${distanceM}미터에 ${tunnel.name}이 있습니다. 길이 ${tunnel.lengthM}미터입니다. 천천히 호흡하세요."
    }

    fun tunnelPassSeconds(tunnel: Tunnel, speedKmh: Double?): Int {
        val v = speedKmh?.takeIf { it > 10 } ?: TunnelTiming.PLAN_SPEED_KMH
        return TunnelTiming.dwellSec(tunnel.lengthM.toDouble(), v).toInt().coerceAtLeast(5)
    }

    fun tunnelAhead(alert: TunnelAheadAlert, hasDetour: Boolean): String {
        val name = alert.tunnel.name.ifBlank { "터널" }
        val minutes = kotlin.math.ceil(alert.dwellSec / 60).toInt()
        val why = if (alert.congested) "앞이 막혀 ${name} 안에 약 ${minutes}분 머물 수 있습니다."
        else "조금 전 불안을 알려 주셔서 ${Korean.withObject(name)} 다시 확인했습니다."
        val next = if (hasDetour) "터널을 피하는 길이 있습니다. 우회할까요? 우회해 줘, 또는 그대로 가자라고 말씀해 주세요."
        else if (TunnelTier.ofLength(alert.tunnel.lengthM) == TunnelTier.LONG) "다른 길이 없습니다. 차로를 지키고 앞차를 따라가세요. 출구까지 거리를 알려 드립니다."
        else "다른 길이 없습니다. 차로를 지키고 앞차를 따라가세요."
        return "$why $next"
    }

    const val START_HINT = "출발하려면 안내 시작이라고 말씀해 주세요."
    const val HELP_HINT = "불안해요, 몸이 이상해요, 같이 탄 사람이 아파요, 차에 문제가 있어요 중에서 말씀해 주세요."
    const val RATING_QUESTION = "이번 터널은 어땠나요? 괜찮았어요, 긴장됐어요, 힘들었어요 중에서 말씀해 주세요."
    const val NOT_UNDERSTOOD = "잘 듣지 못했습니다. 다시 말씀해 주세요."

    fun helpChoices(actions: List<HelpAction>): String =
        if (actions.isEmpty()) "" else actions.joinToString(", ") { it.label } + " 중에서 말씀해 주세요."

    fun ratingSaved(reply: RatingReply): String = when (reply) {
        RatingReply.HARD -> "힘드셨군요. 다음 경로를 고를 때 반영하겠습니다. 도움이 필요하면 도와줘라고 말씀해 주세요."
        RatingReply.SKIP -> "알겠습니다. 이번 평가는 넘어갈게요."
        else -> "고맙습니다. 다음 경로를 고를 때 반영하겠습니다."
    }

    const val BODY_NOTE = "입구에서 심장이 빨라지는 건 흔한 반응입니다."

    fun tunnelEntered(tunnel: Tunnel, speedKmh: Double?): String =
        "${tunnel.name}에 진입했습니다. 약 ${tunnelPassSeconds(tunnel, speedKmh)}초면 지나갑니다. 호흡에 집중하세요."

    fun tunnelExited(tunnel: Tunnel, challengeCountToday: Int?): String {
        val suffix = if (challengeCountToday != null) " 오늘 ${challengeCountToday}번째 터널입니다." else ""
        return "${Korean.withObject(tunnel.name)} 빠져나왔습니다. 잘 하셨습니다.$suffix"
    }
}
