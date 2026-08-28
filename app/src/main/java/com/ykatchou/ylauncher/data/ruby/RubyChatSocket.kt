package com.ykatchou.ylauncher.data.ruby

import android.util.Base64
import com.ykatchou.ylauncher.util.YLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/** One frame off the Ruby conversation channel. Unknown `tipo` is dropped (forward-compat). */
sealed interface ChatEvent {
    object Connected : ChatEvent
    object Disconnected : ChatEvent
    object Pensando : ChatEvent
    /** [conversa] identifies the thread. Null = a loose monologue (hook recado, fleet warning). */
    data class Fala(val texto: String, val gesto: String?, val conversa: String?) : ChatEvent
    data class Estado(val texto: String) : ChatEvent
    data class Aprovar(val id: String, val titulo: String, val detalhe: String) : ChatEvent
    data class Aviso(val texto: String) : ChatEvent
    data class Erro(val texto: String) : ChatEvent
}

/**
 * The conversation with Ruby over `ws://…/ws` — a minimal RFC 6455 client written by hand on a raw
 * [Socket], in the same spirit as the rest of the app (raw `HttpURLConnection`, hand-parsed
 * `/proc/net`). Android ships no `java.net.http.WebSocket`, and pulling OkHttp just for one text
 * socket would undo the weight budget the launcher earns everywhere else. Text frames only, LAN,
 * no TLS — the happy path a hand client actually needs.
 *
 * Auth rides the handshake as a plain `Authorization: Bearer` header line — which is exactly why a
 * raw client is convenient here: no library header restrictions to fight. Streaming is honoured by
 * the caller: a turn arrives as several [ChatEvent.Fala] frames to be appended, not replaced.
 */
