package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // Clica em um elemento que contenha o texto informado
    fun clickByText(text: String, exact: Boolean = false): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)

        for (node in nodes) {
            if (exact && node.text?.toString().equals(text, ignoreCase = true) || !exact) {
                var current: AccessibilityNodeInfo? = node
                while (current != null) {
                    if (current.isClickable) {
                        return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    current = current.parent
                }
            }
        }
        return false
    }

    // Digita texto em um campo focado ou especificado
    fun typeTextInInput(text: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false

        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // Extrai todos os textos visíveis na tela atual
    fun readScreenText(): List<String> {
        val textList = mutableListOf<String>()
        val rootNode = rootInActiveWindow ?: return textList

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (!node.text.isNullOrBlank()) {
                textList.add(node.text.toString())
            }
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)
        return textList
    }

    // Ações globais (Voltar, Home)
    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)
}
