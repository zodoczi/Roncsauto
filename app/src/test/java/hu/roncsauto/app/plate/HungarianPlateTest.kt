package hu.roncsauto.app.plate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HungarianPlateTest {

    @Test
    fun classicFormats() {
        assertEquals("ABC-123", HungarianPlate.findBest("ABC-123")!!.canonical)
        assertEquals("ABC-123", HungarianPlate.findBest("abc 123")!!.canonical)
        assertEquals("ABC-123", HungarianPlate.findBest("ABC123")!!.canonical)
        assertEquals("ABC-123", HungarianPlate.findBest("ABC–123")!!.canonical)
    }

    @Test
    fun modernFourLetterPlates() {
        assertEquals("BB CD-123", HungarianPlate.findBest("BB CD-123")!!.canonical)
        assertEquals("BB CD-123", HungarianPlate.findBest("BBCD-123")!!.canonical)
        assertEquals("BB CD-123", HungarianPlate.findBest("bbcd123")!!.canonical)
        assertEquals("AE FG-123", HungarianPlate.findBest("AE FG-123")!!.canonical)
        assertEquals("AE FG-123", HungarianPlate.findBest("AEFG123")!!.canonical)
        assertEquals("AA AA-001", HungarianPlate.findBest("AA AA-001")!!.canonical)
        assertEquals("TX AB-123", HungarianPlate.findBest("TX AB-123")!!.canonical)
        assertEquals("OT AA-001", HungarianPlate.findBest("OT AA-001")!!.canonical)
    }

    @Test
    fun rejectsInvalidModernPairsAndShortNoise() {
        assertNull(HungarianPlate.findBest("ABCD-123"))
        assertNull(HungarianPlate.findBest("CS AB-123"))
        assertNull(HungarianPlate.findBest("A-12"))
        assertNull(HungarianPlate.findBest("AB-123"))
        assertNull(HungarianPlate.findBest("HELLO"))
        assertNull(HungarianPlate.findBest(""))
    }

    @Test
    fun oldTwoLetterFourDigitPlates() {
        assertEquals("GT-12-34", HungarianPlate.findBest("GT-12-34")!!.canonical)
        assertEquals("GT-12-34", HungarianPlate.findBest("gt 12 34")!!.canonical)
        assertEquals("GT-12-34", HungarianPlate.findBest("GT1234")!!.canonical)
    }

    @Test
    fun findsPlateInsideSentenceAndPrefersIsolatedLine() {
        assertEquals(
            "AE FG-123",
            HungarianPlate.findBest("A fotón ez olvasható: AE FG-123.")!!.canonical
        )
        val text = "rendszam ABC-999 a sorban\nBB CD-123"
        assertEquals("BB CD-123", HungarianPlate.findBest(text)!!.canonical)
    }

    @Test
    fun doesNotTreatLongerDigitRunAsPlate() {
        assertNull(HungarianPlate.findBest("ABC-1234"))
        assertNull(HungarianPlate.findBest("BBCD-1234"))
    }

    @Test
    fun samePlateIgnoresFormatting() {
        assertTrue(HungarianPlate.same("BB CD-123", "BBCD123"))
        assertTrue(HungarianPlate.same("ABC-123", "abc123"))
        assertFalse(HungarianPlate.same("ABC-123", "BB CD-123"))
        assertFalse(HungarianPlate.same("ABC-123", "nincs"))
    }

    @Test
    fun findAllReturnsDistinctPlates() {
        val plates = HungarianPlate.findAll("ABC-123 és BB CD-999").map { it.canonical }
        assertEquals(listOf("ABC-123", "BB CD-999"), plates)
    }
}
