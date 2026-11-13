package kr.tunnelfree.app.domain

enum class StopRoadType {
    TUNNEL, EXPRESSWAY, SURFACE, UNKNOWN;

    companion object {
        fun of(insideTunnel: Boolean, roadClass: RoadClass?): StopRoadType = when {
            insideTunnel -> TUNNEL
            roadClass == null -> UNKNOWN
            roadClass == RoadClass.MOTORWAY || roadClass == RoadClass.MOTORWAY_LINK ||
                roadClass == RoadClass.TRUNK || roadClass == RoadClass.TRUNK_LINK -> EXPRESSWAY
            else -> SURFACE
        }
    }
}

object SafeStopGuide {
    const val RESPONSE_TIMEOUT_MS = 20_000L
    const val STOPPED_CONFIRM_MS = 5_000L

    const val EXPRESSWAY_CALL_CENTER = "1588-2504"

    private const val INTRO = "응답이 없어 안전 정차를 안내합니다."
    private const val HELP_STEP = "정차 후 도움 요청 버튼을 누르거나 \"도와줘\"라고 말하세요"

    fun spoken(road: StopRoadType): String = "$INTRO ${instruction(road)}"

    fun instruction(road: StopRoadType): String = when (road) {
        StopRoadType.TUNNEL ->
            "터널 안입니다. 전조등을 켜고 차로를 유지한 채 출구까지 이동하세요. 더 갈 수 없으면 비상등을 켜고 비상 주차대에 세우세요."
        StopRoadType.EXPRESSWAY ->
            "고속 주행 도로입니다. 급제동 없이 다음 휴게소나 졸음쉼터, 출구까지 이동하세요. 더 갈 수 없으면 비상등을 켜고 서서히 줄여 갓길 끝에 세운 뒤 가드레일 밖으로 대피하세요."
        StopRoadType.SURFACE ->
            "비상등을 켜고 교차로와 횡단보도를 피해 길가 안전한 곳에 천천히 세우세요. 급제동은 하지 마세요."
        StopRoadType.UNKNOWN ->
            "비상등을 켜고 안전한 곳까지 천천히 이동해 세우세요. 급제동은 하지 마세요."
    }

    fun steps(road: StopRoadType): List<String> = when (road) {
        StopRoadType.TUNNEL -> listOf(
            "전조등을 켜고 차로를 유지하세요",
            "출구까지 이동하세요",
            "더 갈 수 없으면 비상등을 켜고 비상 주차대에 세우세요",
            "급제동·급가속은 하지 마세요",
            HELP_STEP,
        )
        StopRoadType.EXPRESSWAY -> listOf(
            "다음 휴게소·졸음쉼터·출구까지 이동하세요",
            "더 갈 수 없으면 비상등을 켜고 서서히 줄여 갓길 끝에 세우세요",
            "급제동·급가속은 하지 마세요",
            "정차하면 트렁크를 열어 뒤차에 알리세요",
            "그다음 가드레일 밖으로 대피해 119 또는 $EXPRESSWAY_CALL_CENTER 에 연락하세요",
            HELP_STEP,
        )
        StopRoadType.SURFACE -> listOf(
            "비상등을 켜세요",
            "교차로·횡단보도를 피해 길가 안전한 곳에 세우세요",
            "급제동·급가속은 하지 마세요",
            HELP_STEP,
        )
        StopRoadType.UNKNOWN -> listOf(
            "비상등을 켜세요",
            "안전한 곳까지 천천히 이동해 세우세요",
            "급제동·급가속은 하지 마세요",
            HELP_STEP,
        )
    }
}
