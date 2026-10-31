package com.nexus.ai.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/** Phone actions Gemini may request via function calling. Only safe, user-visible actions. */
object DeviceActions {

    fun declarations(): JSONArray = JSONArray().put(
        JSONObject().put("functionDeclarations", JSONArray()
            .put(fn("open_app",
                "Open an installed app. Pass the app name in English as shown on the home screen, e.g. WhatsApp, YouTube, Camera.",
                mapOf("name" to "App name in English")))
            .put(fn("search_youtube",
                "Open YouTube search results for a song or video. This does not start playback by itself.",
                mapOf("query" to "What to search for")))
            .put(fn("open_settings",
                "Open Android settings. section can be wifi, bluetooth, display, sound, battery, apps or location; empty for main settings.",
                mapOf("section" to "Settings section"), required = emptyList()))
        )
    )

    private fun fn(name: String, desc: String, props: Map<String, String>,
                   required: List<String> = props.keys.toList()): JSONObject {
        val p = JSONObject()
        props.forEach { (k, v) -> p.put(k, JSONObject().put("type", "STRING").put("description", v)) }
        return JSONObject().put("name", name).put("description", desc)
            .put("parameters", JSONObject().put("type", "OBJECT").put("properties", p)
                .put("required", JSONArray(required)))
    }

    fun execute(ctx: Context, name: String, args: JSONObject): String = try {
        when (name) {
            "open_app" -> openApp(ctx, args.optString("name"))
            "search_youtube" -> searchYoutube(ctx, args.optString("query"))
            "open_settings" -> openSettings(ctx, args.optString("section"))
            else -> "Unknown action: $name"
        }
    } catch (e: Exception) {
        "Failed: ${e.javaClass.simpleName}"
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
        try {
            ctx.startActivity(Intent(i).setPackage("com.google.android.youtube"))
        } catch (_: Exception) {
            ctx.startActivity(i)
        }
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
}
