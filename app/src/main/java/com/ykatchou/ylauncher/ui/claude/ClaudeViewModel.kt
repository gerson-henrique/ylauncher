package com.ykatchou.ylauncher.ui.claude

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykatchou.ylauncher.data.claude.AnthropicClient
import com.ykatchou.ylauncher.data.claude.ClaudeSecretStore
import com.ykatchou.ylauncher.data.claude.DangerGate
import com.ykatchou.ylauncher.data.claude.ToolUse
import com.ykatchou.ylauncher.data.running.ShizukuShell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

enum class Speaker { USER, CLAUDE, TOOL, ERROR }

/** One line in the transcript. [detail] carries a command's output or a tool's error, shown folded. */
data class ChatLine(val speaker: Speaker, val text: String, val detail: String? = null)

/** A destructive command parked until the user allows or denies it. */
data class PendingCommand(val command: String, val reason: String)

/**
 * Drives the Claude page: the manual tool-use loop, the confirmation gate for destructive commands,
 * and the Shizuku health readout. The launcher is the control surface — this holds the conversation
 * and decides what runs; the actual thinking is Claude's and the actual execution is Shizuku's.
 *
 * The loop is deliberately hand-written rather than an SDK's tool-runner: the one thing it must do
 * that a runner will not is *pause* on a dangerous command and wait for a human, which it does by
 * suspending on a [CompletableDeferred] the UI completes from the Permitir/Negar buttons.
 */
@HiltViewModel
class ClaudeViewModel @Inject constructor(
    private val client: AnthropicClient,
    private val secretStore: ClaudeSecretStore,
) : ViewModel() {

    // The wire conversation the API sees — assistant turns (with their thinking/tool_use blocks) and
    // tool results are appended here verbatim, so the loop stays coherent across turns on one model.
    private val wire = JSONArray()

    private val _lines = MutableStateFlow<List<ChatLine>>(emptyList())
    val lines: StateFlow<List<ChatLine>> = _lines.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _pending = MutableStateFlow<PendingCommand?>(null)
    val pending: StateFlow<PendingCommand?> = _pending.asStateFlow()

    private val _shizukuReady = MutableStateFlow(ShizukuShell.isReady())
    val shizukuReady: StateFlow<Boolean> = _shizukuReady.asStateFlow()

    /** Drives the "paste your key" prompt without the key ever reaching the UI. */
    val hasKey: Flow<Boolean> = secretStore.hasKey

    private var pendingDeferred: CompletableDeferred<Boolean>? = null

    fun refreshShizuku() {
        _shizukuReady.value = ShizukuShell.isReady()
    }

    fun saveCredentials(key: String, workspaceId: String) {
        viewModelScope.launch {
            secretStore.setApiKey(key)
            secretStore.setWorkspaceId(workspaceId)
        }
    }

    fun clearCredentials() {
        viewModelScope.launch { secretStore.clear() }
    }

    fun resolvePending(allow: Boolean) {
        pendingDeferred?.complete(allow)
    }

    fun send(userText: String) {
        val text = userText.trim()
        if (text.isEmpty() || _busy.value) return
        append(ChatLine(Speaker.USER, text))
        wire.put(userMessage(textBlock(text)))
        viewModelScope.launch { runLoop() }
    }

    private suspend fun runLoop() {
        _busy.value = true
        refreshShizuku()
        try {
            val apiKey = secretStore.getApiKey()
            if (apiKey == null) {
                append(ChatLine(Speaker.ERROR, "Sem chave da API — cole a sua no campo abaixo."))
                return
            }
            val workspaceId = secretStore.getWorkspaceId()
            var steps = 0
            while (true) {
                if (++steps > MAX_STEPS) {
                    append(ChatLine(Speaker.ERROR, "Limite de passos atingido nesta rodada."))
                    return
                }
                val turn = try {
                    client.send(apiKey, workspaceId, SYSTEM_PROMPT, wire)
                } catch (t: Throwable) {
                    append(ChatLine(Speaker.ERROR, t.message ?: "Falha ao falar com o Claude."))
                    return
                }
                wire.put(assistantMessage(turn.content))
                turn.texts.forEach { append(ChatLine(Speaker.CLAUDE, it)) }

                if (turn.stopReason != "tool_use") return

                val results = JSONArray()
                for (tu in turn.toolUses) {
                    val allowed = if (DangerGate.isDangerous(tu.command)) awaitConfirm(tu) else true
                    val (output, isError) = when {
                        !allowed -> "Comando negado pelo usuário." to true
                        else -> runShell(tu.command)
                    }
                    append(ChatLine(Speaker.TOOL, tu.command, detail = output))
                    results.put(toolResult(tu.id, output, isError))
                }
                wire.put(userMessage(results))
            }
        } finally {
            _busy.value = false
        }
    }

    private suspend fun awaitConfirm(tu: ToolUse): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pendingDeferred = deferred
        _pending.value = PendingCommand(tu.command, DangerGate.reason(tu.command).orEmpty())
        return try {
            deferred.await()
        } finally {
            _pending.value = null
            pendingDeferred = null
        }
    }

    private suspend fun runShell(command: String): Pair<String, Boolean> =
        withContext(Dispatchers.IO) {
            when (val out = ShizukuShell.run(command)) {
                null -> "Shizuku indisponível ou o comando falhou." to true
                else -> {
                    val text = out.ifBlank { "(sem saída)" }
                    val capped = if (text.length > MAX_OUTPUT) {
                        text.take(MAX_OUTPUT) + "\n…(saída cortada)"
                    } else {
                        text
                    }
                    capped to false
                }
            }
        }

    private fun append(line: ChatLine) {
        _lines.value = _lines.value + line
    }

    private fun textBlock(text: String) =
        JSONArray().put(JSONObject().put("type", "text").put("text", text))

    private fun userMessage(content: JSONArray) =
        JSONObject().put("role", "user").put("content", content)

    private fun assistantMessage(content: JSONArray) =
        JSONObject().put("role", "assistant").put("content", content)

    private fun toolResult(id: String, output: String, isError: Boolean): JSONObject =
        JSONObject()
            .put("type", "tool_result")
            .put("tool_use_id", id)
            .put("content", output)
            .apply { if (isError) put("is_error", true) }

    private companion object {
        const val MAX_STEPS = 8
        const val MAX_OUTPUT = 8000

        val SYSTEM_PROMPT = """
            You operate a Blackview BV9300 Pro phone running Android 15, through a single tool,
            run_shell, that runs one shell command at a time with Shizuku's 'shell' identity — no
            root. You are talking to the phone's owner on a small screen, so keep answers short and
            in the user's language (Brazilian Portuguese unless they write otherwise).

            Your job is to help inspect, diagnose and tune this device: read state and measure
            performance with read-only commands first, and make changes only when the user asks.
            When a task needs several commands, run them one at a time and reason from each result.

            Never disturb the apps this launcher depends on to work — do not force-stop, clear,
            disable or uninstall any of them: com.ykatchou.ylauncher (this launcher itself),
            helium314.keyboard (the keyboard), moe.shizuku.privileged.api (Shizuku, your own
            lifeline), com.android.systemui, and com.blackview.launcher.

            Destructive or state-changing commands are allowed, but the app shows the user a
            confirmation before running them, so keep each command to one clear purpose and say what
            you expect it to do. If a command returns an error that it could not run, Shizuku may be
            down — tell the user rather than retrying blindly.
        """.trimIndent()
    }
}
