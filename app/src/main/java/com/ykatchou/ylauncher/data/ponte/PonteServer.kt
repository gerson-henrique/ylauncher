package com.ykatchou.ylauncher.data.ponte

import com.ykatchou.ylauncher.util.YLogger
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * The phone's half of the bridge: a one-route HTTP listener the Mac pushes its clipboard to.
 *
 * Listening, rather than keeping a socket open to the Mac, is what makes the bridge free when idle:
 * an accept() blocked in the kernel costs nothing, and the launcher's process is kept alive by the
 * system anyway because it is the home screen. Hand-written because the whole protocol is one
 * POST with a small body — an HTTP server library would weigh more than the feature.
 */
class PonteServer(
    private val port: Int,
    private val onClip: (text: String, seq: Long) -> Unit,
) {
    @Volatile private var socket: ServerSocket? = null
    @Volatile private var token: String = ""

    @Synchronized
    fun start(token: String) {
        this.token = token
        if (socket?.isClosed == false) return
        val server = runCatching { ServerSocket(port) }
            .onFailure { YLogger.e(TAG, "cannot listen on :$port", it) }
            .getOrNull() ?: return
        socket = server
        thread(name = "ponte-server", isDaemon = true) {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                runCatching { client.use(::serve) }.onFailure { YLogger.d(TAG, "request failed: ${it.message}") }
            }
        }
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun serve(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val input = BufferedInputStream(client.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = generateSequence { readLine(input)?.takeIf { it.isNotEmpty() } }
            .mapNotNull { line -> line.split(':', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim().lowercase() to it[1].trim() } }
            .toMap()
        val (method, path) = requestLine.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "") }

        val status = when {
            headers["authorization"] != "Bearer $token" -> 401
            method != "POST" || path != "/clip" -> 404
            else -> {
                val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n < 0) break
                    read += n
                }
                onClip(String(body, 0, read, Charsets.UTF_8), headers["x-seq"]?.toLongOrNull() ?: 0L)
                200
            }
        }
        val reason = when (status) { 200 -> "OK"; 401 -> "Unauthorized"; else -> "Not Found" }
        client.getOutputStream().write("HTTP/1.1 $status $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
    }

    /** One CRLF-terminated header line, capped so a misbehaving client cannot grow it forever. */
    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (sb.length < MAX_LINE) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private companion object {
        const val TAG = "PonteServer"
        const val READ_TIMEOUT_MS = 5000
        const val MAX_LINE = 8 * 1024
        const val MAX_BODY = 1 shl 20
    }
}
