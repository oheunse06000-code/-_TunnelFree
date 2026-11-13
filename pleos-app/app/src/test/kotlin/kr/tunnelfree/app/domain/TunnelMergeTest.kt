package kr.tunnelfree.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class TunnelMergeTest {
    private fun db(vararg lines: String) = TunnelDatabase.parse(lines.joinToString("\n"))

    @Test
    fun mergesSameNameChainInEitherCsvOrder() {
        val a = "10;문예지하차도;primary;74;36.370864,127.378695|36.371041,127.379492"
        val b = "11;문예지하차도;primary;96;36.371041,127.379492|36.371087,127.380562"
        for (d in listOf(db(a, b), db(b, a))) {
            val merged = d.mergeSegments().tunnels
            assertEquals(1, merged.size)
            val t = merged.single()
            assertEquals(10L, t.id)
            assertEquals(170, t.lengthM)
            assertEquals(3, t.points.size)
            assertEquals(LatLng(36.370864, 127.378695), t.entry)
            assertEquals(LatLng(36.371087, 127.380562), t.exit)
        }
    }

    @Test
    fun mergesThreeSegmentChain() {
        val d = db(
            "1;긴터널;primary;100;36.30,127.300|36.30,127.301",
            "2;긴터널;primary;100;36.30,127.301|36.30,127.302",
            "3;긴터널;primary;100;36.30,127.302|36.30,127.303",
        ).mergeSegments()
        assertEquals(1, d.tunnels.size)
        assertEquals(300, d.tunnels[0].lengthM)
        assertEquals(4, d.tunnels[0].points.size)
    }

    @Test
    fun keepsTwinBoresWhosePortalsAreMetersApart() {
        val d = db(
            "1;사름지하차도;primary;300;36.30,127.300|36.30,127.303",
            "2;사름지하차도;primary;300;36.30005,127.303|36.30005,127.300",
        )
        assertSame(d, d.mergeSegments())
    }

    @Test
    fun keepsDifferentNamesGapsAndOppositeBores() {
        val d = db(
            "1;터널A;primary;100;36.30,127.300|36.30,127.301",
            "2;터널B;primary;100;36.30,127.301|36.30,127.302",
            "3;터널A;primary;100;36.30,127.30104|36.30,127.303",
            "4;터널A;primary;100;36.3003,127.301|36.3003,127.300",
            "5;;primary;100;36.31,127.300|36.31,127.301",
            "6;;primary;100;36.31,127.301|36.31,127.302",
        )
        assertSame(d, d.mergeSegments())
    }

    @Test
    fun realAssetMergesOnlySplitWays() {
        val raw = TunnelDatabase.parse(File("src/main/assets/tunnels_daejeon.csv").readText())
        val merged = raw.mergeSegments()
        assertEquals(raw.tunnels.size - 2, merged.tunnels.size)
        assertEquals(listOf(170, 173), merged.tunnels.filter { it.name == "문예지하차도" }.map { it.lengthM }.sorted())
        assertEquals(raw.tunnels.sumOf { it.lengthM }, merged.tunnels.sumOf { it.lengthM })
        assertEquals(merged.tunnels.size, merged.tunnels.map { it.id }.toSet().size)
    }
}
