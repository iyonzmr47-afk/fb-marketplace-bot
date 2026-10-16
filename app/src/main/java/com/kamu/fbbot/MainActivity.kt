package com.kamu.fbbot

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONArray

data class Product(
    val judul: String,
    val harga: String,
    val kondisi: String,
    val deskripsi: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var logView: TextView
    private lateinit var statusText: TextView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var products: List<Product> = emptyList()
    private var isRunning = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        products = loadProducts()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1877F2"))
            setPadding(16, 16, 16, 16)
        }

        fun makeBtn(text: String, onClick: () -> Unit) = Button(this).apply {
            this.text = text
            textSize = 12f
            setOnClickListener { onClick() }
        }

        toolbar.addView(makeBtn("Login") {
            webView.loadUrl("https://www.facebook.com/login")
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        toolbar.addView(makeBtn("Market") {
            webView.loadUrl("https://www.facebook.com/marketplace/create/item")
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        toolbar.addView(makeBtn("Mulai") { startBot() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        toolbar.addView(makeBtn("Stop") { stopBot() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        statusText = TextView(this).apply {
            text = "Siap"
            setBackgroundColor(Color.parseColor("#EEEEEE"))
            setPadding(20, 10, 20, 10)
            textSize = 12f
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 3f
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString =
                    "Mozilla/5.0 (Linux; Android 13; SM-G991B) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@apply, true)
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    log("Load: ${url?.take(60)}")
                }

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    log("Error load: ${error?.description}")
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    log("JS: ${msg.message()}")
                    return true
                }
            }

            loadUrl("https://www.facebook.com/marketplace")
        }

        logView = TextView(this).apply {
            setBackgroundColor(Color.BLACK)
            setTextColor(Color.parseColor("#00FF00"))
            textSize = 10f
            setPadding(20, 20, 20, 20)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val logScroll = ScrollView(this).apply {
            addView(logView)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }

        root.addView(toolbar)
        root.addView(statusText)
        root.addView(webView)
        root.addView(logScroll)

        setContentView(root)
    }

    private fun log(msg: String) {
        runOnUiThread {
            val time = android.text.format.DateFormat.format(
                "HH:mm:ss", System.currentTimeMillis()
            )
            logView.append("[$time] $msg\n")
            (logView.parent as? ScrollView)?.post {
                (logView.parent as? ScrollView)?.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    private fun setStatus(msg: String) {
        runOnUiThread { statusText.text = msg }
    }

    private fun loadProducts(): List<Product> {
        return try {
            val json = assets.open("produk.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Product(
                    judul = o.getString("judul"),
                    harga = o.getString("harga"),
                    kondisi = o.optString("kondisi", "Baru"),
                    deskripsi = o.optString("deskripsi", "")
                )
            }
        } catch (e: Exception) {
            log("Load produk gagal: ${e.message}")
            emptyList()
        }
    }

    private fun startBot() {
        if (isRunning) { log("Bot sedang jalan"); return }
        if (products.isEmpty()) { log("Produk kosong"); return }
        isRunning = true
        scope.launch { runBot() }
    }

    private fun stopBot() {
        isRunning = false
        setStatus("Dihentikan")
        log("Bot dihentikan")
    }

    private suspend fun runBot() {
        log("Mulai posting ${products.size} produk")
        setStatus("Posting 0/${products.size}")

        for ((i, p) in products.withIndex()) {
            if (!isRunning) break

            log("---------------------")
            log("[${i + 1}/${products.size}] ${p.judul}")
            setStatus("[${i + 1}/${products.size}] ${p.judul.take(20)}")

            try {
                webView.loadUrl("https://www.facebook.com/marketplace/create/item")
                delay(6000)
                fillForm(p)
                delay(3000)
                log("Form terisi, cek layar")
            } catch (e: Exception) {
                log("Error: ${e.message}")
            }

            if (i < products.size - 1 && isRunning) {
                val jeda = (60..120).random()
                log("Jeda $jeda detik...")
                setStatus("Jeda ${jeda}s")
                delay(jeda * 1000L)
            }
        }

        isRunning = false
        log("Bot selesai")
        setStatus("Selesai")
    }

    private suspend fun fillForm(p: Product) {
        fun esc(s: String) = s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")

        val js = """
            (function() {
                var logs = [];
                function setNativeValue(element, value) {
                    var proto = Object.getPrototypeOf(element);
                    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                    if (desc && desc.set) {
                        desc.call(element, value);
                    } else {
                        element.value = value;
                    }
                    element.dispatchEvent(new Event('input', {bubbles: true}));
                    element.dispatchEvent(new Event('change', {bubbles: true}));
                }
                function tryFill(keyword, value) {
                    var inputs = document.querySelectorAll('input, textarea');
                    for (var i = 0; i < inputs.length; i++) {
                        var el = inputs[i];
                        var label = (el.getAttribute('aria-label') || '') + ' ' +
                                    (el.getAttribute('placeholder') || '') + ' ' +
                                    (el.getAttribute('name') || '');
                        if (label.toLowerCase().indexOf(keyword.toLowerCase()) !== -1) {
                            el.focus();
                            setNativeValue(el, value);
                            return label.trim().substring(0, 30);
                        }
                    }
                    return null;
                }
                var r1 = tryFill('judul', '${esc(p.judul)}') ||
                         tryFill('title', '${esc(p.judul)}');
                logs.push('Judul: ' + (r1 || 'X'));
                var r2 = tryFill('harga', '${p.harga}') ||
                         tryFill('price', '${p.harga}');
                logs.push('Harga: ' + (r2 || 'X'));
                var r3 = tryFill('deskripsi', '${esc(p.deskripsi)}') ||
                         tryFill('description', '${esc(p.deskripsi)}');
                logs.push('Desk: ' + (r3 || 'X'));
                return logs.join(' | ');
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            log("Isi: $result")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
