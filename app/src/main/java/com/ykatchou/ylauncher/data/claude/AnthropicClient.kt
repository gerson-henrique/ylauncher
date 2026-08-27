package com.ykatchou.ylauncher.data.claude

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** Raised when the Messages API returns a non-2xx; [message] is the API's own error text. */
class ClaudeApiException(message: String) : Exception(message)

/** One tool call the model asked for — a shell command to run on the device. */
data class ToolUse(val id: String, val command: String)

/**
 * One turn of the model's reply. [content] is the raw assistant `content` array, echoed back
 * verbatim on the next request so thinking and tool_use blocks survive unchanged (the loop stays on
 * one model, which is the only time that is valid). [texts] and [toolUses] are the parsed views the
 * UI and the loop actually use.
 */
data class ClaudeTurn(
    val stopReason: String,
    val content: JSONArray,
    val texts: List<String>,
    val toolUses: List<ToolUse>,
)

/**
 * Talks to the Anthropic Messages API by hand — one HTTP call, JSON built and parsed with org.json,
 * the same shape the weather readout uses. No SDK and no HTTP client library on purpose: this app's
 * whole point is staying light, and a launcher that already hand-rolls its one other network call
 * has no business pulling in Jackson and OkHttp for a second.
 *
 * Adaptive thinking stays on: with thinking disabled, Opus can emit a tool call as plain text that
 * then silently never runs — unacceptable when the entire feature is running tools. Effort is capped
 * at medium to keep a phone turn responsive.
 */
@Singleton
class AnthropicClient @Inject constructor() {

    /**
     * Sends the running conversation and returns the model's turn. [messages] is the wire message
     * array the caller owns (it appends the assistant turn and tool results between calls); this
     * only layers on model, tools and the sampling config.
     */
    suspend fun send(apiKey: String, workspaceId: String?, system: String, messages: JSONArray): ClaudeTurn =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", MODEL)
                .put("max_tokens", MAX_TOKENS)
                .put("system", system)
                .put("thinking", JSONObject().put("type", "adaptive"))
                .put("output_config", JSONObject().put("effort", "medium"))
                .put("tools", JSONArray().put(shellTool()))
                .put("messages", messages)

            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("content-type", "application/json")
                setRequestProperty("x-api-key", apiKey)
                setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
                // Identity-linked keys are rejected without the workspace they act in.
                if (!workspaceId.isNullOrBlank()) setRequestProperty("anthropic-workspace-id", workspaceId)
            }

            val response = try {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw ClaudeApiException(errorMessage(text, code))
                text
            } finally {
                conn.disconnect()
            }

            parse(JSONObject(response))
        }

    private fun parse(root: JSONObject): ClaudeTurn {
        val content = root.optJSONArray("content") ?: JSONArray()
        val texts = mutableListOf<String>()
        val toolUses = mutableListOf<ToolUse>()
        for (i in 0 until content.length()) {
            val block = content.optJSONObject(i) ?: continue
            when (block.optString("type")) {
                "text" -> block.optString("text").takeIf { it.isNotBlank() }?.let { texts += it }
                "tool_use" -> {
                    val cmd = block.optJSONObject("input")?.optString("command").orEmpty()
                    toolUses += ToolUse(id = block.optString("id"), command = cmd)
                }
            }
        }
        return ClaudeTurn(
            stopReason = root.optString("stop_reason"),
            content = content,
            texts = texts,
            toolUses = toolUses,
        )
    }

    private fun errorMessage(body: String, code: Int): String =
        try {
            JSONObject(body).getJSONObject("error").getString("message")
        } catch (_: Throwable) {
            "HTTP $code"
        }

    /** A fresh tool definition per call — org.json objects are not worth sharing across threads. */
    private fun shellTool(): JSONObject = JSONObject()
        .put("name", TOOL_NAME)
        .put(
            "description",
            "Run a single shell command on THIS Android phone with Shizuku's 'shell' identity " +
                "(no root) and get its combined stdout/stderr. Use it to inspect and operate the " +
                "device the user is holding: read state (getprop, settings get, pm list packages, " +
                "dumpsys <svc>), measure performance (top -n1, cat /proc/meminfo, dumpsys meminfo " +
                "<pkg>, dumpsys batterystats), and make changes the user asks for. Prefer read-only " +
                "diagnostics first. Destructive commands are allowed but the app will ask the user " +
                "to confirm before they run, so keep each command to one clear purpose.",
        )
        .put(
            "input_schema",
            JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    JSONObject().put(
                        "command",
                        JSONObject()
                            .put("type", "string")
                            .put(
                                "description",
                                "One shell command line, e.g. 'getprop ro.product.model' or " +
                                    "'dumpsys meminfo com.whatsapp'.",
                            ),
                    ),
                )
                .put("required", JSONArray().put("command")),
        )

    private companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MODEL = "claude-opus-5"
        const val MAX_TOKENS = 8192
        const val TOOL_NAME = "run_shell"
        const val CONNECT_TIMEOUT_MS = 30_000
        const val READ_TIMEOUT_MS = 120_000
    }
}
