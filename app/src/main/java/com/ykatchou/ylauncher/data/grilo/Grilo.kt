package com.ykatchou.ylauncher.data.grilo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.ykatchou.ylauncher.util.YLogger

/**
 * The phone's Grilo, shown on the BV9300 Pro's round rear screen through a loophole: that screen
 * only shows the "now playing" of the audio app you pick in its settings, so Grilo *is* an audio
 * app. Its "track" is the conversation — the cover is the Tinta bubble in the current state, the
 * title is Cricket's answer, the artist line is what Grilo heard. One [MediaSession] for the whole
 * process, shared by [GriloMediaService] (what the rear screen binds to) and the listening activity.
 */
object Grilo {
    enum class Estado { QUIETO, OUVINDO, PENSANDO, FALANDO, INVALIDO }

    private const val TAG = "Grilo"
    private var sessao: MediaSession? = null

    /** Rear-screen transport buttons: next = approve the open request, previous = deny it. */
    var aoDecidir: ((Boolean) -> Unit)? = null
    /** Rear-screen play: ask to listen (Android may refuse a background mic start). */
    var aoOuvir: (() -> Unit)? = null

    fun sessao(context: Context): MediaSession = sessao ?: MediaSession(context.applicationContext, "grilo").also { s ->
        s.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { YLogger.d(TAG, "rear: play"); aoOuvir?.invoke() }
            override fun onSkipToNext() { YLogger.d(TAG, "rear: next = aprovar"); aoDecidir?.invoke(true) }
            override fun onSkipToPrevious() { YLogger.d(TAG, "rear: previous = negar"); aoDecidir?.invoke(false) }
        })
        s.isActive = true
        sessao = s
        mostrar(context, Estado.QUIETO, "Grilo", "aperte o botão e fale")
    }

    /** Publish a state to whoever shows the session (the rear screen, the lock screen). */
    fun mostrar(context: Context, estado: Estado, titulo: String, subtitulo: String) {
        val s = sessao ?: sessao(context)
        val capa = capa(estado)
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, titulo)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, titulo)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, subtitulo)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitulo)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "Cricket")
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, capa)
                .putBitmap(MediaMetadata.METADATA_KEY_ART, capa)
                .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, capa)
                .build(),
        )
        val ativo = estado != Estado.QUIETO
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(if (ativo) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 0L, 1f)
                .build(),
        )
    }

    /** The Tinta bubble: warm paper, the 蛩 seal; the state lives in the ring and the seal colour. */
    fun capa(estado: Estado): Bitmap {
        val n = 480
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val papel = Color.rgb(0xF6, 0xF0, 0xE3)
        val selo = Color.rgb(0xB2, 0x3B, 0x2E)
        val jade = Color.rgb(0x5F, 0x8A, 0x4F)
        val fraca = Color.rgb(0x9A, 0x8C, 0x74)
        val (fundo, anel, largura, marca) = when (estado) {
            Estado.QUIETO -> Quad(papel, fraca, 10f, selo)
            Estado.OUVINDO, Estado.PENSANDO -> Quad(papel, jade, 26f, jade)
            Estado.FALANDO -> Quad(papel, selo, 26f, selo)
            Estado.INVALIDO -> Quad(selo, selo, 10f, papel)
        }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(Color.BLACK)   // the rear panel is black glass: the bubble floats on it
        p.color = fundo; c.drawCircle(n / 2f, n / 2f, n / 2f - 8f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = largura; p.color = anel
        c.drawCircle(n / 2f, n / 2f, n / 2f - 8f - largura / 2, p)
        p.style = Paint.Style.FILL; p.color = marca
        p.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        p.textSize = n * 0.46f; p.textAlign = Paint.Align.CENTER
        c.save(); c.rotate(3f, n / 2f, n / 2f)
        val y = n / 2f - (p.descent() + p.ascent()) / 2
        c.drawText("蛩", n / 2f, y, p)
        c.restore()
        return bmp
    }

    private data class Quad(val fundo: Int, val anel: Int, val largura: Float, val marca: Int)
}
