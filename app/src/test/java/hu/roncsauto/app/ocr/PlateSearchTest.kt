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
}