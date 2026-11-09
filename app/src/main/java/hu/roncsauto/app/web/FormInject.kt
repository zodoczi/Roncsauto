package hu.roncsauto.app.web

import android.content.Context
import android.webkit.WebView
import org.json.JSONObject

/** Runs assets/inject JS files inside a WebView. */
object FormInject {

    fun loadAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun installCommon(context: Context, webView: WebView, then: (() -> Unit)? = null) {
        val js = loadAsset(context, "inject/common.js")
        webView.evaluateJavascript(js) { then?.invoke() }
    }

    fun fillJszpPlate(context: Context, webView: WebView, plate: String, callback: (JSONObject) -> Unit) {
        installCommon(context, webView) {
            val escaped = JSONObject.quote(plate)
            val body = "window.__roncsPlate=$escaped;" + loadAsset(context, "inject/jszp.js")
            webView.evaluateJavascript(body) { raw -> callback(parse(raw)) }
        }
    }

    fun fillJarokeloDescription(context: Context, webView: WebView, text: String, callback: (JSONObject) -> Unit) {
        installCommon(context, webView) {
            val escaped = JSONObject.quote(text)
            val body = "window.__roncsDescription=$escaped;" + loadAsset(context, "inject/jarokelo.js")
            webView.evaluateJavascript(body) { raw -> callback(parse(raw)) }
        }
    }

    fun readBody(context: Context, webView: WebView, callback: (String) -> Unit) {
        installCommon(context, webView) {
            val body = loadAsset(context, "inject/read_body.js")
            webView.evaluateJavascript(body) { raw ->
                val json = parse(raw)
                callback(json.optString("text"))
            }
        }
    }

    /** Parses the JSON-encoded string returned by evaluateJavascript. */
    fun parse(raw: String?): JSONObject {
        if (raw.isNullOrBlank() || raw == "null") return JSONObject().put("ok", false)
        val unquoted = try {
            org.json.JSONTokener(raw).nextValue() as? String ?: raw
        } catch (_: Exception) {
            raw.trim('"')
        }
        return try {
            JSONObject(unquoted)
        } catch (_: Exception) {
            JSONObject().put("ok", false).put("raw", unquoted)
        }
    }
}