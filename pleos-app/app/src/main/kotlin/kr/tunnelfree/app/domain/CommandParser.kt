package kr.tunnelfree.app.domain

sealed class Command {
    data object Help : Command()
    data class Situation(val person: HelpPerson) : Command()
    data object EndDrive : Command()
    data object Pause : Command()
    data object StartDrive : Command()
    data class SetAvoidance(val level: AvoidanceLevel) : Command()
    data object AddRestStop : Command()
    data object AddRestArea : Command()
    data object RemoveVia : Command()
    data object BreathingOn : Command()
    data object BreathingOff : Command()
    data object MusicOn : Command()
    data object MusicOff : Command()
    data class Destination(val index: Int) : Command()
    data object OtherRoute : Command()
    data object Call119 : Command()
    data object CallGuardian : Command()
    data object Close : Command()
    data object Unknown : Command()
}

enum class RatingReply(val distress: Int?) { FINE(2), TENSE(5), HARD(8), SKIP(null) }

object CommandParser {
    private val helpPattern = Regex("안괜찮|괜찮지않|못견디|도와|도움|무서워|숨이막|숨쉬기힘")
    private val okayPattern = Regex("괜찮|네|응|예|문제없|좋아")
    private val passengerSickPattern = Regex("(동승자|옆사람|같이탄|아이|아내|남편|엄마|아빠|부모님).*(아파|아프|쓰러|의식|응급|병원)")
    private val driverSickPattern = Regex("가슴|흉통|어지러|쓰러질|의식|몸이이상|내가아파|아파요|병원")
    private val carTroublePattern = Regex("고장|타이어|펑크|시동|연기|경고등|차가이상|차에문제")
    private val anxiousPattern = Regex("불안|떨려|공황|힘들어")
    private val notOkayPattern = Regex("아니|아뇨|응급|사고|다쳤|아파|아프|119")
    private val call119Pattern = Regex("119|일일구|구급차")
    private val callGuardianPattern = Regex("보호자.*(전화|연락)")
    private val closePattern = Regex("닫아|닫기|괜찮아졌")
    private val otherRoutePattern = Regex("다른길|다른경로")
    private val startDrivePattern = Regex("다시출발|안내시작|주행시작|출발(해|하자|할게|$)")
    private val keepRoutePattern = Regex("그대로|아니|아뇨|됐어|계속가|지나갈|안돌아|안피")
    private val detourPattern = Regex("우회|돌아|피해|피할|바꿔|^네|^응(?!급)|^예|^그래|^좋아")
    private val ratingSkipPattern = Regex("건너|넘어가|나중에|안할래")
    private val ratingTensePattern = Regex("긴장|떨렸|조금무서|약간무서|그럭저럭")
    private val ratingHardPattern = Regex("힘들|안괜찮|괜찮지않|무서웠")
    private val ratingFinePattern = Regex("괜찮|좋았|편했|할만|문제없")
    private val destinationSuffix = Regex("(으로|로|까지|에)?(가줘|가자|가주세요|갈래|가고싶어|안내해줘|안내해주세요|찾아줘)$")

    private fun compact(raw: String) = raw.replace(Regex("\\s+"), "")

