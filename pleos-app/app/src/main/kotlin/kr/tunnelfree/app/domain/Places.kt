package kr.tunnelfree.app.domain

data class Place(val name: String, val lat: Double, val lng: Double) {
    val latLng: LatLng get() = LatLng(lat, lng)
}

object DemoData {
    val places: List<Place> = listOf(
        Place("건양대학교 대전캠퍼스", 36.3061, 127.3446),
        Place("가수원 방면", 36.3100, 127.3600),
        Place("서쪽 우회", 36.3390, 127.3490),
        Place("둔산 방면", 36.3480, 127.3820),
        Place("대전시청", 36.3504, 127.3845),
        Place("대전역", 36.3320, 127.4342),
        Place("한밭수목원", 36.3690, 127.3880),
        Place("유성온천역", 36.3540, 127.3415),
        Place("졸음쉼터 (시연)", 36.3410, 127.3510),
        Place("휴게시설 (시연)", 36.3530, 127.3780),
    )

    val destinationIndices: List<Int> = listOf(4, 5, 6, 7)
    const val START_NODE = 0
    const val REST_STOP_NODE = 8
    const val REST_AREA_NODE = 9

    val START: LatLng get() = places[START_NODE].latLng
}
