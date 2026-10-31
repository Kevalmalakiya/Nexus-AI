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

class GeminiProvider(private val apiKey: String, preferred: String = "") : AiProvider {
    private val models = (listOfNotNull(preferred.trim().takeIf { it.isNotEmpty() }) +
        listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-flash-lite-latest")).distinct()

    private suspend fun <T> tryModels(block: (String) -> T): Result<T> = withContext(Dispatchers.IO) {
        var last: Throwable = IOException("Koi model available nahi")
        for (m in models) {
            for (attempt in 0..1) {
                val r = runCatching { block(m) }
                if (r.isSuccess) return@withContext r
                val e = r.exceptionOrNull()!!
                last = e
                val code = (e as? GeminiHttpException)?.code
                if (code == 503 || code == 429) {
                    if (attempt == 0) delay(800)
                    continue
                }
                if (code == 404) break
                return@withContext Result.failure(e)
            }
        }
        Result.failure(last)
    }

    override suspend fun generate(system: String, history: List<ChatTurn>): Result<String> =
        tryModels { call(it, system, history) }

    override suspend fun stream(system: String, history: List<ChatTurn>, onChunk: (String) -> Unit): Result<String> =
        tryModels { callStream(it, system, history, onChunk) }

    private fun body(system: String, history: List<ChatTurn>, fast: Boolean): JSONObject {
        val contents = JSONArray()
        history.forEach {
            contents.put(JSONObject()
                .put("role", if (it.fromUser) "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", it.text))))
        }
        val b = JSONObject()
            .put("system_instruction", JSONObject()
                .put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        if (fast) b.put("generationConfig", JSONObject()
            .put("maxOutputTokens", 400)
            .put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
        return b
    }

    private fun open(model: String, method: String, body: JSONObject): HttpURLConnection {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:$method")
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
        return conn
    }

    private fun call(model: String, system: String, history: List<ChatTurn>): String {
        val conn = open(model, "generateContent", body(system, history, false))
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(text).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text")
    }

    private fun callStream(model: String, system: String, history: List<ChatTurn>, onChunk: (String) -> Unit): String {
        val method = "streamGenerateContent?alt=sse"
        val conn = try {
            open(model, method, body(system, history, true))
        } catch (e: GeminiHttpException) {
            if (e.code == 400) open(model, method, body(system, history, false)) else throw e
        }
        val all = StringBuilder()
        conn.inputStream.bufferedReader().use { r ->
            r.forEachLine { line ->
                if (line.startsWith("data:")) {
                    val j = runCatching { JSONObject(line.removePrefix("data:").trim()) }.getOrNull()
                        ?: return@forEachLine
                    val parts = j.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts") ?: return@forEachLine
                    for (i in 0 until parts.length()) {
                        val p = parts.getJSONObject(i)
                        if (p.optBoolean("thought", false)) continue
                        val t = p.optString("text", "")
                        if (t.isNotEmpty()) { all.append(t); onChunk(t) }
                    }
                }
            }
        }
        if (all.isEmpty()) throw IOException("Khali jawab mila")
        return all.toString()
    }
}
