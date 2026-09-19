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
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject

class JarvisForegroundService : Service(), WebSocketManager.WebSocketMessageListener {

    interface ServiceStateListener {
        fun onJarvisStateChanged(state: String, text: String)
        fun onConnectionStatusChanged(isConnected: Boolean)
    }

    private val binder = LocalBinder()
    private var stateListener: ServiceStateListener? = null

    private lateinit var webSocketManager: WebSocketManager
    private lateinit var wakeWordManager: WakeWordManager
    private lateinit var speechManager: SpeechManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var currentState: String = "idle"

    private val porcupineAccessKey: String = "SUA_ACCESS_KEY_AQUI"

    companion object {
        private const val TAG = "JarvisService"
        private const val CHANNEL_ID = "jarvis_service_channel"
        private const val NOTIFICATION_ID = 1001

        var instance: JarvisForegroundService? = null
            private set
    }

    inner class LocalBinder : Binder() {
        fun getService(): JarvisForegroundService = this@JarvisForegroundService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun setListener(listener: ServiceStateListener?) {
        this.stateListener = listener
        stateListener?.onJarvisStateChanged(currentState, "")
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "Iniciando JarvisForegroundService...")

        acquireWakeLock()
        createNotificationChannel()
        startForegroundServiceWithNotification()

        webSocketManager = WebSocketManager("ws://127.0.0.1:8765", this)
        webSocketManager.connect()

        speechManager = SpeechManager(
            context = this,
            onResult = { recognizedText ->
                Log.d(TAG, "Comando de voz: $recognizedText")
                updateState("thinking", recognizedText)
                webSocketManager.sendPrompt(recognizedText)
                wakeWordManager.start()
            },
            onError = { errorMsg ->
                Log.e(TAG, "Erro SpeechRecognizer: $errorMsg")
                updateState("idle", errorMsg)
                wakeWordManager.start()
            },
            onStateChange = { isListening ->
                if (isListening) {
                    updateState("listening")
                }
            }
        )

        wakeWordManager = WakeWordManager(
            context = this,
            accessKey = porcupineAccessKey,
            onWakeWordDetected = {
                Log.d(TAG, "Wake Word ativada!")
                playBeepSound()
                wakeWordManager.stop()
                speechManager.startListening()
            }
        )
        wakeWordManager.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    fun startManualListening() {
        Log.d(TAG, "Escuta manual solicitada via UI...")
        wakeWordManager.stop()
        speechManager.startListening()
    }

    fun sendPrompt(text: String) {
        updateState("thinking", text)
        webSocketManager.sendPrompt(text)
    }

    private fun playBeepSound() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao reproduzir áudio de beep: ${e.message}")
        }
    }

    private fun updateState(state: String, text: String = "") {
        this.currentState = state
        stateListener?.onJarvisStateChanged(state, text)
    }

    override fun onConnected() {
        Log.d(TAG, "WebSocket Jarvis online.")
        stateListener?.onConnectionStatusChanged(true)
        updateState("idle", "Jarvis Conectado")
    }

    override fun onDisconnected() {
        Log.w(TAG, "WebSocket Jarvis desconectado.")
        stateListener?.onConnectionStatusChanged(false)
        updateState("idle", "Jarvis Offline")
    }

    override fun onMessageReceived(message: String) {
        try {
            val json = JSONObject(message)
            if (json.has("type") && json.getString("type") == "state") {
                val state = json.optString("state", "idle")
                val text = json.optString("text", "")
                updateState(state, text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao decodificar JSON do WebSocket: ${e.message}", e)
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, "Erro do WebSocket: $error")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Serviço contínuo do assistente pessoal Jarvis"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Jarvis Ativo")
            .setContentText("Toque para abrir a interface do assistente")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Jarvis::ServiceWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L)
            }
            Log.d(TAG, "WakeLock mantido.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao solicitar WakeLock: ${e.message}", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock liberado.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao liberar WakeLock: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Encerrando JarvisForegroundService...")
        wakeWordManager.stop()
        speechManager.destroy()
        webSocketManager.disconnect()
        releaseWakeLock()
        instance = null
        super.onDestroy()
    }
}
