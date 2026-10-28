package de.crunchymate

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import de.crunchymate.bridge.AndroidBridge

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var bridge: AndroidBridge
    private lateinit var assetLoader: WebViewAssetLoader

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission granted or denied; foreground service handles fallback */ }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        bridge = AndroidBridge(this)

        configureWebView()
        setupBackNavigation()
        requestNotificationPermissionIfNecessary()

        loadWebApp()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
        }

        // Configure WebViewAssetLoader to serve bundled web assets under appassets domain
        val assetPathHandler = WebViewAssetLoader.PathHandler { path ->
            val cleanPath = path.trim().removePrefix("/assets/").removePrefix("/").ifEmpty { "index.html" }
            val candidates = listOf("web/$cleanPath", cleanPath)
            for (candidate in candidates) {
                try {
                    val stream = assets.open(candidate)
                    val mimeType = when {
                        candidate.endsWith(".html") -> "text/html"
                        candidate.endsWith(".css") -> "text/css"
                        candidate.endsWith(".js") -> "application/javascript"
                        candidate.endsWith(".json") -> "application/json"
                        candidate.endsWith(".png") -> "image/png"
                        candidate.endsWith(".jpg") || candidate.endsWith(".jpeg") -> "image/jpeg"
                        candidate.endsWith(".svg") -> "image/svg+xml"
                        candidate.endsWith(".ico") -> "image/x-icon"
                        else -> "application/octet-stream"
                    }
                    val encoding = if (mimeType.startsWith("text/") || mimeType == "application/javascript" || mimeType == "application/json") "UTF-8" else null
                    val headers = mapOf(
                        "Access-Control-Allow-Origin" to "*",
                        "Cache-Control" to "no-cache, no-store, must-revalidate"
                    )
                    return@PathHandler WebResourceResponse(mimeType, encoding, 200, "OK", headers, stream)
                } catch (_: Exception) {
                    // Try next candidate
                }
            }
            null
        }

        assetLoader = WebViewAssetLoader.Builder()
            .setDomain("appassets.androidplatform.net")
            .addPathHandler("/", assetPathHandler)
            .addPathHandler("/assets/", assetPathHandler)
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
                    ?: super.shouldInterceptRequest(view, request)
            }
        }

        // Expose native bridge interface as window.AndroidBridge
        webView.addJavascriptInterface(bridge, "AndroidBridge")
    }

    private fun loadWebApp() {
        try {
            webView.loadUrl("https://appassets.androidplatform.net/index.html")
        } catch (_: Exception) {
            // File URL fallback if https scheme asset loader fails
            webView.loadUrl("file:///android_asset/web/index.html")
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Check if detailModal is currently open in frontend and dismiss it first
                val jsCheckModal = """
                    (function() {
                        var modal = document.getElementById('detailModal');
                        if (modal && modal.style.display !== 'none' && modal.style.display !== '') {
                            if (typeof closeDetail === 'function') {
                                closeDetail();
                            } else {
                                modal.style.display = 'none';
                                document.body.style.overflow = '';
                            }
                            return true;
                        }
                        return false;
                    })();
                """.trimIndent()

                webView.evaluateJavascript(jsCheckModal) { result ->
                    val modalDismissed = result?.trim()?.equals("true", ignoreCase = true) == true
                    if (!modalDismissed) {
                        if (webView.canGoBack()) {
                            webView.goBack()
                        } else {
                            finish()
                        }
                    }
                }
            }
        })
    }

    private fun requestNotificationPermissionIfNecessary() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
