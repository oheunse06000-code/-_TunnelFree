package kr.tunnelfree.app.domain

data class ChallengeRecord(val date: String, val count: Int, val longestM: Int) {

    fun countOn(today: String): Int = if (date == today) count else 0

    fun afterPassing(today: String, lengthM: Int): ChallengeRecord =
        ChallengeRecord(today, countOn(today) + 1, maxOf(longestM, lengthM))
}
