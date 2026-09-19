package com.jarvis.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.Locale

class JarvisForegroundService :
    Service(),
    WebSocketManager.WebSocketMessageListener,
    TextToSpeech.OnInitListener {

    interface ServiceStateListener {

        fun onJarvisStateChanged(
            state: String,
            text: String
        )

        fun onConnectionStatusChanged(
            isConnected: Boolean
        )
    }

    private val binder = LocalBinder()

    private var stateListener: ServiceStateListener? = null

    private lateinit var webSocketManager: WebSocketManager
    private lateinit var wakeWordManager: WakeWordManager
    private lateinit var speechManager: SpeechManager

    private var wakeLock: PowerManager.WakeLock? = null

    private var currentState = "idle"

    // ============================================================
    // TTS NATIVO DO ANDROID
    // ============================================================

    private var textToSpeech: TextToSpeech? = null

    private var ttsReady = false

    private var pendingSpeechText: String? = null

    private val ttsUtteranceId = "jarvis_response"

    private val porcupineAccessKey =
        "SUA_ACCESS_KEY_AQUI"

    companion object {

        private const val TAG =
            "JarvisService"

        private const val CHANNEL_ID =
            "jarvis_service_channel"

        private const val NOTIFICATION_ID =
            1001

        var instance:
            JarvisForegroundService? = null
            private set
    }

    // ============================================================
    // BINDER
    // ============================================================

    inner class LocalBinder : Binder() {

        fun getService():
            JarvisForegroundService =
            this@JarvisForegroundService
    }

    override fun onBind(
        intent: Intent?
    ): IBinder {

        return binder
    }

    // ============================================================
    // CREATE
    // ============================================================

    override fun onCreate() {

        super.onCreate()

        instance = this

        Log.d(
            TAG,
            "Iniciando JarvisForegroundService..."
        )

        acquireWakeLock()

        createNotificationChannel()

        startForegroundServiceWithNotification()

        // --------------------------------------------------------
        // TTS NATIVO
        // --------------------------------------------------------

        textToSpeech =
            TextToSpeech(
                this,
                this
            )

        // --------------------------------------------------------
        // WEBSOCKET
        // --------------------------------------------------------

        webSocketManager =
            WebSocketManager(
                "ws://127.0.0.1:8765",
                this
            )

        webSocketManager.connect()

        // --------------------------------------------------------
        // SPEECH RECOGNIZER
        // --------------------------------------------------------

        speechManager =
            SpeechManager(

                context = this,

                onResult = { recognizedText ->

                    Log.d(
                        TAG,
                        "Comando de voz: $recognizedText"
                    )

                    updateState(
                        "thinking",
                        recognizedText
                    )

                    webSocketManager.sendPrompt(
                        recognizedText
                    )

                    /*
                     * Depois que o comando foi reconhecido,
                     * a wake word volta a ficar disponível.
                     */
                    wakeWordManager.start()
                },

                onError = { errorMsg ->

                    Log.e(
                        TAG,
                        "Erro SpeechRecognizer: $errorMsg"
                    )

                    updateState(
                        "idle",
                        errorMsg
                    )

                    wakeWordManager.start()
                },

                onStateChange = { isListening ->

                    if (isListening) {

                        updateState(
                            "listening"
                        )
                    }
                }
            )

        // --------------------------------------------------------
        // WAKE WORD
        // --------------------------------------------------------

        wakeWordManager =
            WakeWordManager(

                context = this,

                accessKey =
                    porcupineAccessKey,

                onWakeWordDetected = {

                    Log.d(
                        TAG,
                        "Wake Word ativada!"
                    )

                    playBeepSound()

                    wakeWordManager.stop()

                    speechManager.startListening()
                }
            )

        wakeWordManager.start()
    }

    // ============================================================
    // START COMMAND
    // ============================================================

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        return START_STICKY
    }

    // ============================================================
    // ESCUTA MANUAL
    // ============================================================

    fun startManualListening() {

        Log.d(
            TAG,
            "Escuta manual solicitada via UI..."
        )

        stopSpeaking()

        wakeWordManager.stop()

        speechManager.startListening()
    }

    // ============================================================
    // ENVIA PROMPT
    // ============================================================

    fun sendPrompt(
        text: String
    ) {

        stopSpeaking()

        updateState(
            "thinking",
            text
        )

        webSocketManager.sendPrompt(
            text
        )
    }

    // ============================================================
    // TTS INIT
    // ============================================================

    override fun onInit(
        status: Int
    ) {

        if (status != TextToSpeech.SUCCESS) {

            Log.e(
                TAG,
                "Falha ao inicializar Android TTS."
            )

            ttsReady = false

            return
        }

        val result =
            textToSpeech?.setLanguage(
                Locale(
                    "pt",
                    "BR"
                )
            )

        if (
            result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED
        ) {

            Log.e(
                TAG,
                "Idioma português do Brasil não disponível."
            )

            ttsReady = false

            return
        }

        textToSpeech?.setSpeechRate(
            1.0f
        )

        textToSpeech?.setPitch(
            1.0f
        )

        textToSpeech?.setOnUtteranceProgressListener(

            object : UtteranceProgressListener() {

                override fun onStart(
                    utteranceId: String?
                ) {

                    Log.d(
                        TAG,
                        "TTS começou a falar."
                    )
                }

                override fun onDone(
                    utteranceId: String?
                ) {

                    if (
                        utteranceId ==
                        ttsUtteranceId
                    ) {

                        Log.d(
                            TAG,
                            "TTS terminou de falar."
                        )

                        updateState(
                            "idle"
                        )
                    }
                }

                override fun onError(
                    utteranceId: String?
                ) {

                    if (
                        utteranceId ==
                        ttsUtteranceId
                    ) {

                        Log.e(
                            TAG,
                            "Erro durante TTS."
                        )

                        updateState(
                            "idle"
                        )
                    }
                }

                override fun onStop(
                    utteranceId: String?,
                    interrupted: Boolean
                ) {

                    if (
                        utteranceId ==
                        ttsUtteranceId
                    ) {

                        Log.d(
                            TAG,
                            "TTS interrompido."
                        )

                        updateState(
                            "idle"
                        )
                    }
                }
            }
        )

        ttsReady = true

        Log.d(
            TAG,
            "Android TTS pronto."
        )

        // Se alguma resposta chegou antes do TTS terminar
        // de inicializar, fala agora.
        pendingSpeechText?.let {

            pendingSpeechText = null

            speakText(it)
        }
    }

    // ============================================================
    // LIMPEZA DO TEXTO PARA VOZ
    // ============================================================

    private fun cleanTextForSpeech(
        text: String
    ): String {

        var result = text

        result =
            result.replace(
                Regex("```[\\s\\S]*?```"),
                ""
            )

        result =
            result.replace(
                Regex("\\*\\*(.*?)\\*\\*"),
                "$1"
            )

        result =
            result.replace(
                Regex("\\*(.*?)\\*"),
                "$1"
            )

        result =
            result.replace(
                Regex("`(.*?)`"),
                "$1"
            )

        result =
            result.replace(
                Regex("\\[(.*?)]\\(.*?\\)"),
                "$1"
            )

        result =
            result.replace(
                Regex("^\\s*#{1,6}\\s*"),
                ""
            )

        result =
            result.replace(
                Regex("^\\s*[-*+]\\s+"),
                ""
            )

        result =
            result.replace(
                Regex("\\s+"),
                " "
            )

        return result.trim()
    }

    // ============================================================
    // FALAR
    // ============================================================

    private fun speakText(
        text: String
    ) {

        val cleanText =
            cleanTextForSpeech(
                text
            )

        if (cleanText.isBlank()) {

            updateState(
                "idle"
            )

            return
        }

        if (!ttsReady) {

            pendingSpeechText =
                text

            return
        }

        Log.d(
            TAG,
            "Falando: $cleanText"
        )

        textToSpeech?.stop()

        updateState(
            "speaking",
            text
        )

        val params =
            Bundle()

        textToSpeech?.speak(
            cleanText,
            TextToSpeech.QUEUE_FLUSH,
            params,
            ttsUtteranceId
        )
    }

    // ============================================================
    // PARAR FALA
    // ============================================================

    fun stopSpeaking() {

        Log.d(
            TAG,
            "Parando fala..."
        )

        pendingSpeechText = null

        try {

            textToSpeech?.stop()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao parar TTS: ${e.message}"
            )
        }

        updateState(
            "idle"
        )
    }

    // ============================================================
    // BEEP
    // ============================================================

    private fun playBeepSound() {

        try {

            val toneGen =
                ToneGenerator(
                    AudioManager.STREAM_NOTIFICATION,
                    80
                )

            toneGen.startTone(
                ToneGenerator.TONE_PROP_BEEP,
                150
            )

            toneGen.release()

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao reproduzir áudio de beep: ${e.message}"
            )
        }
    }

    // ============================================================
    // ESTADO
    // ============================================================

    private fun updateState(
        state: String,
        text: String = ""
    ) {

        currentState =
            state

        stateListener?.onJarvisStateChanged(
            state,
            text
        )
    }

    // ============================================================
    // LISTENER DA ACTIVITY
    // ============================================================

    fun setListener(
        listener: ServiceStateListener?
    ) {

        stateListener =
            listener

        stateListener?.onJarvisStateChanged(
            currentState,
            ""
        )
    }

    // ============================================================
    // WEBSOCKET CONNECTED
    // ============================================================

    override fun onConnected() {

        Log.d(
            TAG,
            "WebSocket Jarvis online."
        )

        stateListener?.onConnectionStatusChanged(
            true
        )

        updateState(
            "idle",
            "Jarvis Conectado"
        )
    }

    // ============================================================
    // WEBSOCKET DISCONNECTED
    // ============================================================

    override fun onDisconnected() {

        Log.w(
            TAG,
            "WebSocket Jarvis desconectado."
        )

        stateListener?.onConnectionStatusChanged(
            false
        )

        updateState(
            "idle",
            "Jarvis Offline"
        )
    }

    // ============================================================
    // MENSAGEM DO PYTHON
    // ============================================================

    override fun onMessageReceived(
        message: String
    ) {

        try {

            val json =
                JSONObject(
                    message
                )

            if (
                json.has("type") &&
                json.getString("type") == "state"
            ) {

                val state =
                    json.optString(
                        "state",
                        "idle"
                    )

                val text =
                    json.optString(
                        "text",
                        ""
                    )

                when (state) {

                    "speaking" -> {

                        if (
                            text.isNotBlank()
                        ) {

                            speakText(
                                text
                            )
                        }
                    }

                    "thinking" -> {

                        updateState(
                            "thinking",
                            text
                        )
                    }

                    "listening" -> {

                        updateState(
                            "listening",
                            text
                        )
                    }

                    "idle" -> {

                        /*
                         * O Python não deve interromper
                         * o TTS Android mandando idle.
                         *
                         * O próprio UtteranceProgressListener
                         * coloca o estado em idle quando
                         * a fala realmente terminar.
                         */
                    }

                    else -> {

                        updateState(
                            state,
                            text
                        )
                    }
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao decodificar JSON do WebSocket: ${e.message}",
                e
            )
        }
    }

    // ============================================================
    // WEBSOCKET ERROR
    // ============================================================

    override fun onError(
        error: String
    ) {

        Log.e(
            TAG,
            "Erro do WebSocket: $error"
        )
    }

    // ============================================================
    // NOTIFICATION CHANNEL
    // ============================================================

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Jarvis Service",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {

                    description =
                        "Serviço contínuo do assistente pessoal Jarvis"
                }

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    // ============================================================
    // FOREGROUND NOTIFICATION
    // ============================================================

    private fun startForegroundServiceWithNotification() {

        val notificationIntent =
            Intent(
                this,
                MainActivity::class.java
            )

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT
            )

        val notification: Notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle(
                    "Jarvis Ativo"
                )
                .setContentText(
                    "Toque para abrir a interface do assistente"
                )
                .setSmallIcon(
                    R.drawable.ic_launcher_foreground
                )
                .setContentIntent(
                    pendingIntent
                )
                .setOngoing(
                    true
                )
                .setPriority(
                    NotificationCompat.PRIORITY_LOW
                )
                .build()

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    // ============================================================
    // WAKE LOCK
    // ============================================================

    private fun acquireWakeLock() {

        try {

            val powerManager =
                getSystemService(
                    Context.POWER_SERVICE
                ) as PowerManager

            wakeLock =
                powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Jarvis::ServiceWakeLock"
                ).apply {

                    acquire(
                        10 * 60 * 1000L
                    )
                }

            Log.d(
                TAG,
                "WakeLock mantido."
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao solicitar WakeLock: ${e.message}",
                e
            )
        }
    }

    private fun releaseWakeLock() {

        try {

            if (
                wakeLock?.isHeld == true
            ) {

                wakeLock?.release()
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao liberar WakeLock: ${e.message}",
                e
            )
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        Log.d(
            TAG,
            "Encerrando JarvisForegroundService..."
        )

        try {
            wakeWordManager.stop()
        } catch (_: Exception) {}

        try {
            speechManager.destroy()
        } catch (_: Exception) {}

        try {
            webSocketManager.disconnect()
        } catch (_: Exception) {}

        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (_: Exception) {}

        textToSpeech = null

        releaseWakeLock()

        instance = null

        super.onDestroy()
    }
}
