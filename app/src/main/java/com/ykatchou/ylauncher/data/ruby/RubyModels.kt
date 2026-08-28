package com.ykatchou.ylauncher.data.ruby

import org.json.JSONArray
import org.json.JSONObject

/**
 * The Ruby control-plane wire types, mapped 1:1 from `gerson-henrique/ruby` ·
 * `docs/contrato-android.md` @ `reescopo/capataz`. The app is a pure consumer: these carry no
 * behaviour, only the shape the server sends. Timestamps are **unix seconds**, never millis.
 *
 * Two rules from the contract are baked in here so every call site inherits them: optional fields
 * are *omitted* (never `null`) so parsing is tolerant of absence, and unknown event types collapse
 * to [Evento.Desconhecido] rather than throwing — new events will appear and must not crash us.
 */

data class Maquina(val id: String, val nome: String, val vistoEm: Long) {
    companion object {
        fun from(o: JSONObject) = Maquina(
            id = o.optString("id"),
            nome = o.optString("nome", o.optString("id")),
            vistoEm = o.optLong("visto_em"),
        )
    }
}

/** `travada` (esperando permissão) and `perdida` (zumbi) are the only two that ask for attention. */
enum class SessaoEstado(val atencao: Boolean) {
    RODANDO(false), TRAVADA(true), OCIOSA(false), TERMINADA(false), PERDIDA(true), DESCONHECIDO(false);

    companion object {
        fun parse(s: String?) = when (s) {
            "rodando" -> RODANDO
            "travada" -> TRAVADA
            "ociosa" -> OCIOSA
            "terminada" -> TERMINADA
            "perdida" -> PERDIDA
            else -> DESCONHECIDO
        }
    }
}

data class Sessao(
    val id: String,
    val maquina: String,
    val cwd: String,
    val nome: String,
    val kind: String,
    val origem: String,
    val estado: SessaoEstado,
    val iniciadaEm: Long,
    val vistaEm: Long,
) {
    companion object {
        fun from(o: JSONObject) = Sessao(
            id = o.optString("id"),
            maquina = o.optString("maquina"),
            cwd = o.optString("cwd"),
            nome = o.optString("nome"),
            kind = o.optString("kind"),
            origem = o.optString("origem"),
            estado = SessaoEstado.parse(o.optString("estado")),
            iniciadaEm = o.optLong("iniciada_em"),
            vistaEm = o.optLong("vista_em"),
        )
    }
}

data class Pedido(
    val id: String,
    val titulo: String,
    val detalhe: String,
    val estado: String,
    val criadoEm: Long,
) {
    companion object {
        fun from(o: JSONObject) = Pedido(
            id = o.optString("id"),
            titulo = o.optString("titulo"),
            detalhe = o.optString("detalhe"),
            estado = o.optString("estado"),
            criadoEm = o.optLong("criado_em"),
        )
    }
}

enum class DespachoEstado(val atencao: Boolean, val ativo: Boolean) {
    RODANDO(false, true),
    PAUSADA(false, true),
    TRAVADA(true, false),
    CONCLUIDA(false, false),
    FALHOU(true, false),
    CANCELADA(false, false),
    DESCONHECIDO(false, false);

    companion object {
        fun parse(s: String?) = when (s) {
            "rodando" -> RODANDO
            "pausada" -> PAUSADA
            "travada" -> TRAVADA
            "concluida" -> CONCLUIDA
            "falhou" -> FALHOU
            "cancelada" -> CANCELADA
            else -> DESCONHECIDO
        }
    }
}

data class Despacho(
    val id: String,
    val fluxo: String,
    val estado: DespachoEstado,
    val no: String?,
    val pedido: String?,
    val motivo: String?,
    val criadaEm: Long,
    val mudouEm: Long,
) {
    companion object {
        fun from(o: JSONObject) = Despacho(
            id = o.optString("id"),
            fluxo = o.optString("fluxo"),
            estado = DespachoEstado.parse(o.optString("estado")),
            no = o.optStringOrNull("no"),
            pedido = o.optStringOrNull("pedido"),
            motivo = o.optStringOrNull("motivo"),
            criadaEm = o.optLong("criada_em"),
            mudouEm = o.optLong("mudou_em"),
        )
    }
}

