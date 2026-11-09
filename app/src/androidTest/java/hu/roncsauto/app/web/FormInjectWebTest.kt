package hu.roncsauto.app.web

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@LargeTest
class FormInjectWebTest {

    @Test
    fun fillsJszpMockAndReadsResult() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val latch = CountDownLatch(1)
        var filled = false
        var body = ""
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post {
            val web = WebView(context)
            web.settings.javaScriptEnabled = true
            web.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    FormInject.fillJszpPlate(context, view, "IUX868") { json ->
                        filled = json.optBoolean("ok")
                        view.evaluateJavascript(
                            "(function(){document.getElementById('kereses').click(); return '1';})();"
                        ) {
                            FormInject.readBody(context, view) { text ->
                                body = text
                                latch.countDown()
                            }
                        }
                    }
                }
            }
            web.loadUrl("file:///android_asset/mock/jszp_form.html")
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        assertTrue(filled)
        assertTrue(body.contains("IUX868"))
        assertTrue(body.contains("2026.01.24"))
    }

    @Test
    fun fillsJarokeloDescriptionMock() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val latch = CountDownLatch(1)
        var filled = false
        var value = ""
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        main.post {
            val web = WebView(context)
            web.settings.javaScriptEnabled = true
            web.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    val text = "Elhagyott / roncs autó. Rendszám: IUX-868."
                    FormInject.fillJarokeloDescription(context, view, text) { json ->
                        filled = json.optBoolean("ok")
                        view.evaluateJavascript(
                            "(function(){return document.getElementById('description').value;})();"
                        ) { raw ->
                            value = try {
                                org.json.JSONTokener(raw).nextValue() as? String ?: ""
                            } catch (_: Exception) {
                                raw?.trim('"') ?: ""
                            }
                            latch.countDown()
                        }
                    }
                }
            }
            web.loadUrl("file:///android_asset/mock/jarokelo_form.html")
        }
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        assertTrue(filled)
        assertEquals("Elhagyott / roncs autó. Rendszám: IUX-868.", value)
    }
}