    fun parse(raw: String): Command {
        val text = compact(raw)
        if (text.isEmpty()) return Command.Unknown

        if (call119Pattern.containsMatchIn(text)) return Command.Call119
        if (callGuardianPattern.containsMatchIn(text)) return Command.CallGuardian
        if (passengerSickPattern.containsMatchIn(text)) return Command.Situation(HelpPerson.PASSENGER_SICK)
        if (driverSickPattern.containsMatchIn(text)) return Command.Situation(HelpPerson.DRIVER_SICK)
        if (carTroublePattern.containsMatchIn(text)) return Command.Situation(HelpPerson.CAR_TROUBLE)

        if (helpPattern.containsMatchIn(text)) return Command.Help
        if (anxiousPattern.containsMatchIn(text)) return Command.Situation(HelpPerson.DRIVER_ANXIOUS)
        if ("안내종료" in text || "주행종료" in text) return Command.EndDrive
        if ("일시정지" in text || "주행멈춰" in text) return Command.Pause
        if (startDrivePattern.containsMatchIn(text)) return Command.StartDrive
        if (closePattern.containsMatchIn(text)) return Command.Close

        if ("짧은터널" in text) return Command.SetAvoidance(AvoidanceLevel.SHORT_OK)
        if ("중간터널" in text || "1킬로" in text) return Command.SetAvoidance(AvoidanceLevel.MEDIUM_OK)
        if ("긴터널" in text || "3킬로" in text) return Command.SetAvoidance(AvoidanceLevel.LONG_OK)
        if ("터널피" in text || "터널회피" in text || "터널없는" in text) return Command.SetAvoidance(AvoidanceLevel.FULL)
        if ("터널허용" in text || "터널괜찮" in text) return Command.SetAvoidance(AvoidanceLevel.NONE)
        if (otherRoutePattern.containsMatchIn(text)) return Command.OtherRoute

        if ("쉼터" in text) return Command.AddRestStop
        if ("휴게소" in text || "쉬고싶" in text) return Command.AddRestArea
        if ("경유취소" in text || "경유삭제" in text) return Command.RemoveVia

        if ("호흡" in text) {
            return if (isTurnOff(text)) Command.BreathingOff else Command.BreathingOn
        }
        if ("음악" in text || "노래" in text) {
            return if (isTurnOff(text)) Command.MusicOff else Command.MusicOn
        }
        for (index in DemoData.destinationIndices) {
            val name = DemoData.places[index].name.replace(" ", "")
            if (name in text) return Command.Destination(index)
        }
        return Command.Unknown
    }

    private fun isTurnOff(text: String): Boolean {
        val t = negatedOffPattern.replace(text, "")
        return "꺼" in t || "끄" in t || "중지" in t || "멈춰" in t
    }

    private val negatedOffPattern = Regex("(끄|꺼|멈추|중지하)지(마|말)")

    fun detourReply(raw: String): Boolean? {
        val text = compact(raw)
        return when {
            keepRoutePattern.containsMatchIn(text) -> false
            detourPattern.containsMatchIn(text) -> true
            else -> null
        }
    }

    fun ratingReply(raw: String): RatingReply? {
        val text = compact(raw)
        return when {
            ratingSkipPattern.containsMatchIn(text) -> RatingReply.SKIP
            ratingTensePattern.containsMatchIn(text) -> RatingReply.TENSE
            ratingHardPattern.containsMatchIn(text) -> RatingReply.HARD
            ratingFinePattern.containsMatchIn(text) -> RatingReply.FINE
            else -> null
        }
    }

    fun helpActionReply(raw: String, available: List<HelpAction>): HelpAction? {
        val text = compact(raw)
        return available.firstOrNull { actionPattern(it).containsMatchIn(text) }
    }

    private fun actionPattern(action: HelpAction): Regex = when (action) {
        HelpAction.NEAREST_REST -> Regex("휴게소|쉼터|쉴곳|쉬고")
        HelpAction.AVOID_TUNNELS -> Regex("터널피|터널회피|남은터널")
        HelpAction.BREATHING -> Regex("호흡")
        HelpAction.NEAREST_HOSPITAL -> Regex("병원")
        HelpAction.CALL_119 -> call119Pattern
        HelpAction.CALL_ROADSIDE -> Regex("콜센터|도로공사|견인|긴급출동")
        HelpAction.CALL_GUARDIAN -> Regex("보호자")
    }

    fun destinationQuery(raw: String): String? {
        val text = compact(raw)
        val match = destinationSuffix.find(text) ?: return null
        return text.substring(0, match.range.first).takeIf { it.length >= 2 }
    }

    fun isOkayReply(raw: String): Boolean {
        val text = compact(raw)
        if (helpPattern.containsMatchIn(text)) return false
        if (notOkayPattern.containsMatchIn(text)) return false
        return okayPattern.containsMatchIn(text)
    }
}