/** The `/v1/frota` snapshot: the server clock plus the machines and sessions it can see. */
data class Frota(val agora: Long, val maquinas: List<Maquina>, val sessoes: List<Sessao>) {
    companion object {
        fun from(o: JSONObject) = Frota(
            agora = o.optLong("agora"),
            maquinas = o.arr("maquinas").mapObjects { Maquina.from(it) },
            sessoes = o.arr("sessoes").mapObjects { Sessao.from(it) },
        )
    }
}

/** One SSE frame off `/v1/eventos`. Unknown `tipo` and malformed JSON both become [Desconhecido]. */
sealed interface Evento {
    data class ExecucaoMudou(
        val id: String,
        val fluxo: String,
        val estado: DespachoEstado,
        val no: String?,
        val pedido: String?,
        val motivo: String?,
    ) : Evento

    data class PedidoAberto(val pedido: Pedido) : Evento
    data class PedidoDecidido(val id: String, val ok: Boolean) : Evento
    data class SessaoMudou(val sessao: Sessao) : Evento
    data class Fala(val texto: String, val gesto: String?) : Evento
    object Pulso : Evento
    object Desconhecido : Evento

    companion object {
        fun parse(json: String): Evento = try {
            val o = JSONObject(json)
            when (o.optString("tipo")) {
                "execucao_mudou" -> ExecucaoMudou(
                    id = o.optString("id"),
                    fluxo = o.optString("fluxo"),
                    estado = DespachoEstado.parse(o.optString("estado")),
                    no = o.optStringOrNull("no"),
                    pedido = o.optStringOrNull("pedido"),
                    motivo = o.optStringOrNull("motivo"),
                )
                "pedido_aberto" -> PedidoAberto(Pedido.from(o.getJSONObject("pedido")))
                "pedido_decidido" -> PedidoDecidido(o.optString("id"), o.optBoolean("ok"))
                "sessao_mudou" -> SessaoMudou(Sessao.from(o.getJSONObject("sessao")))
                "fala" -> Fala(o.optString("texto"), o.optStringOrNull("gesto"))
                "pulso" -> Pulso
                else -> Desconhecido
            }
        } catch (e: Exception) {
            Desconhecido
        }
    }
}

/** A conversation thread with Ruby. `mensagens` is the message count, for the list preview. */
data class Conversa(
    val id: String,
    val titulo: String,
    val criadaEm: Long,
    val mudouEm: Long,
    val mensagens: Int,
) {
    companion object {
        fun from(o: JSONObject) = Conversa(
            id = o.optString("id"),
            titulo = o.optString("titulo").ifBlank { "nova conversa" },
            criadaEm = o.optLong("criada_em"),
            mudouEm = o.optLong("mudou_em"),
            mensagens = o.optInt("mensagens"),
        )
    }
}

/** One stored message in a conversation. `papel` is "gero" (you) or "ruby". */
data class Mensagem(
    val id: String,
    val papel: String,
    val texto: String,
    val gesto: String?,
    val em: Long,
) {
    val fromRuby: Boolean get() = papel == "ruby"

    companion object {
        fun from(o: JSONObject) = Mensagem(
            id = o.optString("id"),
            papel = o.optString("papel"),
            texto = o.optString("texto"),
            gesto = o.optStringOrNull("gesto"),
            em = o.optLong("em"),
        )
    }
}

/** REST outcome that keeps "fora da rede" (normal) apart from a real HTTP status (401 = reparear). */
sealed interface RubyResult<out T> {
    data class Ok<T>(val value: T) : RubyResult<T>
    data class HttpError(val code: Int) : RubyResult<Nothing>
    object Unreachable : RubyResult<Nothing>
}

internal inline fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> =
    (0 until length()).map { f(getJSONObject(it)) }

internal fun JSONObject.arr(key: String): JSONArray = optJSONArray(key) ?: JSONArray()

/** Contract rule: absent means absent. Treat missing/null/blank alike as null. */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
