package com.nexus.ai.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/** Phone tools Gemini may call. Sensitive ones are gated by the ViewModel (verbal yes). */
object DeviceActions {

    fun declarations(): JSONArray = JSONArray().put(
        JSONObject().put("functionDeclarations", JSONArray()
            .put(fn("open_app", "Open an installed app by its English name, e.g. WhatsApp, Instagram, YouTube, Termux.",
                mapOf("name" to "App name")))
            .put(fn("search_youtube", "Open YouTube search results. Does not start playback.",
                mapOf("query" to "Search text")))
            .put(fn("open_settings", "Open Android settings: wifi, bluetooth, display, sound, battery, apps, location, or empty.",
                mapOf("section" to "Section"), required = emptyList()))
            .put(fn("read_screen", "Read the visible text and tappable elements of the current screen.", emptyMap()))
            .put(fn("click", "Tap a visible element whose text or description matches.",
                mapOf("text" to "Visible text of the element")))
            .put(fn("type_text", "Type text into the focused or first text field on screen.",
                mapOf("text" to "Text to type")))
            .put(fn("scroll", "Scroll or swipe: down/next moves forward (e.g. next reel), up/previous moves back.",
                mapOf("direction" to "down, up, next or previous")))
            .put(fn("press", "Press a system key: back, home or recents.", mapOf("key" to "back, home or recents")))
            .put(fn("wait", "Wait 1-5 seconds for a screen to load.", mapOf("seconds" to "Seconds"), required = emptyList()))
            .put(fn("send_sms", "Send an SMS. ONLY after the user clearly said yes to the exact text and recipient.",
                mapOf("to" to "Phone number or contact name", "message" to "Message text")))
            .put(fn("remember", "Save a lasting fact or preference about the user. Never passwords, OTPs or card numbers.",
                mapOf("fact" to "The fact", "category" to "preference, person, project, routine or general"),
                required = listOf("fact")))
            .put(fn("recall", "Search saved memories.", mapOf("query" to "Search text")))
            .put(fn("forget", "Delete saved memories matching the text.", mapOf("query" to "Search text")))
        )
    )

    private fun fn(name: String, desc: String, props: Map<String, String>,
                   required: List<String> = props.keys.toList()): JSONObject {
        val o = JSONObject().put("name", name).put("description", desc)
        if (props.isNotEmpty()) {
            val p = JSONObject()
            props.forEach { (k, v) -> p.put(k, JSONObject().put("type", "STRING").put("description", v)) }
            o.put("parameters", JSONObject().put("type", "OBJECT").put("properties", p)
                .put("required", JSONArray(required)))
        }
        return o
    }

    fun execute(ctx: Context, name: String, args: JSONObject): String = try {
        when (name) {
            "open_app" -> openApp(ctx, args.optString("name"))
            "search_youtube" -> searchYoutube(ctx, args.optString("query"))
            "open_settings" -> openSettings(ctx, args.optString("section"))
            "send_sms" -> sendSms(ctx, args.optString("to"), args.optString("message"))
            "wait" -> {
                val s = (args.optString("seconds").toDoubleOrNull() ?: 1.0).coerceIn(0.5, 5.0)
                Thread.sleep((s * 1000).toLong()); "Waited ${s}s"
            }
            "read_screen", "click", "type_text", "scroll", "press" -> screenTool(name, args)
            else -> "Unknown action: $name"
        }
    } catch (e: Exception) {
        "Failed: ${e.javaClass.simpleName}"
    }

    private fun screenTool(name: String, args: JSONObject): String {
        val svc = NexusAccessibilityService.instance
            ?: return "Accessibility is OFF. Ask the user to enable NEXUS AI in Settings > Accessibility."
        return when (name) {
            "read_screen" -> svc.readScreen()
            "click" -> svc.click(args.optString("text"))
            "type_text" -> svc.typeText(args.optString("text"))
            "scroll" -> svc.scroll(args.optString("direction"))
            else -> svc.press(args.optString("key"))
        }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun openApp(ctx: Context, name: String): String {
        val q = norm(name)
        if (q.isEmpty()) return "No app name given"
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
        val hit = apps.firstOrNull { norm(it.first) == q }
            ?: apps.firstOrNull { norm(it.first).startsWith(q) }
            ?: apps.firstOrNull { norm(it.first).contains(q) }
            ?: return "No installed app matches '$name'"
        val launch = pm.getLaunchIntentForPackage(hit.second) ?: return "Cannot launch ${hit.first}"
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launch)
        return "Opened ${hit.first}"
    }

    private fun searchYoutube(ctx: Context, query: String): String {
        if (query.isBlank()) return "No search text given"
        val uri = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))
        val i = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(Intent(i).setPackage("com.google.android.youtube")) }
        catch (_: Exception) { ctx.startActivity(i) }
        return "Opened YouTube search for '$query'. Playback is NOT started or verified; the user must tap a result."
    }

    private fun openSettings(ctx: Context, section: String): String {
        val action = when (section.lowercase().trim()) {
            "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "sound" -> Settings.ACTION_SOUND_SETTINGS
            "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps" -> Settings.ACTION_APPLICATION_SETTINGS
            "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Opened settings (${section.ifBlank { "main" }})"
    }

    private fun granted(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun findNumber(ctx: Context, name: String): String? {
        if (!granted(ctx, Manifest.permission.READ_CONTACTS)) return null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        return null
    }

    private fun sendSms(ctx: Context, to: String, message: String): String {
        if (message.isBlank()) return "Empty message"
        if (!granted(ctx, Manifest.permission.SEND_SMS))
            return "SMS permission is not granted. Ask the user to allow it in NEXUS Settings > Permissions."
        val raw = to.trim()
        val digits = raw.replace(Regex("[\\s-]"), "")
        val number = if (Regex("^\\+?[0-9]{7,15}$").matches(digits)) digits
        else findNumber(ctx, raw) ?: return "No contact or number found for '$to' (contacts permission may be off)"
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        sm.sendMultipartTextMessage(number, null, sm.divideMessage(message), null, null)
        return "SMS submitted to $number. Delivery is not verified."
    }
}
