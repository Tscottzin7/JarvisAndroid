package com.jarvis.assistant

import android.webkit.JavascriptInterface

class AndroidBridge(
    private val onTriggerMic: () -> Unit,
    private val onSendPrompt: (String) -> Unit
) {

    @JavascriptInterface
    fun startSpeechRecognition() {

        onTriggerMic()
    }

    @JavascriptInterface
    fun startListening() {

        onTriggerMic()
    }

    @JavascriptInterface
    fun sendPrompt(text: String) {

        onSendPrompt(text)
    }
}
