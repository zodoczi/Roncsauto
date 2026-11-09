package hu.roncsauto.app.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import hu.roncsauto.app.databinding.ActivityBrowserBinding
import hu.roncsauto.app.inspection.InspectionExpiry
import hu.roncsauto.app.inspection.JszpVehicle
import hu.roncsauto.app.report.HandoffText
import java.io.File
import java.time.LocalDate

/**
 * Beágyazott böngésző JSZP / Járókelő oldalakhoz.
 * Belépést nem automatizál; jelszót nem tárol.
 */
class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    private val handler = Handler(Looper.getMainLooper())
    private var mode: String = MODE_JSZP
    private var plate: String? = null
    private var description: String? = null
    private var photoPath: String? = null
    private var startUrl: String = ""
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var lastPageText: String = ""
    private var injectAttempts = 0
    private var injectOk = false

    private val injectRetry = object : Runnable {
        override fun run() {
            if (injectOk || isDestroyed) return
            if (injectAttempts >= MAX_INJECT_ATTEMPTS) {
                binding.tvStatus.text = "A mezőt nem találtam. A szöveg a vágólapon van — illeszd be kézzel."
                toast("Mező nem található, vágólapra másoltam.")
                return
            }
            injectAttempts++
            doInject { ok ->
                if (!ok && !isDestroyed) handler.postDelayed(this, INJECT_INTERVAL_MS)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_JSZP
        plate = intent.getStringExtra(EXTRA_PLATE)
        description = intent.getStringExtra(EXTRA_DESCRIPTION)
        photoPath = intent.getStringExtra(EXTRA_PHOTO_PATH)
        startUrl = intent.getStringExtra(EXTRA_URL) ?: when (mode) {
            MODE_JAROKELO -> HandoffText.JAROKELO_URL
            else -> HandoffText.JSZP_URL
        }

        binding.tvBrowserTitle.text = if (mode == MODE_JAROKELO) "Járókelő" else "JSZP"
        binding.btnRead.visibility = if (mode == MODE_JSZP) View.VISIBLE else View.GONE
        binding.btnUse.visibility = if (mode == MODE_JSZP) View.VISIBLE else View.GONE
        binding.btnInject.text = if (mode == MODE_JAROKELO) "Leírás beillesztése" else "Rendszám beillesztése"

        when (mode) {
            MODE_JSZP -> plate?.let { copyText(HandoffText.jszpClipboardPlate(it)) }
            MODE_JAROKELO -> description?.let { copyText(it) }
        }

        setupWebView()
        binding.btnClose.setOnClickListener { finish() }
        binding.btnExternal.setOnClickListener { openExternal(binding.webView.url ?: startUrl) }
        binding.btnInject.setOnClickListener {
            injectAttempts = 0
            injectOk = false
            handler.removeCallbacks(injectRetry)
            handler.post(injectRetry)
        }
        binding.btnRead.setOnClickListener { readAndShow() }
        binding.btnUse.setOnClickListener { useResult() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) binding.webView.goBack() else finish()
            }
        })

        binding.tvStatus.text = when (mode) {
            MODE_JAROKELO ->
                "A leírás a vágólapon van. Belépés után a „Leírás beillesztése” gomb kitölti a mezőt. Küldést nem indítunk."
            else ->
                "A rendszám a vágólapon van. Belépés után a „Rendszám beillesztése” kitölti a mezőt. KERESÉS-t te nyomj."
        }
        binding.webView.loadUrl(startUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            userAgentString = userAgentString // keep default Chrome UA
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            binding.webView.settings.safeBrowsingEnabled = true
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (WebHosts.isAllowed(uri) || uri.scheme == "file") {
                    false
                } else {
                    openExternal(uri.toString())
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                injectAttempts = 0
                injectOk = false
                handler.removeCallbacks(injectRetry)
                handler.postDelayed(injectRetry, 800)
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                val photo = photoPath?.let { File(it) }?.takeIf { it.exists() }
                if (photo != null) {
                    val uri = FileProvider.getUriForFile(
                        this@BrowserActivity,
                        "${packageName}.fileprovider",
                        photo
                    )
                    grantUriPermission("com.android.chrome", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    // Offer our photo first via a chooser that also allows system picker as fallback
                    val cameraOrGallery = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    // Prefer returning our URI immediately for Járókelő convenience
                    fileCallback?.onReceiveValue(arrayOf(uri))
                    fileCallback = null
                    toast("A mentett fotót ajánlottam a csatoláshoz.")
                    return true
                }
                try {
                    startActivityForResult(
                        Intent.createChooser(
                            Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            },
                            "Fotó kiválasztása"
                        ),
                        REQ_FILE
                    )
                } catch (_: ActivityNotFoundException) {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = null
                    toast("Nincs fájlválasztó.")
                }
                return true
            }
        }
    }

    private fun doInject(done: (Boolean) -> Unit) {
        when (mode) {
            MODE_JAROKELO -> {
                val text = description ?: return done(false)
                FormInject.fillJarokeloDescription(this, binding.webView, text) { json ->
                    val ok = json.optBoolean("ok")
                    injectOk = ok
                    if (ok) {
                        binding.tvStatus.text = "Leírás beillesztve (#${json.optString("id")}). A helyszínt és a küldést te intézed."
                        toast("Leírás beillesztve.")
                    } else {
                        binding.tvStatus.text = "Még nincs leírás mező (próbálkozás $injectAttempts/$MAX_INJECT_ATTEMPTS)…"
                        copyText(text)
                    }
                    done(ok)
                }
            }
            else -> {
                val p = plate ?: return done(false)
                val compact = HandoffText.jszpClipboardPlate(p)
                FormInject.fillJszpPlate(this, binding.webView, compact) { json ->
                    val ok = json.optBoolean("ok")
                    injectOk = ok
                    if (ok) {
                        binding.tvStatus.text = "Rendszám beillesztve. Pipáld ki az adatokat, majd nyomj KERESÉS-re."
                        toast("Rendszám beillesztve.")
                    } else {
                        binding.tvStatus.text = "Még nincs rendszám mező (próbálkozás $injectAttempts/$MAX_INJECT_ATTEMPTS)…"
                        copyText(compact)
                    }
                    done(ok)
                }
            }
        }
    }

    private fun readAndShow() {
        FormInject.readBody(this, binding.webView) { text ->
            lastPageText = text
            val p = plate
            if (p == null || text.isBlank()) {
                binding.tvStatus.text = "Nincs olvasható szöveg az oldalon."
                binding.btnUse.isEnabled = false
                return@readBody
            }
            val parsed = InspectionExpiry.analyze(text, p, LocalDate.now())
            val vehicle = JszpVehicle.describe(text)
            val vehicleLine = vehicle?.let { "\nJármű: $it" } ?: ""
            binding.tvStatus.text = parsed.summary + vehicleLine
            binding.btnUse.isEnabled = parsed.canApply && parsed.date != null
            if (!parsed.canApply) toast("A beolvasott adatot ellenőrizd.")
        }
    }

    private fun useResult() {
        if (lastPageText.isBlank()) {
            toast("Előbb olvasd be az eredményt.")
            return
        }
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_PAGE_TEXT, lastPageText)
                .putExtra(EXTRA_MODE, mode)
        )
        finish()
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            toast("Nem nyílt meg a külső böngésző.")
        }
    }

    private fun copyText(text: String) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("roncsauto", text))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_FILE) return
        val uris = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(injectRetry)
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_URL = "url"
        const val EXTRA_PLATE = "plate"
        const val EXTRA_DESCRIPTION = "description"
        const val EXTRA_PHOTO_PATH = "photoPath"
        const val EXTRA_PAGE_TEXT = "pageText"
        const val MODE_JSZP = "jszp"
        const val MODE_JAROKELO = "jarokelo"
        private const val REQ_FILE = 4401
        private const val MAX_INJECT_ATTEMPTS = 12
        private const val INJECT_INTERVAL_MS = 1500L
        private const val TAG = "RoncsAutoWeb"
    }
}
