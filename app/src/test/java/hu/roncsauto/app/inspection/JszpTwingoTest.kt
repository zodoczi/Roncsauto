package hu.roncsauto.app.inspection

import hu.roncsauto.app.plate.HungarianPlate
import hu.roncsauto.app.report.ReportText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Zoli valódi JSZP-szövege az IUX-868 Twingóhoz. */
class JszpTwingoTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val jszp = """
        Nyilvántartott rendszám
        IUX868

        Műszaki érvényesség
        2026.01.24.

        Járműtípusra vonatkozó adatok
        Gyártmány
        RENAULT

        Kereskedelmi leírás
        TWINGO

        Típusjel
        C06

        EK típus-jóváhagyási szám
        E2*98/14*0071

        Gyártás éve
        1999
    """.trimIndent()

    @Test
    fun parsesPlateAndExpiredDate() {
        assertEquals("IUX868", HungarianPlate.findBest(jszp)!!.compact)
        val result = InspectionExpiry.analyze(jszp, "IUX-868", today)
        assertEquals(InspectionExpiry.Status.MATCHED, result.status)
        assertEquals(LocalDate.of(2026, 1, 24), result.date)
        assertTrue(result.expired)
        assertTrue(result.canApply)
        assertEquals(listOf("IUX-868"), result.plates)
    }

    @Test
    fun extractsVehicleDescription() {
        assertEquals("RENAULT TWINGO, 1999", JszpVehicle.describe(jszp))
        assertEquals("RENAULT", JszpVehicle.describe("Gyártmány: RENAULT"))
        assertNull(JszpVehicle.describe("Műszaki érvényesség 2020.03.15."))
    }

    @Test
    fun reportIncludesVehicle() {
        val text = ReportText.build("IUX-868", LocalDate.of(2026, 1, 24), today, "RENAULT TWINGO, 1999")
        assertTrue(text, text.contains("IUX-868"))
        assertTrue(text, text.contains("RENAULT TWINGO, 1999"))
        assertTrue(text, text.contains("2026.01.24."))
    }
}