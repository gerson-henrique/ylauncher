package com.ykatchou.ylauncher.data.ruby

import com.ykatchou.ylauncher.util.YLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Ruby REST client, by hand — `HttpURLConnection` + org.json, in the exact spirit of
 * [com.ykatchou.ylauncher.data.weather.WeatherRepository]. No OkHttp, no Retrofit: the surface is
 * a handful of endpoints and the app stays light. Every authenticated call carries the pairing
 * token as `Authorization: Bearer`; [parear] is the one call made before a token exists, so it
 * takes the base URL explicitly.
 *
 * "Fora da rede" is a normal state, not an error (the Mac only answers on the home WiFi), so a
 * connection failure surfaces as [RubyResult.Unreachable], kept distinct from a real HTTP status.
 */
@Singleton
class RubyApi @Inject constructor(
    @Suppress("unused") @ApplicationContext private val context: Context,
    private val config: RubyConfig,
) {

    /** Trade a one-shot pairing code for a permanent token. `401` = wrong or already-used code. */
    suspend fun parear(baseUrl: String, codigo: String, nome: String): RubyResult<String> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("codigo", codigo).put("nome", nome).toString()
            when (val r = raw(baseUrl, "POST", "/v1/parear", body, token = null)) {
                is Raw.Ok -> try {
                    RubyResult.Ok(JSONObject(r.body).getString("token"))
                } catch (e: Exception) {
                    RubyResult.HttpError(-1)
                }
                is Raw.Http -> RubyResult.HttpError(r.code)
                Raw.Unreachable -> RubyResult.Unreachable
            }
        }

    suspend fun frota(): RubyResult<Frota> = getObject("/v1/frota") { Frota.from(it) }

    suspend fun pedidos(): RubyResult<List<Pedido>> = getArray("/v1/pedidos") { Pedido.from(it) }

    /** Folders Claude Code already trusts — the only places a background session can be opened. */
    suspend fun repos(): RubyResult<List<String>> = withContext(Dispatchers.IO) {
        when (val r = authed("GET", "/v1/repos", null)) {
            is Raw.Ok -> try {
                val arr = JSONArray(r.body)
                RubyResult.Ok((0 until arr.length()).map { arr.getString(it) })
            } catch (e: Exception) {
                RubyResult.HttpError(-1)
            }
            is Raw.Http -> RubyResult.HttpError(r.code)
            Raw.Unreachable -> RubyResult.Unreachable
        }
    }

    /** Open a background Claude Code session in [repo] with [prompt]. Returns its display name. */
    suspend fun despachar(repo: String, prompt: String): RubyResult<String> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("repo", repo).put("prompt", prompt).toString()
        when (val r = authed("POST", "/v1/despachos", body, LONG_TIMEOUT_MS)) {
            is Raw.Ok -> try {
                RubyResult.Ok(JSONObject(r.body).getString("nome"))
            } catch (e: Exception) {
                RubyResult.HttpError(-1)
            }
            is Raw.Http -> RubyResult.HttpError(r.code)
            Raw.Unreachable -> RubyResult.Unreachable
        }
    }

    /** Stop a background session (terminal ones are closed at the terminal). */
    suspend fun parar(sessaoId: String): RubyResult<Unit> = withContext(Dispatchers.IO) {
        when (val r = authed("POST", "/v1/sessoes/$sessaoId/parar", "")) {
            is Raw.Ok -> RubyResult.Ok(Unit)
            is Raw.Http -> RubyResult.HttpError(r.code)
            Raw.Unreachable -> RubyResult.Unreachable
        }
    }

    /**
     * What Cricket answers to something you said or typed: first the fixed command list (no AI,
     * instant); if it is not a command, the warm brain on the Mac (may dispatch or stop sessions).
     * Returns the sentence to show/speak; empty = understood nothing.
     */
    suspend fun falar(texto: String): RubyResult<String> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("texto", texto).toString()
        when (val c = authed("POST", "/v1/comandos", body)) {
            is Raw.Ok -> {
                val j = try { JSONObject(c.body) } catch (e: Exception) { JSONObject() }
                if (j.optBoolean("entendeu")) return@withContext RubyResult.Ok(j.optString("texto"))
            }
            is Raw.Http -> if (c.code == 401) return@withContext RubyResult.HttpError(401)
            Raw.Unreachable -> return@withContext RubyResult.Unreachable
        }
        when (val r = authed("POST", "/v1/fala", body, LONG_TIMEOUT_MS)) {
            is Raw.Ok -> try {
                RubyResult.Ok(JSONObject(r.body).optString("fala"))
            } catch (e: Exception) {
                RubyResult.HttpError(-1)
            }
            is Raw.Http -> RubyResult.HttpError(r.code)
            Raw.Unreachable -> RubyResult.Unreachable
        }
    }

    /**
     * Send a decision: the waiting Claude Code session continues. `409` means another screen already decided —
     * the contract says treat it as silent success and reload, so it maps to [RubyResult.Ok].
     */
    suspend fun decidir(pedidoId: String, ok: Boolean): RubyResult<Unit> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("ok", ok).toString()
        when (val r = authed("POST", "/v1/pedidos/$pedidoId/decisao", body)) {
            is Raw.Ok -> RubyResult.Ok(Unit)
            is Raw.Http -> if (r.code == 409) RubyResult.Ok(Unit) else RubyResult.HttpError(r.code)
            Raw.Unreachable -> RubyResult.Unreachable
        }
    }

    // --- conversas -----------------------------------------------------------------------------

    suspend fun conversas(): RubyResult<List<Conversa>> = getArray("/v1/conversas") { Conversa.from(it) }

    suspend fun criarConversa(titulo: String? = null): RubyResult<Conversa> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { if (!titulo.isNullOrBlank()) put("titulo", titulo) }.toString()
        when (val r = authed("POST", "/v1/conversas", body)) {
            is Raw.Ok -> try {
                RubyResult.Ok(Conversa.from(JSONObject(r.body)))
            } catch (e: Exception) {
                RubyResult.HttpError(-1)
            }
            is Raw.Http -> RubyResult.HttpError(r.code)
            Raw.Unreachable -> RubyResult.Unreachable
        }
    }

    /** `404` means it was already gone (or never yours) — treat as success and let the UI drop it. */
    suspend fun excluirConversa(id: String): RubyResult<Unit> = withContext(Dispatchers.IO) {
        when (val r = authed("DELETE", "/v1/conversas/$id", null)) {
            is Raw.Ok -> RubyResult.Ok(Unit)
            is Raw.Http -> if (r.code == 404) RubyResult.Ok(Unit) else RubyResult.HttpError(r.code)
            Raw.Unreachable -> RubyResult.Unreachable
        }
    }

    suspend fun mensagens(id: String): RubyResult<List<Mensagem>> =
        getArray("/v1/conversas/$id/mensagens") { Mensagem.from(it) }

    // --- helpers -------------------------------------------------------------------------------

    private suspend fun <T> getObject(path: String, parse: (JSONObject) -> T): RubyResult<T> =
        withContext(Dispatchers.IO) {
            when (val r = authed("GET", path, null)) {
                is Raw.Ok -> try {
                    RubyResult.Ok(parse(JSONObject(r.body)))
                } catch (e: Exception) {
                    RubyResult.HttpError(-1)
                }
                is Raw.Http -> RubyResult.HttpError(r.code)
                Raw.Unreachable -> RubyResult.Unreachable
            }
        }

    private suspend fun <T> getArray(path: String, parse: (JSONObject) -> T): RubyResult<List<T>> =
        withContext(Dispatchers.IO) {
            when (val r = authed("GET", path, null)) {
                is Raw.Ok -> try {
                    RubyResult.Ok(JSONArray(r.body).mapObjects { parse(it) })
                } catch (e: Exception) {
                    RubyResult.HttpError(-1)
                }
                is Raw.Http -> RubyResult.HttpError(r.code)
                Raw.Unreachable -> RubyResult.Unreachable
            }
        }

    /** Like [raw], but pulls the current base URL + token; no token yet ⇒ synthesise a 401. */
    private suspend fun authed(method: String, path: String, body: String?, timeoutMs: Int = TIMEOUT_MS): Raw {
        val token = config.tokenNow() ?: return Raw.Http(401)
        return raw(config.baseUrlNow(), method, path, body, token, timeoutMs)
    }

    private fun raw(
        baseUrl: String, method: String, path: String, body: String?, token: String?, timeoutMs: Int = TIMEOUT_MS,
    ): Raw {
        return try {
            val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = timeoutMs
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    Raw.Ok(conn.inputStream.bufferedReader().readText())
                } else {
                    conn.errorStream?.bufferedReader()?.readText() // drain so the socket can be reused
                    Raw.Http(code)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            YLogger.d(TAG, "unreachable: $method $path (${e.javaClass.simpleName})")
            Raw.Unreachable
        }
    }

    private sealed interface Raw {
        data class Ok(val body: String) : Raw
        data class Http(val code: Int) : Raw
        object Unreachable : Raw
    }

    private companion object {
        const val TAG = "RubyApi"
        const val TIMEOUT_MS = 6000
        /** The brain and `claude --bg` can take seconds; don't give up on them like on a GET. */
        const val LONG_TIMEOUT_MS = 70_000
    }
}
