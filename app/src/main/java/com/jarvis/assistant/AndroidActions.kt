package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log

class AndroidActions(private val context: Context) {

    companion object {
        private const val TAG = "AndroidActions"
    }

    fun executeAction(
        action: String,
        params: Map<String, Any>
    ): Boolean {

        Log.d(
            TAG,
            "Executando ação: $action | params=$params"
        )

        return when (action) {

            // ============================================================
            // ABRIR APLICATIVO
            // ============================================================

            "open_app" -> {
                val packageName =
                    params["package_name"] as? String
                        ?: return false

                openApp(packageName)
            }

            // ============================================================
            // ALARME
            // ============================================================

            "create_alarm" -> {
                val hour =
                    (params["hour"] as? Number)?.toInt()
                        ?: return false

                val minutes =
                    (params["minutes"] as? Number)?.toInt()
                        ?: 0

                val message =
                    params["message"] as? String
                        ?: "Alarme Jarvis"

                createAlarm(
                    hour,
                    minutes,
                    message
                )
            }

            // ============================================================
            // ACCESSIBILITY
            // ============================================================

            "click_text" -> {
                val text =
                    params["text"] as? String
                        ?: return false

                val exact =
                    params["exact"] as? Boolean
                        ?: false

                JarvisAccessibilityService
                    .instance
                    ?.clickByText(text, exact)
                    ?: false
            }

            "click_description" -> {
                val description =
                    params["description"] as? String
                        ?: return false

                JarvisAccessibilityService
                    .instance
                    ?.clickByDescription(description)
                    ?: false
            }

            "type_text" -> {
                val text =
                    params["text"] as? String
                        ?: return false

                JarvisAccessibilityService
                    .instance
                    ?.typeTextInInput(text)
                    ?: false
            }

            "replace_text" -> {
                val text =
                    params["text"] as? String
                        ?: return false

                JarvisAccessibilityService
                    .instance
                    ?.replaceTextInInput(text)
                    ?: false
            }

            "press_back" -> {
                JarvisAccessibilityService
                    .instance
                    ?.pressBack()
                    ?: false
            }

            "press_home" -> {
                JarvisAccessibilityService
                    .instance
                    ?.pressHome()
                    ?: false
            }

            else -> {
                Log.w(
                    TAG,
                    "Ação desconhecida: $action"
                )
                false
            }
        }
    }

    // ================================================================
    // ABRIR APP
    // ================================================================

    private fun openApp(packageName: String): Boolean {

        return try {

            val intent =
                context.packageManager
                    .getLaunchIntentForPackage(packageName)

            if (intent == null) {

                Log.e(
                    TAG,
                    "Aplicativo não encontrado: $packageName"
                )

                return false
            }

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )

            context.startActivity(intent)

            Log.d(
                TAG,
                "Aplicativo aberto: $packageName"
            )

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao abrir $packageName: ${e.message}",
                e
            )

            false
        }
    }

    // ================================================================
    // ALARME
    // ================================================================

    private fun createAlarm(
        hour: Int,
        minutes: Int,
        message: String
    ): Boolean {

        return try {

            val intent =
                Intent(AlarmClock.ACTION_SET_ALARM).apply {

                    putExtra(
                        AlarmClock.EXTRA_HOUR,
                        hour
                    )

                    putExtra(
                        AlarmClock.EXTRA_MINUTES,
                        minutes
                    )

                    putExtra(
                        AlarmClock.EXTRA_MESSAGE,
                        message
                    )

                    putExtra(
                        AlarmClock.EXTRA_SKIP_UI,
                        true
                    )

                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

            context.startActivity(intent)

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Erro ao criar alarme: ${e.message}",
                e
            )

            false
        }
    }
}
