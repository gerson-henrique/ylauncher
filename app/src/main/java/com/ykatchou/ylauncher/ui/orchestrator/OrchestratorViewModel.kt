package com.ykatchou.ylauncher.ui.orchestrator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykatchou.ylauncher.data.ruby.Evento
import com.ykatchou.ylauncher.data.ruby.Maquina
import com.ykatchou.ylauncher.data.ruby.Pedido
import com.ykatchou.ylauncher.data.ruby.RubyApi
import com.ykatchou.ylauncher.data.ruby.RubyConfig
import com.ykatchou.ylauncher.data.ruby.RubyEventStream
import com.ykatchou.ylauncher.data.ruby.RubyResult
import com.ykatchou.ylauncher.data.ruby.Sessao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Which face the orchestrator page is showing. */
enum class Phase { LOADING, PAIRING, READY, UNREACHABLE }

/** A loose remark in the mural — either Cricket's own (mine=false) or the command you sent (mine=true). */
data class Recado(val id: Long, val texto: String, val mine: Boolean)

data class OrchestratorState(
    val phase: Phase = Phase.LOADING,
    /** SSE is connected and pushing — drives "ao vivo" vs "religando". */
    val connected: Boolean = false,
    val baseUrl: String = RubyConfig.DEFAULT_BASE_URL,
    /** Server clock from the last snapshot; "parado há X" = agora − vista_em. */
    val agora: Long = 0L,
    val maquinas: List<Maquina> = emptyList(),
    val sessoes: List<Sessao> = emptyList(),
    val pedidos: List<Pedido> = emptyList(),
    /** Trusted folders where a background session can be dispatched. */
    val repos: List<String> = emptyList(),
    /** A command/question is being answered by the Mac (shows the "• • •"). */
    val pensando: Boolean = false,
    /** Last thing the Ruby said — shown once as a toast, then consumed. */
    val fala: String? = null,
    /** Rolling recados for the mural (your commands + Cricket's remarks), newest last, capped. */
    val recados: List<Recado> = emptyList(),
    /** Set briefly after a failed pairing attempt, to explain why. */
    val pairingError: String? = null,
)

/**
 * Owns the whole orchestrator world as one [StateFlow]. On becoming active it pairs-or-loads: no
 * token ⇒ [Phase.PAIRING]; otherwise it pulls the REST snapshot and then *lives off the SSE*,
 * merging each event into state. The connect loop re-syncs the snapshot on every reconnect and backs
 * off when the Mac is unreachable — which is a normal state here, not a failure.
 *
 * All regra de negócio is on the Mac; this only reads, shows, and sends decisions.
 */
