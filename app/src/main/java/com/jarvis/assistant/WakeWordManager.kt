package com.jarvis.assistant

import android.content.Context
import android.util.Log
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback

class WakeWordManager(
    private val context: Context,
    private val accessKey: String,
    private val onWakeWordDetected: () -> Unit
) {
    private var porcupineManager: PorcupineManager? = null
    private var isListening = false

    companion object {
        private const val TAG = "WakeWordManager"
    }

    fun start() {
        if (isListening) return
        if (accessKey.isBlank() || accessKey == "SUA_ACCESS_KEY_AQUI") {
            Log.w(TAG, "AccessKey do Porcupine ausente. Wake Word desativada. Use o botão do microfone para testes.")
            return
        }

        try {
            val callback = PorcupineManagerCallback { keywordIndex ->
                if (keywordIndex == 0) {
                    Log.d(TAG, "Wake Word 'Jarvis' detectada com sucesso!")
                    onWakeWordDetected()
                }
            }

            val builder = PorcupineManager.Builder()
                .setAccessKey(accessKey)
                .setSensitivity(0.7f)

            val hasCustomModel = try {
                context.assets.list("")?.contains("jarvis.ppn") == true
            } catch (e: Exception) {
                false
            }

            if (hasCustomModel) {
                builder.setKeywordPath("jarvis.ppn")
            } else {
                builder.setKeyword(Porcupine.BuiltInKeyword.JARVIS)
            }

            porcupineManager = builder.build(context, callback)
            porcupineManager?.start()
            isListening = true
            Log.d(TAG, "Porcupine ativado com sucesso.")
        } catch (e: PorcupineException) {
            Log.e(TAG, "Erro de inicialização do Porcupine: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Erro inesperado em WakeWordManager: ${e.message}", e)
        }
    }

    fun stop() {
        if (!isListening) return
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
            porcupineManager = null
            isListening = false
            Log.d(TAG, "Porcupine pausado/interrompido.")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao interromper Porcupine: ${e.message}", e)
        }
    }
}
