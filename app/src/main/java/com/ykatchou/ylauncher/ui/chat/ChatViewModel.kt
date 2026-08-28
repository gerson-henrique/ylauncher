package com.ykatchou.ylauncher.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykatchou.ylauncher.data.ruby.ChatEvent
import com.ykatchou.ylauncher.data.ruby.Conversa
import com.ykatchou.ylauncher.data.ruby.Mensagem
import com.ykatchou.ylauncher.data.ruby.RubyApi
import com.ykatchou.ylauncher.data.ruby.RubyChatSocket
import com.ykatchou.ylauncher.data.ruby.RubyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One thing rendered in the conversation stream. */
sealed interface ChatItem {
    val key: Long
}

data class UserMsg(override val key: Long, val texto: String) : ChatItem

/** Ruby's turn. [open] = still streaming; new `fala` frames append into it until the turn closes. */
data class RubyMsg(override val key: Long, val texto: String, val gesto: String?, val open: Boolean) : ChatItem

/** ESTADO = execution status, AVISO/ERRO = out-of-band, RECADO = Ruby's loose monologue (no thread). */
enum class SysKind { ESTADO, AVISO, ERRO, RECADO }

data class SystemLine(override val key: Long, val texto: String, val kind: SysKind) : ChatItem

data class ApprovalCard(
    override val key: Long,
    val id: String,
    val titulo: String,
    val detalhe: String,
    val decided: Boolean,
) : ChatItem

data class ChatState(
    val conversas: List<Conversa> = emptyList(),
    val activeId: String? = null,
    val items: List<ChatItem> = emptyList(),
    val connected: Boolean = false,
    val thinking: Boolean = false,
)

