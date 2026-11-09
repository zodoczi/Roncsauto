package hu.roncsauto.app.wizard

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportSessionTest {
    private fun session() = ReportSession(SavedStateHandle())

    @Test
    fun advancesAndGoesBack() {
        val s = session()
        assertEquals(WizardStep.PLATE, s.step)
        assertFalse(s.canAdvanceFromPlate())
        s.setPlate("IUX-868", allowClear = false)
        assertTrue(s.canAdvanceFromPlate())
        s.goNext()
        assertEquals(WizardStep.JSZP, s.step)
        s.goNext()
        assertEquals(WizardStep.JAROKELO, s.step)
        s.goNext()
        assertEquals(WizardStep.JAROKELO, s.step)
        s.goBack()
        assertEquals(WizardStep.JSZP, s.step)
    }

    @Test
    fun restartClearsEverything() {
        val s = session()
        s.setPlate("IUX-868", allowClear = false)
        s.setInspection(LocalDate.of(2020, 1, 1), "RENAULT TWINGO, 1999")
        s.photoMode = true
        s.photoSaved = true
        s.goNext()
        s.restart()
        assertEquals(WizardStep.PLATE, s.step)
        assertNull(s.plate)
        assertNull(s.expiry)
        assertNull(s.vehicle)
        assertFalse(s.photoMode)
        assertFalse(s.photoSaved)
    }

    @Test
    fun plateChangeDropsUnrelatedExpiry() {
        val s = session()
        s.setPlate("IUX-868", allowClear = false)
        s.setInspection(LocalDate.of(2020, 1, 1), "RENAULT")
        s.setPlate("ABC-123", allowClear = false)
        assertNull(s.expiry)
        assertNull(s.vehicle)
        assertEquals("ABC-123", s.plate)
    }
}
