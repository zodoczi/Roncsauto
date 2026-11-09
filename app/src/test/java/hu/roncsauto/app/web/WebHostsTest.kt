package hu.roncsauto.app.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebHostsTest {
    @Test
    fun allowlistCoversFormsAndIdp() {
        assertTrue(allowed("magyarorszag.hu"))
        assertTrue(allowed("www.magyarorszag.hu"))
        assertTrue(allowed("kau.gov.hu"))
        assertTrue(allowed("idp.gov.hu"))
        assertTrue(allowed("ugyfelkapu.gov.hu"))
        assertTrue(allowed("szeusz.gov.hu"))
        assertTrue(allowed("jarokelo.hu"))
        assertTrue(allowed("www.jarokelo.hu"))
        assertFalse(allowed("evil.example.com"))
        assertFalse(allowed("google.com"))
    }

    private fun allowed(host: String): Boolean {
        val exact = setOf(
            "magyarorszag.hu", "www.magyarorszag.hu", "kau.gov.hu", "idp.gov.hu",
            "ugyfelkapu.gov.hu", "szeusz.gov.hu", "jarokelo.hu", "www.jarokelo.hu"
        )
        val suffixes = listOf(
            ".magyarorszag.hu", ".jarokelo.hu", ".kau.gov.hu", ".idp.gov.hu", ".ugyfelkapu.gov.hu"
        )
        return host in exact || suffixes.any { host.endsWith(it) }
    }
}
