package com.jarvis.assistant

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
        private const val TAG = "JarvisAccessibility"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Serviço de acessibilidade conectado.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Mantemos os eventos disponíveis para futuras skills.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Serviço de acessibilidade interrompido.")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /**
     * Clica em um elemento que contenha o texto informado.
     */
    fun clickByText(text: String, exact: Boolean = false): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.w(TAG, "Não foi possível obter a tela atual.")
            return false
        }

        val nodes = rootNode.findAccessibilityNodeInfosByText(text)

        for (node in nodes) {
            val nodeText = node.text?.toString() ?: ""

            val matches = if (exact) {
                nodeText.equals(text, ignoreCase = true)
            } else {
                nodeText.contains(text, ignoreCase = true)
            }

            if (!matches) continue

            var current: AccessibilityNodeInfo? = node

            while (current != null) {
                if (current.isClickable && current.isEnabled) {
                    val result = current.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )

                    Log.d(
                        TAG,
                        "Clique em '$text': $result"
                    )

                    return result
                }

                current = current.parent
            }
        }

        Log.w(TAG, "Elemento com texto '$text' não encontrado.")
        return false
    }

    /**
     * Clica em um elemento pela contentDescription.
     */
    fun clickByDescription(description: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false

        val nodes = rootNode.findAccessibilityNodeInfosByText(description)

        for (node in nodes) {
            var current: AccessibilityNodeInfo? = node

            while (current != null) {
                val currentDescription =
                    current.contentDescription?.toString()

                if (
                    currentDescription.equals(
                        description,
                        ignoreCase = true
                    ) &&
                    current.isClickable &&
                    current.isEnabled
                ) {
                    return current.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )
                }

                current = current.parent
            }
        }

        return false
    }

    /**
     * Digita texto no campo atualmente focado.
     */
    fun typeTextInInput(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: run {
            Log.w(TAG, "Tela atual indisponível para digitação.")
            return false
        }

        val focusedNode =
            rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode == null) {
            Log.w(TAG, "Nenhum campo de entrada está focado.")
            return false
        }

        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )
        }

        val result = focusedNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            args
        )

        Log.d(TAG, "Texto digitado: $result")

        return result
    }

    /**
     * Limpa e depois digita texto no campo focado.
     */
    fun replaceTextInInput(text: String): Boolean {
        return typeTextInInput(text)
    }

    /**
     * Extrai todos os textos visíveis da tela.
     */
    fun readScreenText(): List<String> {
        val textList = mutableListOf<String>()
        val rootNode = rootInActiveWindow ?: return textList

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return

            node.text
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { textList.add(it) }

            node.contentDescription
                ?.toString()
                ?.takeIf { it.isNotBlank() }
                ?.let { textList.add(it) }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)

        return textList.distinct()
    }

    /**
     * Retorna se o serviço de acessibilidade está ativo.
     */
    fun isReady(): Boolean {
        return rootInActiveWindow != null
    }

    /**
     * Ações globais do Android.
     */
    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
