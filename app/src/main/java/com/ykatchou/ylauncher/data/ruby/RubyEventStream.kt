package com.ykatchou.ylauncher.data.ruby

import com.ykatchou.ylauncher.util.YLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Server-Sent Events feed off `/v1/eventos` — the heart of the app. One [connectOnce] call is a
 * single connection: it emits [Msg.Open] once the stream is live, then one [Msg.Ev] per `data:` line,
 * and simply **completes** when the connection drops (or a read times out past the keep-alive pulse).
 * The reconnect policy — backoff, and re-syncing the REST snapshot on every reconnect — lives one
 * level up in the ViewModel, so the stream itself stays a dumb, honest pipe.
 *
 * Contract rules honoured here: parse is tolerant (via [Evento.parse]), unknown event types survive
 * as [Evento.Desconhecido] rather than crashing the feed, and a dropped stream is a normal event,
 * never an exception the UI has to see.
 */
@Singleton
class RubyEventStream @Inject constructor(
    @Suppress("unused") @ApplicationContext private val context: Context,
    private val config: RubyConfig,
) {

    sealed interface Msg {
        /** The stream is connected and live. */
        object Open : Msg

        /** One parsed event. */
        data class Ev(val evento: Evento) : Msg
    }

    fun connectOnce(): Flow<Msg> = flow {
        val token = config.tokenNow() ?: return@flow
        val base = config.baseUrlNow()
        val conn = (URL("$base/v1/eventos").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_MS
            // A read that blocks longer than this without even a `pulso` means the connection is
            // dead in a way TCP has not noticed yet — time it out and let the caller reconnect.
            readTimeout = READ_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "text/event-stream")
        }
        try {
            if (conn.responseCode !in 200..299) {
                YLogger.d(TAG, "eventos handshake ${conn.responseCode}")
                return@flow
            }
            emit(Msg.Open)
            val reader = conn.inputStream.bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break // null = stream closed
                if (line.startsWith("data:")) {
                    val json = line.substring(5).trim()
                    if (json.isNotEmpty()) emit(Msg.Ev(Evento.parse(json)))
                }
                // Blank lines and SSE `event:`/`id:` fields are irrelevant to this contract.
            }
        } catch (e: Exception) {
            // Read timeout, socket reset, network gone — all just "the stream ended". Complete
            // normally so the ViewModel reconnects; never surface it as a crash on the feed.
            YLogger.d(TAG, "eventos ended: ${e.javaClass.simpleName}")
        } finally {
            conn.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val TAG = "RubyEventStream"
        const val CONNECT_MS = 6000
        const val READ_MS = 90_000
    }
}
