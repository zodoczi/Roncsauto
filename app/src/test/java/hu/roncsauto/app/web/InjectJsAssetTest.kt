package hu.roncsauto.app.web

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InjectJsAssetTest {
    @Test
    fun injectionScriptsMentionRealSelectors() {
        val root = File("src/main/assets/inject")
        val jszp = File(root, "jszp.js").readText()
        val jaro = File(root, "jarokelo.js").readText()
        assertTrue(jszp.contains("rendszam") || jszp.contains("rendsz"))
        assertTrue(jaro.contains("description"))
        assertTrue(jaro.contains("leiras") || jaro.contains("le\\u00edr"))
    }
}