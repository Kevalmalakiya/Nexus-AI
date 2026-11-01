package com.nexus.ai.voice

import android.util.Base64
import org.json.JSONObject

/** Sends one JPEG screenshot into the live session (only after setup is complete). */
fun LiveVoice.sendFrame(jpeg: ByteArray): Boolean {
    if (!audioStarted) return false
    val b64 = Base64.encodeToString(jpeg, Base64.NO_WRAP)
    return ws?.send("""{"realtimeInput":{"video":{"data":"$b64","mimeType":"image/jpeg"}}}""") ?: false
}

fun LiveVoice.sendText(t: String): Boolean {
    if (!audioStarted) return false
    return ws?.send(JSONObject().put("realtimeInput", JSONObject().put("text", t)).toString()) ?: false
}
