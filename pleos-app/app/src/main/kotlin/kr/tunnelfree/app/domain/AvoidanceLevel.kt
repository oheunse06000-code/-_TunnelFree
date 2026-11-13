package kr.tunnelfree.app.domain

enum class AvoidanceLevel(val maxTunnelLengthM: Double, val label: String, val description: String) {
    FULL(0.0, "모든 터널 회피", "길이와 관계없이 모든 터널·지하차도를 피합니다"),
    SHORT_OK(300.0, "300 m 이하 허용", "약 18초 안에 지나는 짧은 지하차도 정도는 지나갑니다"),
    MEDIUM_OK(1000.0, "1 km 이하 허용", "약 1분 안에 지나는 터널까지 지나갑니다"),
    LONG_OK(3000.0, "3 km 이하 허용", "약 3분 안에 지나는 터널까지 지나갑니다"),
    NONE(Double.MAX_VALUE, "회피 안 함", "최단 경로로 안내합니다");

    val avoidsAny: Boolean get() = this != NONE

    fun shouldAvoid(lengthM: Double): Boolean = lengthM > maxTunnelLengthM

    companion object {
        val DEFAULT = FULL
        fun fromName(name: String?): AvoidanceLevel = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
