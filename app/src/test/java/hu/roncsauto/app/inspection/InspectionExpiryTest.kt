package hu.roncsauto.app.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InspectionExpiryTest {
    private val today = LocalDate.of(2026, 11, 8)
    private val plate = "ABC-123"

    @Test
    fun parsesHungarianAndIsoDates() {
        val dotted = InspectionExpiry.analyze("Műszaki érvényesség: 2020.03.15. Rendszám: ABC-123", plate, today)
        assertEquals(InspectionExpiry.Status.MATCHED, dotted.status)
        assertEquals(LocalDate.of(2020, 3, 15), dotted.date)
        assertTrue(dotted.expired)
        assertTrue(dotted.canApply)

        val spaced = InspectionExpiry.analyze("műszaki érvényesség 2020. 03. 15. ABC-123", plate, today)
        assertEquals(LocalDate.of(2020, 3, 15), spaced.date)

        val iso = InspectionExpiry.analyze("érvényes: 2020-03-15 ABC-123", plate, today)
        assertEquals(LocalDate.of(2020, 3, 15), iso.date)

        val dmy = InspectionExpiry.analyze("lejárat 15.03.2020 ABC-123", plate, today)
        assertEquals(LocalDate.of(2020, 3, 15), dmy.date)

        val short = InspectionExpiry.analyze("2020.3.5 ABC-123", plate, today)
        assertEquals(LocalDate.of(2020, 3, 5), short.date)
    }

    @Test
    fun todayAndFutureAreNotExpired() {
        val todayResult = InspectionExpiry.analyze("Műszaki érvényesség: 2026.11.08. ABC-123", plate, today)
        assertEquals(LocalDate.of(2026, 11, 8), todayResult.date)
        assertFalse(todayResult.expired)
        assertTrue(todayResult.summary.contains("nem kerül be lejártként"))

        val future = InspectionExpiry.analyze("Műszaki érvényesség: 2027.01.02. ABC-123", plate, today)
        assertFalse(future.expired)
        assertFalse(InspectionExpiry.isExpired(LocalDate.of(2026, 11, 8), today))
        assertFalse(InspectionExpiry.isExpired(LocalDate.of(2026, 11, 9), today))
        assertTrue(InspectionExpiry.isExpired(LocalDate.of(2026, 11, 7), today))
    }

    @Test
    fun rejectsInvalidDatesAndDoesNotInventOne() {
        assertEquals(
            InspectionExpiry.Status.NO_DATE,
            InspectionExpiry.analyze("A műszaki lejárt.", plate, today).status
        )
        assertEquals(
            InspectionExpiry.Status.NO_DATE,
            InspectionExpiry.analyze("2020.02.31 ABC-123", plate, today).status
        )
        assertEquals(
            InspectionExpiry.Status.NO_DATE,
            InspectionExpiry.analyze("31.02.2020 ABC-123", plate, today).status
        )
        assertEquals(
            InspectionExpiry.Status.NO_DATE,
            InspectionExpiry.analyze("15/03/2020 ABC-123", plate, today).status
        )
        assertNull(InspectionExpiry.analyze("nincs adat ABC-123", plate, today).date)
    }

    @Test
    fun prefersTechnicalExpiryOverOtherRegistryDates() {
        val text = "Forgalomba helyezés: 2001.01.01. Műszaki érvényesség: 2019.05.01. Rendszám ABC-123"
        val result = InspectionExpiry.analyze(text, plate, today)
        assertEquals(LocalDate.of(2019, 5, 1), result.date)
        assertEquals(InspectionExpiry.Status.MATCHED, result.status)
    }

    @Test
    fun ambiguousWhenSeveralUndistinguishedDates() {
        val result = InspectionExpiry.analyze("2010.01.01 és 2011.02.02 ABC-123", plate, today)
        assertEquals(InspectionExpiry.Status.AMBIGUOUS, result.status)
        assertNull(result.date)
        assertFalse(result.canApply)
    }

    @Test
    fun plateMustMatch() {
        val mismatch = InspectionExpiry.analyze(
            "Rendszám: XYZ-999. Műszaki érvényesség: 2019.01.01.",
            plate,
            today
        )
        assertEquals(InspectionExpiry.Status.PLATE_MISMATCH, mismatch.status)
        assertNull(mismatch.date)
        assertFalse(mismatch.canApply)
        assertFalse(mismatch.expired)

        val missingPlate = InspectionExpiry.analyze("Műszaki érvényesség: 2019.01.01.", plate, today)
        assertEquals(InspectionExpiry.Status.NEEDS_PLATE_CONFIRMATION, missingPlate.status)
        assertEquals(LocalDate.of(2019, 1, 1), missingPlate.date)
        assertTrue(missingPlate.canApply)

        val several = InspectionExpiry.analyze(
            "ABC-123 és DEF-456. Műszaki érvényesség: 2019.01.01.",
            plate,
            today
        )
        assertEquals(InspectionExpiry.Status.NEEDS_PLATE_CONFIRMATION, several.status)
        assertTrue(several.canApply)
    }

    @Test
    fun matchesModernPlateFormatting() {
        val result = InspectionExpiry.analyze(
            "Rendszám BBCD123. Műszaki érvényesség: 2018.12.01.",
            "BB CD-123",
            today
        )
        assertEquals(InspectionExpiry.Status.MATCHED, result.status)
        assertEquals(LocalDate.of(2018, 12, 1), result.date)
    }

    @Test
    fun changingPlateDropsExpiry() {
        val expiry = LocalDate.of(2019, 1, 1)
        assertEquals(expiry, InspectionExpiry.retainForPlate("ABC-123", "abc123", expiry))
        assertNull(InspectionExpiry.retainForPlate("ABC-123", "DEF-456", expiry))
        assertNull(InspectionExpiry.retainForPlate("ABC-123", null, expiry))
        assertNull(InspectionExpiry.retainForPlate(null, "ABC-123", expiry))
    }

    @Test
    fun leapDayAccepted() {
        val result = InspectionExpiry.analyze("2020.02.29 ABC-123", plate, today)
        assertEquals(LocalDate.of(2020, 2, 29), result.date)
    }
}