/**
 * The conversation with Ruby, now over many threads. Holds the whole thing as one [StateFlow]:
 * the list of conversations, which one is open, and its stream. Each [ChatEvent] folds in — `fala`
 * frames append into the open [RubyMsg] (streaming), and are **routed by conversation**: a frame
 * tagged with another thread's id is ignored here, and an untagged `fala` is Ruby's loose monologue
 * (a hook recado / fleet warning), shown as a system line, not a message in the open thread.
 *
 * All state lives on the Dell; this reads, shows, and sends. It never fakes what Ruby cannot yet do.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val socket: RubyChatSocket,
    private val api: RubyApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private var loop: Job? = null
    private var nextKey = 0L

    fun setActive(active: Boolean) {
        if (active) start() else stop()
    }

    private fun start() {
        if (loop != null) return
        loop = viewModelScope.launch {
            loadConversas(selectMostRecent = true)
            connectLoop()
        }
    }

    private fun stop() {
        loop?.cancel()
        loop = null
        _state.update { it.copy(connected = false) }
    }

    private suspend fun connectLoop() {
        var backoff = MIN_BACKOFF
        while (currentCoroutineContext().isActive) {
            var opened = false
            socket.open().collect { ev ->
                if (ev is ChatEvent.Connected) opened = true
                apply(ev)
            }
            if (opened) {
                backoff = MIN_BACKOFF
            } else {
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF)
            }
        }
    }

    // --- conversation management ---------------------------------------------------------------

    private suspend fun loadConversas(selectMostRecent: Boolean) {
        val r = api.conversas()
        if (r !is RubyResult.Ok) return
        val list = r.value.sortedByDescending { it.mudouEm }
        _state.update { it.copy(conversas = list) }
        if (selectMostRecent && _state.value.activeId == null) {
            val first = list.firstOrNull()
            if (first != null) openConversa(first.id) else novaConversa()
        }
    }

    fun refreshConversas() = viewModelScope.launch { loadConversas(selectMostRecent = false) }

    fun openConversa(id: String) {
        _state.update { it.copy(activeId = id, items = emptyList()) }
        viewModelScope.launch {
            val r = api.mensagens(id)
            val items = (r as? RubyResult.Ok)?.value?.map { it.toItem(nextKey++) } ?: emptyList()
            // Guard against a race: only apply if this is still the open thread.
            _state.update { if (it.activeId == id) it.copy(items = items) else it }
        }
    }

    fun novaConversa() {
        viewModelScope.launch {
            when (val r = api.criarConversa()) {
                is RubyResult.Ok -> {
                    _state.update { it.copy(activeId = r.value.id, items = emptyList()) }
                    loadConversas(selectMostRecent = false)
                }
                else -> Unit
            }
        }
    }

    fun excluirConversa(id: String) {
        viewModelScope.launch {
            api.excluirConversa(id)
            val remaining = _state.value.conversas.filterNot { it.id == id }
            _state.update { it.copy(conversas = remaining) }
            if (_state.value.activeId == id) {
                val next = remaining.firstOrNull()
                if (next != null) openConversa(next.id) else novaConversa()
            }
        }
    }

    // --- incoming ------------------------------------------------------------------------------

    private fun apply(ev: ChatEvent) {
        when (ev) {
            ChatEvent.Connected -> _state.update { it.copy(connected = true) }
            ChatEvent.Disconnected -> _state.update { it.copy(connected = false, thinking = false, items = closeOpen(it.items)) }
            ChatEvent.Pensando -> _state.update { it.copy(thinking = true, items = closeOpen(it.items)) }

            is ChatEvent.Fala -> _state.update { s ->
                val conv = ev.conversa
                when {
                    // Loose monologue (no thread): a recado / fleet warning, shown as a system line.
                    conv == null -> s.copy(
                        items = s.items + SystemLine(nextKey++, ev.texto.trim(), SysKind.RECADO),
                        thinking = false,
                    )
                    // Belongs to another thread — not this view.
                    s.activeId != null && conv != s.activeId -> s
                    else -> {
                        val items = s.items.toMutableList()
                        val last = items.lastOrNull()
                        if (last is RubyMsg && last.open) {
                            items[items.lastIndex] = last.copy(
                                texto = (last.texto + " " + ev.texto).trim(),
                                gesto = ev.gesto ?: last.gesto,
                            )
                        } else {
                            items += RubyMsg(nextKey++, ev.texto.trim(), ev.gesto, open = true)
                        }
                        // Adopt the server's default thread if we hadn't picked one yet.
                        s.copy(items = items, activeId = s.activeId ?: conv, thinking = false)
                    }
                }
            }

            is ChatEvent.Estado -> addLine(ev.texto, SysKind.ESTADO)
            is ChatEvent.Aviso -> addLine(ev.texto, SysKind.AVISO)
            is ChatEvent.Erro -> addLine(ev.texto, SysKind.ERRO)

            is ChatEvent.Aprovar -> _state.update { s ->
                if (s.items.any { it is ApprovalCard && it.id == ev.id }) return@update s
                s.copy(items = s.items + ApprovalCard(nextKey++, ev.id, ev.titulo, ev.detalhe, decided = false))
            }
        }
    }

    private fun addLine(texto: String, kind: SysKind) = _state.update {
        it.copy(items = it.items + SystemLine(nextKey++, texto, kind))
    }

    // --- outgoing ------------------------------------------------------------------------------

    fun send(texto: String) {
        val t = texto.trim()
        if (t.isEmpty()) return
        _state.update { it.copy(items = closeOpen(it.items) + UserMsg(nextKey++, t)) }
        socket.falar(t, _state.value.activeId)
        // Pick up the auto-title (first message) and the new ordering.
        refreshConversas()
    }

    fun decide(id: String, ok: Boolean) {
        _state.update { s ->
            s.copy(items = s.items.map { if (it is ApprovalCard && it.id == id) it.copy(decided = true) else it })
        }
        socket.aprovar(id, ok)
    }

    private fun closeOpen(items: List<ChatItem>): List<ChatItem> {
        val last = items.lastOrNull()
        if (last is RubyMsg && last.open) {
            return items.toMutableList().also { it[it.lastIndex] = last.copy(open = false) }
        }
        return items
    }

    private fun Mensagem.toItem(key: Long): ChatItem =
        if (fromRuby) RubyMsg(key, texto, gesto, open = false) else UserMsg(key, texto)

    private companion object {
        const val MIN_BACKOFF = 1_000L
        const val MAX_BACKOFF = 15_000L
    }
}
