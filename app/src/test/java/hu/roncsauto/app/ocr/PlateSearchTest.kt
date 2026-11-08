package hu.roncsauto.app.ocr

import hu.roncsauto.app.plate.HungarianPlate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlateSearchTest {
    @Test
    fun regionsCoverImageWithinBounds() {
        val w = 921
        val h = 2048
        val regions = PlateSearch.regions(w, h)
        assertEquals(13, regions.size)
        for (r in regions) {
            assertTrue(r.left >= 0 && r.top >= 0 && r.left + r.width <= w && r.top + r.height <= h)
        }
        assertTrue(PlateSearch.regions(0, 10).isEmpty())
    }

    @Test
    fun upscaleIsBounded() {
        assertEquals(2f, PlateSearch.upscale(500, 600, 2048), 0f)
        assertEquals(1.5f, PlateSearch.upscale(1000, 1365, 2048), 0.01f)
        assertEquals(1f, PlateSearch.upscale(3000, 2000, 2048), 0f)
    }

    @Test
    fun pickPrefersRepeatedReadings() {
        val reads = listOf("IUX-B68", "IUX-868", "1UX-868", "LUX-B68").mapNotNull { HungarianPlate.findBest(it) }
        assertEquals("IUX-868", PlateSearch.pick(reads)!!.canonical)
        assertNull(PlateSearch.pick(emptyList()))
    }

    @Test
    fun warpsStartWithShear() {
        assertTrue(PlateSearch.WARPS.first().skewY > 0.8f)
        assertTrue(PlateSearch.WARPS.any { it.skewY < 0f })
        assertEquals("skew 0.85", PlateSearch.WARPS.first().label)
    }

    @Test
    fun singleStrongMisreadIsNotConfidentAndRepeatsWin() {
        // Emulátoros valós sorrend: "FIUX 868" (gyenge), "IUX869" (erős, de félreolvasott).
        val first = listOf("FIUX 868", "IUX869").flatMap { HungarianPlate.candidates(it) }
        assertTrue(!PlateSearch.confident(first))
        val more = first + HungarianPlate.candidates("IUX-868") + HungarianPlate.candidates("IUX868")
        assertTrue(PlateSearch.confident(more))
        assertEquals("IUX-868", PlateSearch.pick(more)!!.canonical)
        // Emulátoros valós olvasatsor (8. futás): "FIU-868" kétszer is erős, mégis IUX-868 a konszenzus.
        val real = listOf("EIUK 869", "EIUX-868", "IUK-868", "698-XOTE", "FIU-868", "FIUX-868", "FIUXK-868", "FIU-868", "FIUX-868")
        val reads = mutableListOf<hu.roncsauto.app.plate.HungarianPlate.Match>()
        for ((index, text) in real.withIndex()) {
            reads += HungarianPlate.candidates(text)
            if (index == 4) assertTrue("FIU-868 egyszer még nem dönt", !PlateSearch.confident(reads))
            if (index == 2) assertEquals("IUK-868 + EIUK 869 + EIUX-868 után sincs döntés", false, PlateSearch.confident(reads))
        }
        assertEquals("IUX-868", PlateSearch.pick(reads)!!.canonical)
    }

    @Test
    fun newFormatOutsideASeriesScoresBelowClassic() {
        assertTrue(HungarianPlate.findBest("IUXK-868")!!.score < HungarianPlate.findBest("IUX-868")!!.score)
        assertTrue(HungarianPlate.findBest("AE FG-123")!!.score >= PlateSearch.STRONG_SCORE)
    }

    @Test
    fun chromeBandsTrimScreenshotBars() {
        assertEquals(106 to 2484, PlateSearch.chromeBands(2670))
    }

    @Test
    fun refineHelpers() {
        assertTrue(PlateSearch.plateLike("898-XOIE"))
        assertTrue(PlateSearch.plateLike("EIUX-&68"))
        assertTrue(!PlateSearch.plateLike("22:58"))
        assertTrue(!PlateSearch.plateLike("RENAULT"))
        assertEquals(PlateSearch.Region(30, 79, 340, 102), PlateSearch.expand(100, 100, 200, 60, 1000, 1000))
        assertEquals(PlateSearch.Region(0, 0, 135, 81), PlateSearch.expand(0, 0, 100, 60, 1000, 1000))
        assertEquals(3f, PlateSearch.refineScale(60), 0f)
        assertEquals(1f, PlateSearch.refineScale(500), 0f)
    }
}