@HiltViewModel
class OrchestratorViewModel @Inject constructor(
    private val api: RubyApi,
    private val events: RubyEventStream,
    private val config: RubyConfig,
) : ViewModel() {

    private var recadoId = 0L

    private val _state = MutableStateFlow(OrchestratorState())
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    private var loop: Job? = null

    fun setActive(active: Boolean) {
        if (active) start() else stop()
    }

    private fun start() {
        if (loop != null) return
        loop = viewModelScope.launch {
            _state.update { it.copy(baseUrl = config.baseUrlNow()) }
            if (!config.hasToken.first()) {
                _state.update { it.copy(phase = Phase.PAIRING) }
                return@launch
            }
            connectLoop()
        }
    }

    private fun stop() {
        loop?.cancel()
        loop = null
        _state.update { it.copy(connected = false) }
    }

    /**
     * Snapshot → stream → (drop) → snapshot → stream → … while the page is active. A 401 anywhere
     * drops to pairing; being unreachable backs off and retries; a live stream that dies reconnects
     * fast, re-syncing the snapshot first so we never drift from the server's truth.
     */
    private suspend fun connectLoop() {
        var backoffMs = MIN_BACKOFF_MS
        while (currentCoroutineContext().isActive) {
            when (loadSnapshot()) {
                Load.UNAUTHORIZED -> {
                    config.clearToken()
                    _state.update { it.copy(phase = Phase.PAIRING, connected = false) }
                    return
                }
                Load.UNREACHABLE -> {
                    _state.update { it.copy(phase = Phase.UNREACHABLE, connected = false) }
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                }
                Load.OK -> {
                    _state.update { it.copy(phase = Phase.READY) }
                    val everOpened = streamOnce()
                    _state.update { it.copy(connected = false) }
                    // Reconnect fast if we had a healthy stream; otherwise keep backing off.
                    if (everOpened) {
                        backoffMs = MIN_BACKOFF_MS
                        delay(MIN_BACKOFF_MS)
                    } else {
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                    }
                }
            }
        }
    }

    private enum class Load { OK, UNREACHABLE, UNAUTHORIZED }

    private suspend fun loadSnapshot(): Load {
        val frota = api.frota()
        if (frota is RubyResult.Unreachable) return Load.UNREACHABLE
        if (frota is RubyResult.HttpError && frota.code == 401) return Load.UNAUTHORIZED

        val pedidos = api.pedidos()
        val repos = api.repos()

        _state.update { s ->
            s.copy(
                agora = (frota as? RubyResult.Ok)?.value?.agora ?: s.agora,
                maquinas = (frota as? RubyResult.Ok)?.value?.maquinas ?: s.maquinas,
                sessoes = (frota as? RubyResult.Ok)?.value?.sessoes?.sortedByParada(
                    (frota).value.agora,
                ) ?: s.sessoes,
                pedidos = (pedidos as? RubyResult.Ok)?.value ?: s.pedidos,
                repos = (repos as? RubyResult.Ok)?.value ?: s.repos,
            )
        }
        return Load.OK
    }

    /** Collect one SSE connection until it drops. Returns whether it ever actually opened. */
    private suspend fun streamOnce(): Boolean {
        var opened = false
        events.connectOnce().collect { msg ->
            when (msg) {
                RubyEventStream.Msg.Open -> {
                    opened = true
                    _state.update { it.copy(connected = true) }
                }
                is RubyEventStream.Msg.Ev -> apply(msg.evento)
            }
        }
        return opened
    }

    private fun apply(evento: Evento) {
        when (evento) {
            is Evento.ExecucaoMudou -> Unit // the old flow engine is gone; nothing emits this now

            is Evento.PedidoAberto -> _state.update { s ->
                s.copy(pedidos = listOf(evento.pedido) + s.pedidos.filterNot { it.id == evento.pedido.id })
            }

            is Evento.PedidoDecidido -> _state.update { s ->
                s.copy(pedidos = s.pedidos.filterNot { it.id == evento.id })
            }

            is Evento.SessaoMudou -> _state.update { s ->
                val merged = listOf(evento.sessao) + s.sessoes.filterNot { it.id == evento.sessao.id }
                s.copy(sessoes = merged.sortedByParada(s.agora))
            }

            is Evento.Fala -> {
                        addRecado(evento.texto, mine = false, falar = true)
            }

            Evento.Pulso, Evento.Desconhecido -> Unit // keep-alive / forward-compat: nothing to do
        }
    }

    // --- user actions --------------------------------------------------------------------------

    fun pair(baseUrl: String, codigo: String, nome: String) {
        viewModelScope.launch {
            _state.update { it.copy(pairingError = null) }
            config.setBaseUrl(baseUrl)
            when (val r = api.parear(config.baseUrlNow(), codigo.trim(), nome.trim().ifEmpty { "celular" })) {
                is RubyResult.Ok -> {
                    config.setToken(r.value)
                    _state.update { it.copy(baseUrl = config.baseUrlNow(), phase = Phase.LOADING) }
                    restart()
                }
                is RubyResult.HttpError -> _state.update {
                    it.copy(pairingError = if (r.code == 401) "Código errado ou já usado." else "Erro ${r.code}.")
                }
                RubyResult.Unreachable -> _state.update {
                    it.copy(pairingError = "Sem alcance ao Mac — confira o endereço e a WiFi.")
                }
            }
        }
    }

    fun decidir(pedidoId: String, ok: Boolean) {
        // Optimistic: drop the card now; a 409 (someone else decided) is silent success anyway.
        _state.update { it.copy(pedidos = it.pedidos.filterNot { p -> p.id == pedidoId }) }
        viewModelScope.launch { api.decidir(pedidoId, ok) }
    }

    /** Open a background Claude Code session in [repo]; Cricket's permission flow takes it from there. */
    fun despachar(repo: String, prompt: String) {
        val t = prompt.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            val r = api.despachar(repo, t)
            val texto = when (r) {
                is RubyResult.Ok -> "despachei ${r.value} em ${repo.substringAfterLast('/')}"
                is RubyResult.HttpError -> "o despacho falhou (${r.code})"
                RubyResult.Unreachable -> "sem alcance ao Mac"
            }
            addRecado(texto, mine = false)
        }
    }

    fun parar(sessaoId: String) {
        viewModelScope.launch { api.parar(sessaoId) }
    }

    /**
     * Say or type something to Cricket: fixed commands answer instantly; anything else goes to the
     * warm brain on the Mac, which may dispatch or stop sessions. Echoes yours, then her answer.
     */
    fun mandar(texto: String) {
        val t = texto.trim()
        if (t.isEmpty()) return
        addRecado(t, mine = true)
        _state.update { it.copy(pensando = true) }
        viewModelScope.launch {
            val r = api.falar(t)
            _state.update { it.copy(pensando = false) }
            when (r) {
                is RubyResult.Ok -> addRecado(r.value.ifBlank { "cri cri…" }, mine = false, falar = r.value.isNotBlank())
                is RubyResult.HttpError -> addRecado(if (r.code == 401) "precisa parear de novo" else "erro ${r.code}", mine = false)
                RubyResult.Unreachable -> addRecado("sem alcance ao Mac", mine = false)
            }
        }
    }

    private fun addRecado(texto: String, mine: Boolean, falar: Boolean = false) {
        val r = Recado(recadoId++, texto, mine)
        _state.update { it.copy(recados = (it.recados + r).takeLast(8), fala = if (falar) texto else it.fala) }
    }

    fun consumeFala() = _state.update { it.copy(fala = null) }

    private fun restart() {
        stop()
        start()
    }

    private companion object {
        const val MIN_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 15_000L
    }
}

/** Longest-stopped first, per the contract's ordering rule for the fleet. */
private fun List<Sessao>.sortedByParada(agora: Long): List<Sessao> =
    sortedByDescending { agora - it.vistaEm }
