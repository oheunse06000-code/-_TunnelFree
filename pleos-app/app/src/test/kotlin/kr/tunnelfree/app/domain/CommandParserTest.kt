package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandParserTest {

    @Test
    fun `도움 화면 음성 명령`() {
        assertEquals(Command.Call119, CommandParser.parse("119 불러 줘"))
        assertEquals(Command.Call119, CommandParser.parse("일일구 전화"))
        assertEquals(Command.CallGuardian, CommandParser.parse("보호자한테 전화해 줘"))
        assertEquals(Command.Close, CommandParser.parse("화면 닫아 줘"))
        assertEquals(Command.Situation(HelpPerson.CAR_TROUBLE), CommandParser.parse("차에 문제가 있어요"))
    }

    @Test
    fun `출발과 다른 경로`() {
        assertEquals(Command.StartDrive, CommandParser.parse("출발하자"))
        assertEquals(Command.StartDrive, CommandParser.parse("출발"))
        assertEquals(Command.OtherRoute, CommandParser.parse("다른 길로 가 줘"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.FULL), CommandParser.parse("터널 피해서 다른 길로"))
    }

    @Test
    fun `터널 앞 우회 질문 대답`() {
        assertEquals(true, CommandParser.detourReply("우회해 줘"))
        assertEquals(true, CommandParser.detourReply("네"))
        assertEquals(true, CommandParser.detourReply("돌아가자"))
        assertEquals(false, CommandParser.detourReply("그대로 가자"))
        assertEquals(false, CommandParser.detourReply("아니요"))
        assertEquals(false, CommandParser.detourReply("안 돌아가도 돼"))
        assertEquals(null, CommandParser.detourReply("도와줘"))
        assertEquals(null, CommandParser.detourReply("무서워"))
    }

    @Test
    fun `터널 통과 평가 대답`() {
        assertEquals(RatingReply.FINE, CommandParser.ratingReply("괜찮았어요"))
        assertEquals(RatingReply.TENSE, CommandParser.ratingReply("긴장됐어요"))
        assertEquals(RatingReply.TENSE, CommandParser.ratingReply("조금 무서웠어"))
        assertEquals(RatingReply.HARD, CommandParser.ratingReply("힘들었어요"))
        assertEquals(RatingReply.HARD, CommandParser.ratingReply("안 괜찮았어"))
        assertEquals(RatingReply.SKIP, CommandParser.ratingReply("건너뛰어"))
        assertEquals(null, CommandParser.ratingReply("대전역으로 가 줘"))
    }

    @Test
    fun `도움 행동은 지금 고를 수 있는 것만 받는다`() {
        val actions = listOf(HelpAction.CALL_119, HelpAction.CALL_GUARDIAN)
        assertEquals(HelpAction.CALL_119, CommandParser.helpActionReply("119 전화", actions))
        assertEquals(HelpAction.CALL_GUARDIAN, CommandParser.helpActionReply("보호자 전화", actions))
        assertEquals(null, CommandParser.helpActionReply("휴게소로 가 줘", actions))
        assertEquals(HelpAction.NEAREST_REST, CommandParser.helpActionReply("휴게소로 가 줘", listOf(HelpAction.NEAREST_REST)))
    }

    @Test
    fun `가 줘 앞의 말을 목적지로 본다`() {
        assertEquals("충남대학교", CommandParser.destinationQuery("충남대학교로 가 줘"))
        assertEquals("중앙시장", CommandParser.destinationQuery("중앙시장까지 안내해 줘"))
        assertEquals("서대전역", CommandParser.destinationQuery("서대전역으로 가자"))
        assertEquals(null, CommandParser.destinationQuery("오늘 날씨 어때"))
        assertEquals(null, CommandParser.destinationQuery("가 줘"))
    }

    @Test
    fun `도움 요청이 다른 명령보다 우선한다`() {
        assertEquals(Command.Help, CommandParser.parse("무서워 안내 종료"))
        assertEquals(Command.Help, CommandParser.parse("숨이 막혀"))
        assertEquals(Command.Help, CommandParser.parse("안 괜찮아"))
        assertEquals(Command.Help, CommandParser.parse("도와줘"))
    }

    @Test
    fun `주행 제어 명령`() {
        assertEquals(Command.EndDrive, CommandParser.parse("안내 종료"))
        assertEquals(Command.Pause, CommandParser.parse("일시 정지"))
        assertEquals(Command.StartDrive, CommandParser.parse("다시 출발"))
        assertEquals(Command.StartDrive, CommandParser.parse("안내 시작"))
    }

    @Test
    fun `회피 강도 명령`() {
        assertEquals(Command.SetAvoidance(AvoidanceLevel.FULL), CommandParser.parse("터널 피해서 가 줘"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.FULL), CommandParser.parse("터널 없는 길로"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.NONE), CommandParser.parse("터널 허용"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.SHORT_OK), CommandParser.parse("짧은 터널은 괜찮아"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.SHORT_OK), CommandParser.parse("짧은 터널 허용"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.MEDIUM_OK), CommandParser.parse("중간 터널까지 허용"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.LONG_OK), CommandParser.parse("긴 터널까지 허용"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.LONG_OK), CommandParser.parse("3 킬로 터널까지"))
    }

    @Test
    fun `경유지 명령 - 화장실은 범위 제외`() {
        assertEquals(Command.AddRestStop, CommandParser.parse("졸음쉼터 들러줘"))
        assertEquals(Command.AddRestArea, CommandParser.parse("휴게소 들러줘"))
        assertEquals(Command.AddRestArea, CommandParser.parse("쉬고 싶어"))
        assertEquals(Command.RemoveVia, CommandParser.parse("경유 취소"))
        assertEquals(Command.Unknown, CommandParser.parse("화장실 가고 싶어"))
    }

    @Test
    fun `호흡 안내 켜기 끄기`() {
        assertEquals(Command.BreathingOn, CommandParser.parse("호흡 안내"))
        assertEquals(Command.BreathingOff, CommandParser.parse("호흡 꺼"))
        assertEquals(Command.BreathingOff, CommandParser.parse("호흡 중지"))
    }

    @Test
    fun `음악 켜기 끄기`() {
        assertEquals(Command.MusicOn, CommandParser.parse("음악 틀어 줘"))
        assertEquals(Command.MusicOn, CommandParser.parse("노래 틀어"))
        assertEquals(Command.MusicOff, CommandParser.parse("음악 꺼"))
        assertEquals(Command.MusicOff, CommandParser.parse("음악 멈춰"))
    }

    @Test
    fun `목적지 이름 인식`() {
        assertEquals(Command.Destination(5), CommandParser.parse("대전역으로 가 줘"))
        assertEquals(Command.Destination(4), CommandParser.parse("대전시청"))
        assertEquals(Command.Destination(6), CommandParser.parse("한밭수목원 가자"))
        assertEquals(Command.Destination(7), CommandParser.parse("유성온천역"))
    }

    @Test
    fun `알 수 없는 명령과 빈 문자열`() {
        assertEquals(Command.Unknown, CommandParser.parse("오늘 날씨 어때"))
        assertEquals(Command.Unknown, CommandParser.parse("   "))
    }

    @Test
    fun `급정거 후 응답 판정`() {
        assertTrue(CommandParser.isOkayReply("네 괜찮아요"))
        assertTrue(CommandParser.isOkayReply("응 문제 없어"))
        assertFalse(CommandParser.isOkayReply("안 괜찮아"))
        assertFalse(CommandParser.isOkayReply("도와줘"))
        assertFalse(CommandParser.isOkayReply("무서워"))
        assertFalse(CommandParser.isOkayReply(""))
    }

    @Test
    fun `빈 입력과 공백·탭·줄바꿈만 있는 입력은 Unknown`() {
        assertEquals(Command.Unknown, CommandParser.parse(""))
        assertEquals(Command.Unknown, CommandParser.parse("\t\n  \r\n"))
    }

    @Test
    fun `공백 위치와 종류는 무시한다`() {
        assertEquals(Command.EndDrive, CommandParser.parse("\t안\n내  종 료 "))
        assertEquals(Command.Destination(5), CommandParser.parse("대 전 역"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.MEDIUM_OK), CommandParser.parse("1 킬로 터널까지"))
    }

    @Test
    fun `모든 명령 분기를 한 번씩`() {
        assertEquals(Command.Help, CommandParser.parse("못 견디겠어"))
        assertEquals(Command.Help, CommandParser.parse("괜찮지 않아"))
        assertEquals(Command.Help, CommandParser.parse("숨쉬기 힘들어"))
        assertEquals(Command.Help, CommandParser.parse("도움이 필요해"))
        assertEquals(Command.EndDrive, CommandParser.parse("주행 종료"))
        assertEquals(Command.Pause, CommandParser.parse("주행 멈춰"))
        assertEquals(Command.StartDrive, CommandParser.parse("주행 시작"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.FULL), CommandParser.parse("터널 회피"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.NONE), CommandParser.parse("터널 괜찮아"))
        assertEquals(Command.RemoveVia, CommandParser.parse("경유 삭제"))
        assertEquals(Command.BreathingOff, CommandParser.parse("호흡 멈춰"))
        assertEquals(Command.MusicOff, CommandParser.parse("음악 중지"))
        assertEquals(Command.MusicOff, CommandParser.parse("노래 끄기"))
        assertEquals(Command.Destination(4), CommandParser.parse("대전시청 가자"))
    }

    @Test
    fun `여러 명령이 섞이면 검사 순서상 앞선 것이 이긴다`() {
        assertEquals(Command.EndDrive, CommandParser.parse("안내 종료하고 음악 꺼"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.FULL), CommandParser.parse("터널 피해서 경유 취소"))
        assertEquals(Command.AddRestStop, CommandParser.parse("졸음쉼터 말고 휴게소"))
        assertEquals(Command.AddRestArea, CommandParser.parse("대전역 가기 전에 휴게소"))
        assertEquals(Command.BreathingOff, CommandParser.parse("호흡 꺼 음악 틀어"))
        assertEquals(Command.MusicOn, CommandParser.parse("대전역 가면서 노래 틀어"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.SHORT_OK), CommandParser.parse("짧은 터널 괜찮아"))
    }

    @Test
    fun `모호한 표현`() {
        assertEquals(Command.Help, CommandParser.parse("터널 안 괜찮아"))
        assertEquals(Command.Unknown, CommandParser.parse("괜찮아"))
        assertEquals(Command.BreathingOn, CommandParser.parse("호흡"))
        assertEquals(Command.MusicOn, CommandParser.parse("음악"))
        assertEquals(Command.Destination(4), CommandParser.parse("대전시청역"))
        assertEquals(Command.Unknown, CommandParser.parse("건양대학교 대전캠퍼스"))
    }

    @Test
    fun `호흡 끄기도 음악처럼 끄 를 끄기로 본다`() {
        assertEquals(Command.BreathingOff, CommandParser.parse("호흡 안내 끄기"))
        assertEquals(Command.BreathingOff, CommandParser.parse("호흡 안내 꺼 줘"))
        assertEquals(Command.BreathingOn, CommandParser.parse("호흡 안내 켜 줘"))
        assertEquals(Command.MusicOff, CommandParser.parse("음악 끄기"))
    }

    @Test
    fun `부정형 끄지 마 는 켜 두라는 뜻`() {
        assertEquals(Command.MusicOn, CommandParser.parse("음악 끄지 마"))
        assertEquals(Command.MusicOn, CommandParser.parse("노래 멈추지 말아 줘"))
        assertEquals(Command.BreathingOn, CommandParser.parse("호흡 안내 끄지 마"))
        assertEquals(Command.MusicOff, CommandParser.parse("음악 꺼 줘"))
        assertEquals(Command.MusicOff, CommandParser.parse("노래 중지"))
        assertEquals(Command.BreathingOff, CommandParser.parse("호흡 안내 멈춰"))
    }

    @Test
    fun `응답 판정 - 긍정`() {
        assertTrue(CommandParser.isOkayReply("네"))
        assertTrue(CommandParser.isOkayReply("예"))
        assertTrue(CommandParser.isOkayReply("응"))
        assertTrue(CommandParser.isOkayReply("좋아요"))
        assertTrue(CommandParser.isOkayReply("괜찮아"))
        assertTrue(CommandParser.isOkayReply("  문제 없습니다  "))
    }

    @Test
    fun `응답 판정 - 부정과 무응답`() {
        assertFalse(CommandParser.isOkayReply("   "))
        assertFalse(CommandParser.isOkayReply("아니"))
        assertFalse(CommandParser.isOkayReply("싫어"))
        assertFalse(CommandParser.isOkayReply("괜찮지 않아"))
        assertFalse(CommandParser.isOkayReply("못 견디겠어"))
        assertFalse(CommandParser.isOkayReply("숨이 막혀"))
        assertFalse(CommandParser.isOkayReply("네 도와주세요"))
        assertFalse(CommandParser.isOkayReply("응 무서워"))
    }

    @Test
    fun `한 글자 긍정어가 부정·응급 단어 안에 있어도 괜찮음으로 보지 않는다`() {
        assertFalse(CommandParser.isOkayReply("응급 상황이야"))
        assertFalse(CommandParser.isOkayReply("아니예요"))
        assertFalse(CommandParser.isOkayReply("아뇨"))
        assertFalse(CommandParser.isOkayReply("사고 났어요"))
        assertFalse(CommandParser.isOkayReply("119 불러 줘"))
        assertTrue(CommandParser.isOkayReply("네 괜찮아요"))
        assertTrue(CommandParser.isOkayReply("응"))
        assertTrue(CommandParser.isOkayReply("예"))
    }
}
