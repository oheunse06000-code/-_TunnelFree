package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SituationPlannerTest {

    @Test
    fun `터널 안 불안은 출구 거리를 말하고 멈추라고 하지 않는다`() {
        val p = SituationPlanner.plan(HelpPerson.DRIVER_ANXIOUS, StopRoadType.TUNNEL, tunnelExitM = 400.0)
        assertTrue(p.spoken.contains("400미터"))
        assertTrue(p.steps.first().contains("400 m"))
        assertEquals(listOf(HelpAction.BREATHING), p.actions)
    }

    @Test
    fun `터널 밖 불안은 휴게소가 먼저이고 남은 터널이 있을 때만 회피를 준다`() {
        val ahead = SituationPlanner.plan(HelpPerson.DRIVER_ANXIOUS, StopRoadType.SURFACE, tunnelsAhead = true)
        assertEquals(HelpAction.NEAREST_REST, ahead.actions.first())
        assertTrue(HelpAction.AVOID_TUNNELS in ahead.actions)
        val none = SituationPlanner.plan(HelpPerson.DRIVER_ANXIOUS, StopRoadType.SURFACE, tunnelsAhead = false)
        assertFalse(HelpAction.AVOID_TUNNELS in none.actions)
    }

    @Test
    fun `운전자 몸 이상은 병원 경로를 주지 않고 119 가 먼저다`() {
        val p = SituationPlanner.plan(HelpPerson.DRIVER_SICK, StopRoadType.EXPRESSWAY)
        assertEquals(HelpAction.CALL_119, p.actions.first())
        assertFalse(HelpAction.NEAREST_HOSPITAL in p.actions)
        assertTrue(p.spoken.contains("직접 운전하지"))
        assertTrue(p.spoken.contains("휴게소"))
    }

    @Test
    fun `동승자 응급은 119 다음에 병원이고 신호 준수를 유지한다`() {
        val p = SituationPlanner.plan(HelpPerson.PASSENGER_SICK, StopRoadType.SURFACE)
        assertEquals(listOf(HelpAction.CALL_119, HelpAction.NEAREST_HOSPITAL), p.actions)
        assertTrue(p.spoken.contains("신호를 지키며"))
        assertTrue(p.steps.any { it.contains("전조등") })
    }

    @Test
    fun `고속 주행 도로 고장만 콜센터가 먼저다`() {
        val fast = SituationPlanner.plan(HelpPerson.CAR_TROUBLE, StopRoadType.EXPRESSWAY)
        assertEquals(HelpAction.CALL_ROADSIDE, fast.actions.first())
        val city = SituationPlanner.plan(HelpPerson.CAR_TROUBLE, StopRoadType.SURFACE)
        assertFalse(HelpAction.CALL_ROADSIDE in city.actions)
    }

    @Test
    fun `보호자가 없으면 보호자 전화를 숨긴다`() {
        for (person in HelpPerson.entries) {
            assertFalse(HelpAction.CALL_GUARDIAN in SituationPlanner.plan(person, StopRoadType.SURFACE).actions)
        }
        assertTrue(HelpAction.CALL_GUARDIAN in SituationPlanner.plan(HelpPerson.DRIVER_SICK, StopRoadType.SURFACE, hasGuardian = true).actions)
    }

    @Test
    fun `음성은 기능 이름이 아니라 사람의 말로 고른다`() {
        assertEquals(Command.Situation(HelpPerson.DRIVER_SICK), CommandParser.parse("가슴이 아파요"))
        assertEquals(Command.Situation(HelpPerson.DRIVER_SICK), CommandParser.parse("병원 가야 해"))
        assertEquals(Command.Situation(HelpPerson.PASSENGER_SICK), CommandParser.parse("옆 사람이 쓰러졌어"))
        assertEquals(Command.Situation(HelpPerson.PASSENGER_SICK), CommandParser.parse("동승자가 아파"))
        assertEquals(Command.Situation(HelpPerson.CAR_TROUBLE), CommandParser.parse("타이어 펑크 났어"))
        assertEquals(Command.Situation(HelpPerson.DRIVER_ANXIOUS), CommandParser.parse("너무 불안해"))
        assertEquals(Command.Help, CommandParser.parse("도와줘"))
    }
}
