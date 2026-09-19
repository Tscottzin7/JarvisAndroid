package com.jarvis.assistant

import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val url: String = "ws://127.0.0.1:8765",
    private val listener: WebSocketMessageListener
) {
    interface WebSocketMessageListener {
        fun onConnected()
        fun onDisconnected()
        fun onMessageReceived(message: String)
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var isConnecting = false
    private val handler = Handler(Looper.getMainLooper())

    private val reconnectRunnable = Runnable {
        connect()
    }

    companion object {
        private const val TAG = "WebSocketManager"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    fun connect() {
        if (isConnected || isConnecting) return
        isConnecting = true
        Log.d(TAG, "Conectando ao Jarvis Core em $url...")

        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                isConnecting = false
                Log.d(TAG, "WebSocket conectado com sucesso.")
                handler.post { listener.onConnected() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Mensagem do servidor Python: $text")
                handler.post { listener.onMessageReceived(text) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket fechando: $reason")
                isConnected = false
                isConnecting = false
                handler.post { listener.onDisconnected() }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket fechado: $reason")
                isConnected = false
                isConnecting = false
                handler.post { listener.onDisconnected() }
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Falha de conexão WebSocket: ${t.localizedMessage}")
                isConnected = false
                isConnecting = false
                handler.post { listener.onError(t.localizedMessage ?: "Erro de conexão WebSocket") }
                handler.post { listener.onDisconnected() }
                scheduleReconnect()
            }
        })
    }

    fun sendPrompt(text: String) {
        if (!isConnected || webSocket == null) {
            Log.e(TAG, "Envio cancelado: WebSocket offline.")
            listener.onError("Jarvis offline. Inicie o servidor no Termux.")
            return
        }

        try {
            val json = JSONObject().apply {
                put("type", "user_prompt")
                put("text", text)
            }
            val jsonString = json.toString()
            webSocket?.send(jsonString)
            Log.d(TAG, "Comando enviado ao Termux: $jsonString")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao construir mensagem JSON: ${e.message}", e)
        }
    }

    private fun scheduleReconnect() {
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    fun disconnect() {
        handler.removeCallbacks(reconnectRunnable)
        isConnected = false
        isConnecting = false
        try {
            webSocket?.close(1000, "Serviço destruído")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fechar WebSocket: ${e.message}")
        }
        webSocket = null
    }
}
