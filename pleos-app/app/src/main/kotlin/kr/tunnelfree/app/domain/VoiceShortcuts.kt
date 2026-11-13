package kr.tunnelfree.app.domain

data class VoiceShortcutGroup(val title: String, val phrases: List<String>)

object VoiceShortcuts {
    val groups: List<VoiceShortcutGroup> = listOf(
        VoiceShortcutGroup("도움", listOf("도와줘", "제가 불안해요", "제 몸이 이상해요", "같이 탄 사람이 아파요", "차에 문제가 있어요")),
        VoiceShortcutGroup("목적지", listOf("대전역으로 가 줘", "대전시청으로 가 줘", "한밭수목원으로 가 줘", "유성온천역으로 가 줘")),
        VoiceShortcutGroup("안내", listOf("안내 시작", "일시정지", "다시 출발", "안내 종료", "다른 길로 가 줘")),
        VoiceShortcutGroup("터널", listOf("터널 피해서 가 줘", "짧은 터널은 괜찮아", "중간 터널까지 괜찮아", "터널 허용")),
        VoiceShortcutGroup("쉬어 가기", listOf("휴게소 들렀다 가자", "쉼터로 가 줘", "경유 취소")),
        VoiceShortcutGroup("안심", listOf("호흡 안내 켜 줘", "호흡 안내 꺼 줘", "음악 틀어 줘", "음악 꺼 줘")),
    )
}
