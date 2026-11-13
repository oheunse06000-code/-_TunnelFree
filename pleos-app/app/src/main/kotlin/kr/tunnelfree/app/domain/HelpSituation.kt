package kr.tunnelfree.app.domain

enum class HelpPerson(val label: String, val hint: String) {
    DRIVER_ANXIOUS("제가 불안해요", "운전은 할 수 있어요"),
    DRIVER_SICK("제 몸이 이상해요", "가슴·호흡·어지럼"),
    PASSENGER_SICK("같이 탄 사람이 아파요", "응급 환자가 있어요"),
    CAR_TROUBLE("차에 문제가 있어요", "고장·경고등·타이어"),
}

enum class HelpAction(val label: String) {
    NEAREST_REST("가까운 휴게소로"),
    AVOID_TUNNELS("남은 터널 피하기"),
    BREATHING("호흡 안내"),
    NEAREST_HOSPITAL("가까운 병원 찾기"),
    CALL_119("119 전화"),
    CALL_ROADSIDE("고속도로 콜센터"),
    CALL_GUARDIAN("보호자 전화"),
}

data class SituationPlan(
    val person: HelpPerson,
    val title: String,
    val spoken: String,
    val steps: List<String>,
    val actions: List<HelpAction>,
)

object SituationPlanner {

    fun plan(
        person: HelpPerson,
        road: StopRoadType,
        tunnelExitM: Double? = null,
        tunnelsAhead: Boolean = false,
        hasGuardian: Boolean = false,
    ): SituationPlan {
        val plan = when (person) {
            HelpPerson.DRIVER_ANXIOUS -> anxious(road, tunnelExitM, tunnelsAhead)
            HelpPerson.DRIVER_SICK -> SituationPlan(
                person,
                "운전을 멈추는 것이 먼저입니다",
                "몸에 이상이 있으면 병원까지 직접 운전하지 마세요. ${SafeStopGuide.instruction(road)} 세운 뒤 119에 연락하세요.",
                SafeStopGuide.steps(road),
                listOf(HelpAction.CALL_119, HelpAction.CALL_GUARDIAN),
            )
            HelpPerson.PASSENGER_SICK -> SituationPlan(
                person,
                "119에 먼저 연락하세요",
                "119에 먼저 연락하고 안내를 따르세요. 직접 이송해야 하면 비상등을 켜고, 신호를 지키며 이동하세요.",
                listOf(
                    "119에 환자 상태와 위치를 알리세요",
                    "구급대 안내에 따라 기다리거나 이동하세요",
                    "직접 이송하면 전조등이나 비상등으로 표시하세요",
                    "신호와 중앙선을 지키는 것이 가장 안전합니다",
                    "응급실 수용 여부는 전화로 확인하세요",
                ),
                listOf(HelpAction.CALL_119, HelpAction.NEAREST_HOSPITAL, HelpAction.CALL_GUARDIAN),
            )
            HelpPerson.CAR_TROUBLE -> SituationPlan(
                person,
                "차를 안전한 곳으로 옮기세요",
                SafeStopGuide.instruction(road),
                SafeStopGuide.steps(road),
                if (road == StopRoadType.EXPRESSWAY) {
                    listOf(HelpAction.CALL_ROADSIDE, HelpAction.CALL_119, HelpAction.CALL_GUARDIAN)
                } else {
                    listOf(HelpAction.CALL_119, HelpAction.CALL_GUARDIAN)
                },
            )
        }
        return if (hasGuardian) plan else plan.copy(actions = plan.actions - HelpAction.CALL_GUARDIAN)
    }

    private fun anxious(road: StopRoadType, tunnelExitM: Double?, tunnelsAhead: Boolean): SituationPlan {
        if (road == StopRoadType.TUNNEL) {
            val distance = tunnelExitM?.let { spokenDistance(it) }
            return SituationPlan(
                HelpPerson.DRIVER_ANXIOUS,
                "곧 터널을 벗어납니다",
                (if (distance != null) "출구까지 $distance 남았습니다. " else "") +
                    "지금 속도를 유지하고 천천히 숨을 내쉬세요. 터널을 벗어나면 쉴 곳을 찾겠습니다.",
                listOfNotNull(
                    tunnelExitM?.let { "출구까지 ${formatDistance(it)}" },
                    "지금 차로와 속도를 유지하세요",
                    "숨을 천천히 길게 내쉬세요",
                    "터널 안에서는 멈추지 말고 출구까지 가세요",
                ),
                listOf(HelpAction.BREATHING, HelpAction.CALL_GUARDIAN),
            )
        }
        return SituationPlan(
            HelpPerson.DRIVER_ANXIOUS,
            "잠시 쉬어 가도 됩니다",
            "괜찮습니다. 가까운 휴게소로 안내하거나 남은 터널을 피할 수 있습니다.",
            listOf("서두르지 않아도 됩니다", "급제동·급가속은 하지 마세요", "\"휴게소로 가 줘\"라고 말해도 됩니다"),
            buildList {
                add(HelpAction.NEAREST_REST)
                if (tunnelsAhead) add(HelpAction.AVOID_TUNNELS)
                add(HelpAction.BREATHING)
                add(HelpAction.CALL_GUARDIAN)
            },
        )
    }
}
