package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelDatabaseTest {

    private val good = "1;정상;primary;600;36.350000,127.400000|36.350000,127.406700"

    @Test
    fun `빈 문자열·주석·공백 줄만 있으면 빈 DB`() {
        assertTrue(TunnelDatabase.parse("").tunnels.isEmpty())
        assertTrue(TunnelDatabase.parse("# a\n\n   \n\t\n   # 들여쓴 주석\n").tunnels.isEmpty())
    }

    @Test
    fun `줄 앞뒤 공백과 CRLF 줄바꿈은 무시`() {
        val db = TunnelDatabase.parse("   $good   \r\n# c\r\n\r\n")
        assertEquals(1, db.tunnels.size)
        assertEquals(127.4067, db.tunnels[0].exit.lng, 1e-9)
    }

    @Test
    fun `EMPTY 는 비어 있고 조회는 모두 빈 결과`() {
        val db = TunnelDatabase.EMPTY
        assertTrue(db.tunnels.isEmpty())
        assertNull(db.nearest(LatLng(36.35, 127.40)))
        assertTrue(db.entriesWithin(LatLng(36.35, 127.40), 1e9).isEmpty())
        assertTrue(db.within(-90.0, 90.0, -180.0, 180.0).isEmpty())
    }

    @Test
    fun `필드가 5개 미만이면 건너뜀`() {
        val db = TunnelDatabase.parse("1;이름;primary;600\n2;이름;primary\n$good")
        assertEquals(listOf(1L), db.tunnels.map { it.id })
    }

    @Test
    fun `필드가 5개를 넘으면 앞 5개만 쓴다`() {
        val db = TunnelDatabase.parse("$good;여분;필드")
        assertEquals(1, db.tunnels.size)
        assertEquals(2, db.tunnels[0].points.size)
    }

    @Test
    fun `id 가 숫자가 아니면 건너뜀`() {
        val db = TunnelDatabase.parse("abc;이름;primary;600;36.35,127.40|36.35,127.41\n$good")
        assertEquals(listOf(1L), db.tunnels.map { it.id })
    }

    @Test
    fun `현재 동작 - 필드 안 공백은 다듬지 않아 id 앞뒤 공백이면 줄 전체를 버린다`() {
        val db = TunnelDatabase.parse("7 ;이름;primary;600;36.35,127.40|36.35,127.41")
        assertTrue(db.tunnels.isEmpty())
    }

    @Test
    fun `현재 동작 - 이름·도로 등급의 앞뒤 공백은 그대로 남는다`() {
        val db = TunnelDatabase.parse("7; 이름 ; primary ;600;36.35,127.40|36.35,127.41")
        assertEquals(" 이름 ", db.tunnels[0].name)
        assertEquals(" primary ", db.tunnels[0].highway)
    }

    @Test
    fun `길이가 숫자가 아니면 0`() {
        val db = TunnelDatabase.parse("1;이름;primary;육백;36.35,127.40|36.35,127.41\n2;이름;primary;;36.35,127.40|36.35,127.41")
        assertEquals(listOf(0, 0), db.tunnels.map { it.lengthM })
    }

    @Test
    fun `현재 동작 - 소수 길이는 정수가 아니라서 0`() {
        val db = TunnelDatabase.parse("1;이름;primary;600.5;36.35,127.40|36.35,127.41")
        assertEquals(0, db.tunnels[0].lengthM)
    }

    @Test
    fun `음수 길이도 그대로 받아들인다`() {
        val db = TunnelDatabase.parse("1;이름;primary;-5;36.35,127.40|36.35,127.41")
        assertEquals(-5, db.tunnels[0].lengthM)
    }

    @Test
    fun `점이 2개 미만이면 건너뜀`() {
        val csv = """
            1;점하나;primary;100;36.35,127.40
            2;점없음;primary;100;
            3;정상;primary;100;36.35,127.40|36.35,127.41
        """.trimIndent()
        assertEquals(listOf(3L), TunnelDatabase.parse(csv).tunnels.map { it.id })
    }

    @Test
    fun `잘못된 좌표 쌍은 조용히 빠지고 남은 점이 2개 이상이면 터널로 남는다`() {
        val csv = "1;이름;primary;100;36.35,127.40|xx,127.405|36.35|36.35,127.41,9|36.35,127.41|"
        val t = TunnelDatabase.parse(csv).tunnels.single()
        assertEquals(listOf(LatLng(36.35, 127.40), LatLng(36.35, 127.41)), t.points)
    }

    @Test
    fun `잘못된 좌표를 빼고 1개만 남으면 건너뜀`() {
        val csv = "1;이름;primary;100;36.35,127.40|36.35,abc"
        assertTrue(TunnelDatabase.parse(csv).tunnels.isEmpty())
    }

    @Test
    fun `현재 동작 - 좌표 숫자 앞뒤 공백은 허용된다`() {
        val t = TunnelDatabase.parse("1;이름;primary;100;36.35, 127.40| 36.35 ,127.41").tunnels.single()
        assertEquals(2, t.points.size)
        assertEquals(127.40, t.entry.lng, 1e-12)
    }

    @Test
    fun `현재 동작 - NaN 좌표도 걸러내지 않는다`() {
        val t = TunnelDatabase.parse("1;이름;primary;100;NaN,127.40|36.35,127.41").tunnels.single()
        assertTrue(t.entry.lat.isNaN())
        assertTrue(distanceMeters(LatLng(36.35, 127.40), t.entry).isNaN())
    }

    @Test
    fun `위경도 범위는 검사하지 않는다`() {
        val t = TunnelDatabase.parse("1;이름;primary;100;999,999|-999,-999").tunnels.single()
        assertEquals(999.0, t.entry.lat, 0.0)
    }

    @Test
    fun `같은 id 가 두 번 나오면 둘 다 남는다 (순서 유지)`() {
        val db = TunnelDatabase.parse("$good\n1;둘째;primary;10;36.36,127.40|36.36,127.41")
        assertEquals(listOf("정상", "둘째"), db.tunnels.map { it.name })
    }

    @Test
    fun `entry 는 첫 점, exit 는 마지막 점 (방향 그대로)`() {
        val t = TunnelDatabase.parse("1;역방향;primary;100;36.35,127.41|36.35,127.405|36.35,127.40").tunnels.single()
        assertEquals(127.41, t.entry.lng, 0.0)
        assertEquals(127.40, t.exit.lng, 0.0)
    }

    private val line = TunnelDatabase.parse(good).tunnels.single()

    @Test
    fun `distanceTo - 중심선 위는 0, 옆으로 비켜나면 수직 거리`() {
        assertEquals(0.0, line.distanceTo(LatLng(36.35, 127.403)), 1e-6)
        assertEquals(111.32, line.distanceTo(LatLng(36.351, 127.403)), 0.01)
    }

    @Test
    fun `distanceTo - 선분 끝 너머는 끝점까지 거리`() {
        val p = LatLng(36.35, 127.39)
        assertEquals(distanceMeters(p, line.entry), line.distanceTo(p), 1.0)
        val q = LatLng(36.351, 127.41)
        assertEquals(distanceMeters(q, line.exit), line.distanceTo(q), 1.0)
    }

    @Test
    fun `distanceTo - 같은 점이 두 번이면(길이 0 선분) 그 점까지 거리`() {
        val t = Tunnel(1, "점", "primary", 0, listOf(LatLng(36.35, 127.40), LatLng(36.35, 127.40)))
        val p = LatLng(36.351, 127.40)
        assertEquals(distanceMeters(p, t.entry), t.distanceTo(p), 0.5)
    }

    @Test
    fun `현재 동작 - 점이 1개뿐인 터널(직접 생성)의 distanceTo 는 Double MAX_VALUE`() {
        val t = Tunnel(1, "점", "primary", 0, listOf(LatLng(36.35, 127.40)))
        assertEquals(Double.MAX_VALUE, t.distanceTo(LatLng(36.35, 127.40)), 0.0)
        assertNull(TunnelDatabase(listOf(t)).nearest(LatLng(36.35, 127.40)))
    }

    private val two = TunnelDatabase.parse(
        """
        1;남쪽;primary;600;36.350000,127.400000|36.350000,127.406700
        2;북쪽;primary;600;36.352000,127.400000|36.352000,127.406700
        """.trimIndent()
    )

    @Test
    fun `nearest - 가까운 쪽과 그 거리`() {
        val (t, d) = two.nearest(LatLng(36.3515, 127.403))!!
        assertEquals("북쪽", t.name)
        assertEquals(55.66, d, 0.05)
    }

    @Test
    fun `nearest - maxDistanceM 는 미만(엄격) 비교`() {
        val p = LatLng(36.3505, 127.403)
        val d = two.tunnels[0].distanceTo(p)
        assertNull(two.nearest(p, d))
        assertNotNull(two.nearest(p, d + 1e-9))
    }

    @Test
    fun `nearest - 거리가 같으면 목록 앞쪽이 이긴다`() {
        val (t, _) = two.nearest(LatLng(36.351, 127.403))!!
        assertEquals("남쪽", t.name)
    }

    @Test
    fun `nearest - filter 로 제외한 터널은 보지 않는다`() {
        val p = LatLng(36.3501, 127.403)
        assertEquals("북쪽", two.nearest(p) { it.id != 1L }!!.first.name)
        assertNull(two.nearest(p) { false })
        assertNull(two.nearest(p, 50.0) { it.id != 1L })
    }

    @Test
    fun `entriesWithin - 입구(첫 점) 기준, 경계 포함, 가까운 순`() {
        val p = LatLng(36.3515, 127.399)
        val list = two.entriesWithin(p, 1000.0)
        assertEquals(listOf("북쪽", "남쪽"), list.map { it.first.name })
        val dNorth = list[0].second
        assertEquals(1, two.entriesWithin(p, dNorth).size)
        assertEquals(0, two.entriesWithin(p, dNorth - 1e-6).size)
    }

    @Test
    fun `entriesWithin - 중심선 옆이라도 입구가 멀면 빠진다`() {
        assertTrue(two.entriesWithin(LatLng(36.35, 127.4067), 500.0).isEmpty())
    }

    @Test
    fun `현재 동작 - entriesWithin 은 반경 0 이면 입구와 정확히 같은 점만`() {
        assertEquals(1, two.entriesWithin(LatLng(36.35, 127.40), 0.0).size)
    }

    @Test
    fun `within - 한 점이라도 범위 안이면 포함, 경계 포함`() {
        assertEquals(listOf("남쪽"), two.within(36.35, 36.3501, 127.4067, 127.5).map { it.name })
        assertTrue(two.within(36.34, 36.36, 127.402, 127.403).isEmpty())
    }

    @Test
    fun `within - 최소가 최대보다 크면 빈 결과`() {
        assertTrue(two.within(36.36, 36.34, 127.39, 127.41).isEmpty())
    }

    @Test
    fun `distanceMeters 는 대칭이고 같은 점이면 0`() {
        val a = LatLng(36.35, 127.40); val b = LatLng(36.36, 127.42)
        assertEquals(distanceMeters(a, b), distanceMeters(b, a), 1e-9)
        assertEquals(0.0, distanceMeters(a, a), 0.0)
    }
}
