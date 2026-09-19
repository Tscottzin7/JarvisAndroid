package com.jarvis.assistant

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(),
    JarvisForegroundService.ServiceStateListener {

    private lateinit var webView: WebView

    private var jarvisService: JarvisForegroundService? = null
    private var isServiceBound = false

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val recordAudioGranted =
                permissions[Manifest.permission.RECORD_AUDIO] ?: false

            if (recordAudioGranted) {

                Log.d(
                    TAG,
                    "Permissão RECORD_AUDIO concedida."
                )

                startAndBindJarvisService()

            } else {

                Log.w(
                    TAG,
                    "Permissão RECORD_AUDIO negada pelo usuário."
                )
            }
        }

    private val serviceConnection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?
            ) {

                val binder =
                    service as JarvisForegroundService.LocalBinder

                jarvisService =
                    binder.getService()

                jarvisService?.setListener(
                    this@MainActivity
                )

                isServiceBound = true

                Log.d(
                    TAG,
                    "Conectado com sucesso ao JarvisForegroundService."
                )
            }

            override fun onServiceDisconnected(
                name: ComponentName?
            ) {

                jarvisService?.setListener(null)

                jarvisService = null

                isServiceBound = false

                Log.w(
                    TAG,
                    "Desconectado do JarvisForegroundService."
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        enableFullScreen()

        webView =
            findViewById(R.id.webView)

        configureWebView()

        checkAndRequestPermissions()
    }

    private fun enableFullScreen() {

        window.statusBarColor =
            Color.BLACK

        window.navigationBarColor =
            Color.BLACK

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {

            window.setDecorFitsSystemWindows(false)

            window.insetsController?.let { controller ->

                controller.hide(
                    WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars()
                )

                controller.systemBarsBehavior =
                    WindowInsetsController
                        .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

        } else {

            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    private fun configureWebView() {

        WebView.setWebContentsDebuggingEnabled(true)

        webView.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            allowFileAccess = true

            allowContentAccess = true

            databaseEnabled = true

            useWideViewPort = true

            loadWithOverviewMode = true

            mediaPlaybackRequiresUserGesture = false

            mixedContentMode =
                WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    Log.d(
                        TAG,
                        "WebView index.html carregado."
                    )
                }
            }

        // ========================================================
        // Ponte JavaScript -> AndroidBridge
        // ========================================================

        webView.addJavascriptInterface(

            AndroidBridge(

                onTriggerMic = {

                    runOnUiThread {

                        jarvisService
                            ?.startManualListening()
                    }
                },

                onSendPrompt = { promptText ->

                    runOnUiThread {

                        jarvisService
                            ?.sendPrompt(promptText)
                    }
                },

                onCloseApp = {

                    runOnUiThread {

                        closeJarvisApp()
                    }
                },

                onStopSpeaking = {

                    runOnUiThread {

                        jarvisService
                            ?.stopSpeaking()
                    }
                }
            ),

            "AndroidBridge"
        )

        // ========================================================
        // Ponte JavaScript -> Android
        // ========================================================

        webView.addJavascriptInterface(

            AndroidBridge(

                onTriggerMic = {

                    runOnUiThread {

                        jarvisService
                            ?.startManualListening()
                    }
                },

                onSendPrompt = { promptText ->

                    runOnUiThread {

                        jarvisService
                            ?.sendPrompt(promptText)
                    }
                },

                onCloseApp = {

                    runOnUiThread {

                        closeJarvisApp()
                    }
                },

                onStopSpeaking = {

                    runOnUiThread {

                        jarvisService
                            ?.stopSpeaking()
                    }
                }
            ),

            "Android"
        )

        webView.loadUrl(
            "file:///android_asset/index.html"
        )
    }

    private fun checkAndRequestPermissions() {

        val permissionsToRequest =
            mutableListOf<String>()

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            permissionsToRequest.add(
                Manifest.permission.RECORD_AUDIO
            )
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                permissionsToRequest.add(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }

        if (
            permissionsToRequest.isNotEmpty()
        ) {

            requestPermissionsLauncher.launch(
                permissionsToRequest.toTypedArray()
            )

        } else {

            startAndBindJarvisService()
        }
    }

    private fun startAndBindJarvisService() {

        val serviceIntent =
            Intent(
                this,
                JarvisForegroundService::class.java
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            startForegroundService(
                serviceIntent
            )

        } else {

            startService(
                serviceIntent
            )
        }

        bindService(
            serviceIntent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    // ============================================================
    // FECHAR APLICATIVO
    // ============================================================

    private fun closeJarvisApp() {

        Log.d(
            TAG,
            "Fechando aplicativo pelo botão X."
        )

        try {

            webView.stopLoading()

        } catch (_: Exception) {
        }

        try {

            if (isServiceBound) {

                jarvisService?.setListener(null)

                unbindService(
                    serviceConnection
                )

                isServiceBound = false
            }

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Erro ao desvincular serviço: ${e.message}"
            )
        }

        jarvisService = null

        finishAndRemoveTask()
    }

    // ============================================================
    // ESTADO DO JARVIS -> JAVASCRIPT
    // ============================================================

    override fun onJarvisStateChanged(
        state: String,
        text: String
    ) {

        runOnUiThread {

            val safeState =
                org.json.JSONObject.quote(
                    state
                )

            val safeText =
                org.json.JSONObject.quote(
                    text
                )

            val jsCommand =
                """
                if (typeof setJarvisState === 'function') {
                    setJarvisState($safeState, $safeText);
                }
                """.trimIndent()

            webView.evaluateJavascript(
                jsCommand,
                null
            )
        }
    }

    // ============================================================
    // STATUS WEBSOCKET
    // ============================================================

    override fun onConnectionStatusChanged(
        isConnected: Boolean
    ) {

        Log.d(
            TAG,
            "Status WebSocket: ${
                if (isConnected) "conectado" else "desconectado"
            }"
        )
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        try {

            if (isServiceBound) {

                jarvisService?.setListener(null)

                unbindService(
                    serviceConnection
                )

                isServiceBound = false
            }

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Erro ao desvincular serviço no onDestroy: ${e.message}"
            )
        }

        jarvisService = null

        super.onDestroy()
    }
}
