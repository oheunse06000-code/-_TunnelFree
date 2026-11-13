package kr.tunnelfree.app.domain

object Korean {

    fun withObject(word: String): String = word + if (hasFinalConsonant(word)) "을" else "를"

    fun hasFinalConsonant(word: String): Boolean {
        val c = word.trimEnd().lastOrNull { it.isLetterOrDigit() } ?: return true
        return when {
            c in '가'..'힣' -> (c - '가') % 28 != 0
            c.isDigit() -> c !in "2459"
            else -> true
        }
    }
}
