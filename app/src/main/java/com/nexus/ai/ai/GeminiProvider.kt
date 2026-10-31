package com.nexus.ai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiHttpException(val code: Int, msg: String) : IOException(msg)

/** Tries the custom model first, retries once on 503/429, then falls back. */
class GeminiProvider(private val apiKey: String, preferred: String = "") : AiProvider {
    private val models = (listOfNotNull(preferred.trim().takeIf { it.isNotEmpty() }) +
        listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-flash-lite-latest")).distinct()

    override suspend fun generate(system: String, history: List<ChatTurn>): Result<String> =
        withContext(Dispatchers.IO) {
            var last: Throwable = IOException("Koi model available nahi")
            for (m in models) {
                for (attempt in 0..1) {
                    val r = runCatching { call(m, system, history) }
                    if (r.isSuccess) return@withContext r
                    val e = r.exceptionOrNull()!!
                    last = e
                    val code = (e as? GeminiHttpException)?.code
                    if (code == 503 || code == 429) {
                        if (attempt == 0) delay(1200)
                        continue
                    }
                    if (code == 404) break
                    return@withContext Result.failure(e)
                }
            }
            Result.failure(last)
        }

    private fun call(model: String, system: String, history: List<ChatTurn>): String {
        val contents = JSONArray()
        history.forEach {
            contents.put(JSONObject()
                .put("role", if (it.fromUser) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", it.text))))
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject()
                .put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault("")
            throw GeminiHttpException(code, "Gemini HTTP $code: ${msg.take(160)}")
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(text).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text")
    }
}
