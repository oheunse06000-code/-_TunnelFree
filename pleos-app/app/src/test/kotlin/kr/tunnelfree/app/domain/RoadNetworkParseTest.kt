package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RoadNetworkParseTest {

    private val v2 = "V 2\n36300000 127300000\n36310000 127300000\n"
    private fun edge(line: String) = RoadNetwork.parse("${v2}P 0\nE 1\n$line\n")

    private inline fun <reified T : Throwable> assertThrows(block: () -> Unit): T {
        try {
            block()
        } catch (t: Throwable) {
            if (t is T) return t
            fail("예상 ${T::class.simpleName}, 실제 $t")
        }
        fail("예외가 나지 않음 (예상 ${T::class.simpleName})")
        throw AssertionError()
    }

    @Test
    fun `섹션 중간의 주석·빈 줄은 건너뛰고 개수에 세지 않는다`() {
        val net = RoadNetwork.parse("# 머리\nV 2\n\n36300000 127300000\n# 중간\n36310000 127300000\n\nP 0\nE 1\n# x\n0 1 11132 4 0 0 0 0 길\n")
        assertEquals(2, net.vertexCount)
        assertEquals(1, net.edges.size)
        assertEquals(36.31, net.vertex(1).lat, 1e-9)
    }

    @Test
    fun `머리줄 개수 앞뒤 공백은 허용`() {
        val net = RoadNetwork.parse("V   2  \n36300000 127300000\n36310000 127300000\n")
        assertEquals(2, net.vertexCount)
    }

    @Test
    fun `공백 없는 머리줄은 무시`() {
        val net = RoadNetwork.parse("V\n${v2}P 0\nE 0\n")
        assertEquals(2, net.vertexCount)
    }

    @Test
    fun `P 섹션이 없어도 기하점 0개 간선은 읽힌다`() {
        val net = RoadNetwork.parse("${v2}E 1\n0 1 11132 4 0 0 0 0 길\n")
        assertEquals(1, net.edges.size)
        assertEquals(2, net.edgePoints(net.edges[0]).size)
    }

    @Test
    fun `알 수 없는 섹션 문자는 개수만큼 줄을 건너뛴다`() {
        val net = RoadNetwork.parse("X 2\n아무거나\n또 아무거나\n${v2}E 0\n")
        assertEquals(2, net.vertexCount)
    }

    @Test
    fun `간선 필드 해석 - 등급 코드·일방·터널 길이·이름`() {
        val net = edge("1 0 11132 99 2 12.5 0 0   대전  로  ")
        val e = net.edges.single()
        assertEquals(1, e.from); assertEquals(0, e.to)
        assertEquals(1113.2, e.lengthM, 1e-9)
        assertEquals(RoadClass.UNCLASSIFIED, e.roadClass)
        assertFalse(e.oneway)
        assertEquals(12.5, e.tunnelLengthM, 0.0)
        assertTrue(e.isTunnel)
        assertEquals("대전  로", e.name)
        assertEquals(0, e.index)
    }

    @Test
    fun `이름이 - 이거나 없으면 빈 문자열`() {
        assertEquals("", edge("0 1 11132 4 0 0 0 0 -").edges.single().name)
        assertEquals("", edge("0 1 11132 4 0 0 0 0").edges.single().name)
    }

    @Test
    fun `현재 동작 - 음수 터널 길이는 터널이 아닌 것으로 본다`() {
        val e = edge("0 1 11132 4 0 -5 0 0 길").edges.single()
        assertFalse(e.isTunnel)
    }

    @Test
    fun `현재 동작 - 같은 섹션이 두 번 나오면 뒤의 것이 앞을 덮어쓴다`() {
        val net = RoadNetwork.parse("V 1\n36300000 127300000\n$v2")
        assertEquals(2, net.vertexCount)
    }

    @Test
    fun `현재 동작 - 파일이 정점 섹션 중간에 끝나면 남은 정점은 (0,0)`() {
        val net = RoadNetwork.parse("V 3\n36300000 127300000\n36310000 127300000\n")
        assertEquals(3, net.vertexCount)
        assertEquals(LatLng(0.0, 0.0), net.vertex(2))
        assertEquals(0.0, net.bounds.minLat, 0.0)
        assertTrue(net.contains(LatLng(10.0, 10.0)))
    }

    @Test
    fun `현재 동작 - 정점 줄이 선언보다 많으면 남는 줄을 머리줄로 오인해 뒤 섹션을 통째로 삼킨다`() {
        val net = RoadNetwork.parse("V 1\n36300000 127300000\n36310000 127300000\nP 0\nE 1\n0 0 11132 4 0 0 0 0 길\n")
        assertEquals(1, net.vertexCount)
        assertTrue(net.isEmpty)
    }

    @Test
    fun `정점 줄이 선언보다 적고 다음 섹션이 이어지면 머리줄을 숫자로 읽다 예외`() {
        assertThrows<NumberFormatException> {
            RoadNetwork.parse("V 3\n36300000 127300000\n36310000 127300000\nP 0\nE 0\n")
        }
    }

    @Test
    fun `간선 줄이 선언보다 많으면 남는 줄을 머리줄로 읽다 예외`() {
        assertThrows<NumberFormatException> {
            RoadNetwork.parse("${v2}P 0\nE 1\n0 1 11132 4 0 0 0 0 길\n1 0 11132 4 0 0 0 0 길\n")
        }
    }

    @Test
    fun `개수가 숫자가 아니면 NumberFormatException`() {
        assertThrows<NumberFormatException> { RoadNetwork.parse("V abc\n") }
        assertThrows<NumberFormatException> { RoadNetwork.parse("V 2 3\n") }
    }

    @Test
    fun `음수 개수는 NegativeArraySizeException`() {
        assertThrows<NegativeArraySizeException> { RoadNetwork.parse("V -1\n") }
    }

    @Test
    fun `현재 동작 - 공백만 있는 줄은 빈 줄로 보지 않아 머리줄 위치에서 예외`() {
        assertThrows<NumberFormatException> { RoadNetwork.parse("   \n$v2") }
    }

    @Test
    fun `CRLF 줄바꿈 파일도 읽힌다 (BufferedReader readLine 이 CR 을 떼어 줌)`() {
        val net = RoadNetwork.parse("${v2}P 0\nE 1\n0 1 11132 4 1 0 0 0 길\n".replace("\n", "\r\n"))
        assertEquals(2, net.vertexCount)
        assertEquals(127.3, net.vertex(1).lng, 1e-9)
        assertEquals("길", net.edges.single().name)
        assertTrue(net.edges.single().oneway)
    }

    @Test
    fun `정점 줄 앞 공백·소수 좌표·공백 없는 줄은 예외`() {
        assertThrows<NumberFormatException> { RoadNetwork.parse("V 1\n 36300000 127300000\n") }
        assertThrows<NumberFormatException> { RoadNetwork.parse("V 1\n36.3 127.3\n") }
        assertThrows<StringIndexOutOfBoundsException> { RoadNetwork.parse("V 1\n36300000\n") }
        assertThrows<StringIndexOutOfBoundsException> { RoadNetwork.parse("V 1\n36300000\t127300000\n") }
    }

    @Test
    fun `간선 필드가 8개 미만이면 IndexOutOfBoundsException`() {
        assertThrows<IndexOutOfBoundsException> { edge("0 1 11132 4 0 0 0") }
    }

    @Test
    fun `간선 길이가 정수가 아니면 NumberFormatException`() {
        assertThrows<NumberFormatException> { edge("0 1 1113.2 4 0 0 0 0 길") }
    }

    @Test
    fun `없는 정점을 가리키는 간선은 생성 단계에서 ArrayIndexOutOfBoundsException`() {
        assertThrows<ArrayIndexOutOfBoundsException> { edge("0 5 11132 4 0 0 0 0 길") }
    }

    @Test
    fun `현재 동작 - 범위 밖 기하점 참조는 파싱은 통과하고 기하를 꺼낼 때 터진다`() {
        val net = edge("0 1 11132 4 0 0 10 2 길")
        assertEquals(1, net.edges.size)
        assertThrows<ArrayIndexOutOfBoundsException> { net.edgePoints(net.edges[0]) }
        assertThrows<ArrayIndexOutOfBoundsException> {
            net.route(LatLng(36.30, 127.30), LatLng(36.31, 127.30), AvoidanceLevel.NONE)
        }
    }
}
