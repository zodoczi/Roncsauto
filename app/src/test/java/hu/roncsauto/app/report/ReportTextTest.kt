package hu.roncsauto.app.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportTextTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test
    fun omitsDateUnlessActuallyExpired() {
        assertEquals(
            "Elhagyott / roncs autó. Rendszám: ABC-123.",
            ReportText.build("ABC-123", null, today)
        )
        assertEquals(
            "Elhagyott / roncs autó. Rendszám: ABC-123.",
            ReportText.build("ABC-123", LocalDate.of(2026, 11, 8), today)
        )
        assertEquals(
            "Elhagyott / roncs autó. Rendszám: ABC-123.",
            ReportText.build("ABC-123", LocalDate.of(2026, 12, 1), today)
        )
        assertEquals(
            "Elhagyott / roncs autó. Rendszám: ABC-123. A műszaki vizsga lejárata: 2020.03.15. (lejárt).",
            ReportText.build("ABC-123", LocalDate.of(2020, 3, 15), today)
        )
    }

    @Test
    fun includesVehicleWhenPresent() {
        assertEquals(
            "Elhagyott / roncs autó. Rendszám: IUX-868. Jármű: RENAULT TWINGO, 1999. A műszaki vizsga lejárata: 2026.01.24. (lejárt).",
            ReportText.build("IUX-868", LocalDate.of(2026, 1, 24), today, "RENAULT TWINGO, 1999")
        )
    }

    @Test
    fun plateAloneDoesNotCreateADate() {
        val text = ReportText.build("ABC-123", null, today)
        assertFalse(Regex("""\d{4}""").containsMatchIn(text))
        assertFalse(text.contains("lejárt"))
    }

    @Test
    fun handoffDoesNotPretendToUploadOrPrefill() {
        assertEquals("https://jarokelo.hu/bejelentest-teszek", HandoffText.JAROKELO_URL)
        assertEquals("https://magyarorszag.hu/jszp_szuf", HandoffText.JSZP_URL)
        assertFalse(HandoffText.JAROKELO_URL.contains("description="))
        assertFalse(HandoffText.JSZP_URL.contains("description="))
        assertEquals("IUX868", HandoffText.jszpClipboardPlate("IUX-868"))
        assertTrue(HandoffText.JSZP_PASTE_HINT.contains("Gépjármű rendszáma"))
        assertTrue(HandoffText.JSZP_PASTE_HINT.contains("KERESÉS"))
        val withPhoto = HandoffText.jarokeloInstructions(true, false)
        assertTrue(withPhoto.contains("vágólapon"))
        assertTrue(withPhoto.contains("nem fogad előtöltött"))
        assertTrue(withPhoto.contains("nem küldi el"))
        val saved = HandoffText.jarokeloInstructions(true, true)
        assertTrue(saved.contains("Pictures/RoncsAuto"))
        val withoutPhoto = HandoffText.jarokeloInstructions(false, false)
        assertTrue(withoutPhoto.contains("Nincs kiválasztott fotó"))
        assertEquals("Elhagyott / roncs autó", ReportText.subject())
    }
}
