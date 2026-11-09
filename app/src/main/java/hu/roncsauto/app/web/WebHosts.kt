package hu.roncsauto.app.web

import android.net.Uri

/** Engedélyezett hosztok a beágyazott böngészőhöz (űrlap + belépési IdP). */
object WebHosts {
    private val exact = setOf(
        "magyarorszag.hu",
        "www.magyarorszag.hu",
        "kau.gov.hu",
        "idp.gov.hu",
        "ugyfelkapu.gov.hu",
        "szeusz.gov.hu",
        "jarokelo.hu",
        "www.jarokelo.hu"
    )

    private val suffixes = listOf(
        ".magyarorszag.hu",
        ".jarokelo.hu",
        ".kau.gov.hu",
        ".idp.gov.hu",
        ".ugyfelkapu.gov.hu"
    )

    fun isAllowed(uri: Uri?): Boolean {
        val host = uri?.host?.lowercase() ?: return false
        if (host in exact) return true
        return suffixes.any { host.endsWith(it) }
    }

    fun isBridgeHost(uri: Uri?): Boolean {
        val host = uri?.host?.lowercase() ?: return false
        return host == "magyarorszag.hu" || host == "www.magyarorszag.hu" ||
            host == "jarokelo.hu" || host == "www.jarokelo.hu" ||
            host == "localhost" || host == "127.0.0.1" ||
            // file:///android_asset mock oldalak instrumentált teszthez
            uri.scheme.equals("file", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true) && host.endsWith(".app")
    }
}