@Singleton
class RubyChatSocket @Inject constructor(
    private val config: RubyConfig,
) {
    @Volatile private var out: OutputStream? = null
    private val writeLock = Any()
    private val rnd = SecureRandom()

    /**
     * One connection as a cold flow: emits [ChatEvent.Connected], then events, and completes when
     * the socket drops. The caller owns reconnect (re-collect). Cancelling the collector closes the
     * socket, which unblocks the reader thread.
     */
    fun open(): Flow<ChatEvent> = callbackFlow {
        val base = config.baseUrlNow()
        val token = config.tokenNow()
        val uri = URI.create(base)
        val host = uri.host ?: "192.168.0.30"
        val port = if (uri.port > 0) uri.port else 8080

        val sock: Socket
        try {
            sock = Socket()
            sock.connect(InetSocketAddress(host, port), CONNECT_MS)
            handshake(sock, host, port, token)
        } catch (e: Exception) {
            YLogger.d(TAG, "ws connect failed: ${e.javaClass.simpleName}")
            trySend(ChatEvent.Disconnected)
            close()
            return@callbackFlow
        }
        out = sock.getOutputStream()
        trySend(ChatEvent.Connected)

        val reader = Thread {
            try {
                readLoop(sock.getInputStream()) { trySend(it) }
            } catch (e: Exception) {
                // socket closed / interrupted — a normal end of stream
            } finally {
                trySend(ChatEvent.Disconnected)
                close()
            }
        }.apply { isDaemon = true; start() }

        awaitClose {
            try { writeFrame(OP_CLOSE, ByteArray(0)) } catch (_: Exception) {}
            try { sock.close() } catch (_: Exception) {}
            reader.interrupt()
            out = null
        }
    }.flowOn(Dispatchers.IO)

    /** Say something. [conversa] targets a specific conversation; null falls to the device default. */
    fun falar(texto: String, conversa: String?) {
        val o = JSONObject().put("tipo", "falar").put("texto", texto)
        if (!conversa.isNullOrBlank()) o.put("conversa", conversa)
        send(o)
    }

    /** Answer an approval card in-conversation. */
    fun aprovar(id: String, ok: Boolean) {
        send(JSONObject().put("tipo", "aprovacao").put("id", id).put("ok", ok))
    }

    private fun send(o: JSONObject) {
        try {
            writeFrame(OP_TEXT, o.toString().toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            YLogger.d(TAG, "ws send failed: ${e.javaClass.simpleName}")
        }
    }

    // --- RFC 6455 ------------------------------------------------------------------------------

    private fun handshake(sock: Socket, host: String, port: Int, token: String?) {
        val key = ByteArray(16).also { rnd.nextBytes(it) }
        val keyB64 = Base64.encodeToString(key, Base64.NO_WRAP)
        val req = buildString {
            append("GET /ws HTTP/1.1\r\n")
            append("Host: $host:$port\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: $keyB64\r\n")
            append("Sec-WebSocket-Version: 13\r\n")
            if (!token.isNullOrBlank()) append("Authorization: Bearer $token\r\n")
            append("\r\n")
        }
        sock.getOutputStream().apply { write(req.toByteArray(Charsets.UTF_8)); flush() }

        val inp = sock.getInputStream()
        val status = readLineAscii(inp) ?: throw IOException("ws: no handshake response")
        if (!status.contains("101")) throw IOException("ws handshake rejected: $status")
        // Drain the remaining response headers up to the blank line; then the stream is all frames.
        while (true) {
            val line = readLineAscii(inp) ?: break
            if (line.isEmpty()) break
        }
    }

    /** Read a CRLF-terminated ASCII line byte-by-byte (no buffering, so the stream stays frame-aligned). */
    private fun readLineAscii(inp: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = inp.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun readLoop(inp: InputStream, emit: (ChatEvent) -> Unit) {
        val din = DataInputStream(inp)
        val frag = StringBuilder()
        while (true) {
            val b0 = din.read()
            if (b0 == -1) return
            val fin = (b0 and 0x80) != 0
            val opcode = b0 and 0x0F
            val b1 = din.read()
            if (b1 == -1) return
            val masked = (b1 and 0x80) != 0
            var len = (b1 and 0x7F).toLong()
            when (len) {
                126L -> len = din.readUnsignedShort().toLong()
                127L -> len = din.readLong()
            }
            val mask = if (masked) ByteArray(4).also { din.readFully(it) } else null
            val payload = ByteArray(len.toInt())
            din.readFully(payload)
            if (mask != null) for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
            when (opcode) {
                OP_TEXT, OP_CONT -> {
                    frag.append(String(payload, Charsets.UTF_8))
                    if (fin) {
                        val msg = frag.toString()
                        frag.setLength(0)
                        parseFrame(msg)?.let(emit)
                    }
                }
                OP_CLOSE -> return
                OP_PING -> writeFrame(OP_PONG, payload)
                OP_PONG -> Unit
            }
        }
    }

    private fun writeFrame(opcode: Int, payload: ByteArray) {
        val o = out ?: return
        synchronized(writeLock) {
            o.write(0x80 or opcode) // FIN + opcode
            val len = payload.size
            when {
                len < 126 -> o.write(0x80 or len)
                len < 65536 -> {
                    o.write(0x80 or 126)
                    o.write((len ushr 8) and 0xFF)
                    o.write(len and 0xFF)
                }
                else -> {
                    o.write(0x80 or 127)
                    for (i in 7 downTo 0) o.write(((len.toLong() ushr (8 * i)) and 0xFF).toInt())
                }
            }
            // Client frames MUST be masked (RFC 6455 §5.3).
            val mask = ByteArray(4).also { rnd.nextBytes(it) }
            o.write(mask)
            val masked = ByteArray(len)
            for (i in 0 until len) masked[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            o.write(masked)
            o.flush()
        }
    }

    private fun parseFrame(json: String): ChatEvent? = try {
        val o = JSONObject(json)
        when (o.optString("tipo")) {
            "pensando" -> ChatEvent.Pensando
            "fala" -> ChatEvent.Fala(o.optString("texto"), o.optStringOrNull("gesto"), o.optStringOrNull("conversa"))
            "estado" -> ChatEvent.Estado(o.optString("texto"))
            "aprovar" -> ChatEvent.Aprovar(o.optString("id"), o.optString("titulo"), o.optString("detalhe"))
            "aviso" -> ChatEvent.Aviso(o.optString("texto"))
            "erro" -> ChatEvent.Erro(o.optString("texto"))
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val TAG = "RubyChatSocket"
        const val CONNECT_MS = 6000
        const val OP_CONT = 0x0
        const val OP_TEXT = 0x1
        const val OP_CLOSE = 0x8
        const val OP_PING = 0x9
        const val OP_PONG = 0xA
    }
}
