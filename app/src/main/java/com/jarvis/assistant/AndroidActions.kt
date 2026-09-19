package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import java.util.Calendar

class AndroidActions(private val context: Context) {

    fun executeAction(action: String, params: Map<String, Any>): Boolean {
        return when (action) {
            // --- SKILLS NATIVAS VIA INTENTS ---
            "create_alarm" -> {
                val hour = (params["hour"] as? Number)?.toInt() ?: return false
                val minutes = (params["minutes"] as? Number)?.toInt() ?: 0
                val message = params["message"] as? String ?: "Alarme Jarvis"

                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                    putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }

            "open_app" -> {
                val packageName = params["package_name"] as? String ?: return false
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    true
                } else false
            }

            // --- SKILLS VIA ACCESSIBILITY SERVICE ---
            "click_text" -> {
                val text = params["text"] as? String ?: return false
                JarvisAccessibilityService.instance?.clickByText(text) ?: false
            }

            "type_text" -> {
                val text = params["text"] as? String ?: return false
                JarvisAccessibilityService.instance?.typeTextInInput(text) ?: false
            }

            "press_back" -> JarvisAccessibilityService.instance?.pressBack() ?: false
            "press_home" -> JarvisAccessibilityService.instance?.pressHome() ?: false

            else -> false
        }
    }
}
