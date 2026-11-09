package hu.roncsauto.app.web

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A szelektoros JS fájlok a forrásfában megvannak (build előtt).
 * Az androidTest a mock HTML-en futtatja őket WebView-ban.
 */
class InjectJsAssetTest {
    @Test
    fun injectionScriptsMentionRealSelectors() {
        val root = File("src/main/assets/inject")
        // Gradle unit test cwd = app/
        val jszp = File(root, "jszp.js").readText()
        val jaro = File(root, "jarokelo.js").readText()
        assertTrue(jszp.contains("gépjármű rendszáma") || jszp.contains("gepjarmu"))
        assertTrue(jaro.contains("description"))
        assertTrue(jaro.contains("probléma leírása") || jaro.contains("leírás"))
    }
}
