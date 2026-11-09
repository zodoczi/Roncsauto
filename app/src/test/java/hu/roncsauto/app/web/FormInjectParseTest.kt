package hu.roncsauto.app.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormInjectParseTest {
    @Test
    fun parsesQuotedJsonFromEvaluateJavascript() {
        val raw = "\"{\\\"ok\\\":true,\\\"reason\\\":\\\"filled\\\"}\""
        val json = FormInject.parse(raw)
        assertTrue(json.getBoolean("ok"))
        assertEquals("filled", json.getString("reason"))
    }

    @Test
    fun nullBecomesNotOk() {
        assertFalse(FormInject.parse(null).optBoolean("ok"))
        assertFalse(FormInject.parse("null").optBoolean("ok"))
    }
}
