package com.nexus.ai.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GeminiProvider(
    private val apiKey: String,
    private val model: String = "gemini-flash-latest" // alias; change if your key needs another model
) : AiProvider {

    override suspend fun generate(system: String, history: List<ChatTurn>): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
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
                    throw IOException("Gemini HTTP $code: ${msg.take(160)}")
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                JSONObject(text).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                    .getString("text")
            }
        }
}
