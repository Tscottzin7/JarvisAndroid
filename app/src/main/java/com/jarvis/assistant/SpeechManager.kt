package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class SpeechManager(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStateChange: (Boolean) -> Unit
) : RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "SpeechManager"
    }

    fun startListening() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                val msg = "Reconhecimento de voz não suportado neste aparelho."
                Log.e(TAG, msg)
                onError(msg)
                return@post
            }

            destroyRecognizer()

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(this@SpeechManager)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_PATH_OBSERVED, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            try {
                speechRecognizer?.startListening(intent)
                onStateChange(true)
                Log.d(TAG, "SpeechRecognizer iniciado (pt-BR).")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar SpeechRecognizer: ${e.message}", e)
                onError("Erro ao ativar microfone.")
                onStateChange(false)
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao parar SpeechRecognizer: ${e.message}", e)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            destroyRecognizer()
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao destruir SpeechRecognizer: ${e.message}", e)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "Aguardando voz...")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "Início da fala detectado.")
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "Fim da fala.")
        onStateChange(false)
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Erro de captura de áudio."
            SpeechRecognizer.ERROR_CLIENT -> "Erro interno no cliente de voz."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão de microfone negada."
            SpeechRecognizer.ERROR_NETWORK -> "Erro de conexão de rede."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo limite de rede excedido."
            SpeechRecognizer.ERROR_NO_MATCH -> "Nenhum comando reconhecido."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Serviço de voz ocupado."
            SpeechRecognizer.ERROR_SERVER -> "Erro do servidor de reconhecimento."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nenhuma fala detectada."
            else -> "Erro desconhecido ($error)."
        }
        Log.e(TAG, "SpeechRecognizer erro $error:$errorMessage")
        onStateChange(false)
        onError(errorMessage)
    }

    override fun onResults(results: Bundle?) {
        onStateChange(false)
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val text = matches[0]
            Log.d(TAG, "Texto reconhecido: $text")
            onResult(text)
        } else {
            Log.w(TAG, "Resultados vazios retornado pelo SpeechRecognizer.")
            onError("Fala não compreendida.")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
