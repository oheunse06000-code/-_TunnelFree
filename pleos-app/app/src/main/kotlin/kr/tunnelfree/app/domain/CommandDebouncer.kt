package kr.tunnelfree.app.domain

class CommandDebouncer(private val windowMs: Long = WINDOW_MS) {
    private var last = ""
    private var lastAt = 0L

    fun accept(raw: String, nowMs: Long): Boolean {
        val text = raw.replace(Regex("\\s+"), "")
        if (text.isEmpty()) return false
        if (text == last && nowMs - lastAt < windowMs) return false
        last = text
        lastAt = nowMs
        return true
    }

    companion object {
        const val WINDOW_MS = 2_000L
    }
}
