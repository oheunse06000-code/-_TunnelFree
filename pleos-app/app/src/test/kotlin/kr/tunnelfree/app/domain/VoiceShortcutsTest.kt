package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceShortcutsTest {

    private val allPhrases = VoiceShortcuts.groups.flatMap { it.phrases }

    @Test
    fun everyButtonPhraseIsUnderstoodByTheParser() {
        for (phrase in allPhrases) {
            assertNotEquals(phrase, Command.Unknown, CommandParser.parse(phrase))
        }
    }

    @Test
    fun buttonsReachTheIntendedCommands() {
        assertEquals(Command.Help, CommandParser.parse("도와줘"))
        assertEquals(Command.Situation(HelpPerson.DRIVER_ANXIOUS), CommandParser.parse("제가 불안해요"))
        assertEquals(Command.Situation(HelpPerson.DRIVER_SICK), CommandParser.parse("제 몸이 이상해요"))
        assertEquals(Command.Situation(HelpPerson.PASSENGER_SICK), CommandParser.parse("같이 탄 사람이 아파요"))
        assertEquals(Command.Situation(HelpPerson.CAR_TROUBLE), CommandParser.parse("차에 문제가 있어요"))
        assertEquals(Command.StartDrive, CommandParser.parse("안내 시작"))
        assertEquals(Command.StartDrive, CommandParser.parse("다시 출발"))
        assertEquals(Command.Pause, CommandParser.parse("일시정지"))
        assertEquals(Command.EndDrive, CommandParser.parse("안내 종료"))
        assertEquals(Command.OtherRoute, CommandParser.parse("다른 길로 가 줘"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.FULL), CommandParser.parse("터널 피해서 가 줘"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.SHORT_OK), CommandParser.parse("짧은 터널은 괜찮아"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.MEDIUM_OK), CommandParser.parse("중간 터널까지 괜찮아"))
        assertEquals(Command.SetAvoidance(AvoidanceLevel.NONE), CommandParser.parse("터널 허용"))
        assertEquals(Command.AddRestArea, CommandParser.parse("휴게소 들렀다 가자"))
        assertEquals(Command.AddRestStop, CommandParser.parse("쉼터로 가 줘"))
        assertEquals(Command.RemoveVia, CommandParser.parse("경유 취소"))
        assertEquals(Command.BreathingOn, CommandParser.parse("호흡 안내 켜 줘"))
        assertEquals(Command.BreathingOff, CommandParser.parse("호흡 안내 꺼 줘"))
        assertEquals(Command.MusicOn, CommandParser.parse("음악 틀어 줘"))
        assertEquals(Command.MusicOff, CommandParser.parse("음악 꺼 줘"))
    }

    @Test
    fun destinationButtonsCoverEveryDemoDestination() {
        val reached = VoiceShortcuts.groups.first { it.title == "목적지" }.phrases
            .map { CommandParser.parse(it) }
        for (index in DemoData.destinationIndices) {
            assertTrue(Command.Destination(index) in reached)
        }
    }
}
