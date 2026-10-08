package com.ykatchou.ylauncher.ui.grilo

import android.Manifest
import androidx.activity.ComponentActivity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.lifecycle.lifecycleScope
import com.ykatchou.ylauncher.R
import com.ykatchou.ylauncher.data.grilo.Grilo
import com.ykatchou.ylauncher.data.ruby.RubyApi
import com.ykatchou.ylauncher.data.ruby.RubyResult
import com.ykatchou.ylauncher.util.YLogger
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Grilo's ear on the phone. Invisible (translucent, no UI): the Smart Key opens it, it beeps,
 * listens once with the phone's own pt-BR recognizer, sends the text to Cricket on the Mac (fixed
 * commands first, then the warm brain), says the answer, and leaves. Every step is mirrored on the
 * rear screen through [Grilo]'s media session. Works over the lock screen, so the phone can stay
 * face down.
 */
@AndroidEntryPoint
class GriloActivity : ComponentActivity() {
    @Inject lateinit var api: RubyApi

    private var reconhecedor: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsPronto = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opened as an "audio player" (the rear-screen picker loophole): nothing to play, leave.
        if (intent?.action == Intent.ACTION_VIEW) { Grilo.sessao(this); finish(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)
        tts = TextToSpeech(this) { st ->
            if (st == TextToSpeech.SUCCESS) { tts?.language = Locale("pt", "BR"); ttsPronto = true }
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) ouvir()
        else pedirMic.launch(Manifest.permission.RECORD_AUDIO)
    }

    private val pedirMic = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { ok -> if (ok) ouvir() else finish() }

    private fun ouvir() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Grilo.mostrar(this, Grilo.Estado.INVALIDO, "Sem reconhecimento de voz", "Grilo"); finish(); return
        }
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60).startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
        Grilo.mostrar(this, Grilo.Estado.OUVINDO, "Ouvindo…", "Grilo")
        reconhecedor = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val texto = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (texto.isBlank()) responder("", "") else perguntar(texto)
                }
                override fun onError(error: Int) { YLogger.d(TAG, "recognizer error $error"); responder("", "") }
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR"))
        }
    }

    private fun perguntar(texto: String) {
        Grilo.mostrar(this, Grilo.Estado.PENSANDO, "• • •", texto)
        lifecycleScope.launch {
            val r = api.falar(texto.removePrefix("grilo").trim())
            responder(if (r is RubyResult.Ok) r.value else "", texto)
        }
    }

    /** Chirp always; the sentence (spoken + on the rear screen) only when there is one. */
    private fun responder(frase: String, ouvido: String) {
        val limpa = frase.removePrefix("Creak Creak... ").trim()
        Grilo.mostrar(this, if (limpa.isEmpty()) Grilo.Estado.INVALIDO else Grilo.Estado.FALANDO,
            limpa.ifEmpty { "cri cri…" }, ouvido.ifEmpty { "Grilo" })
        runCatching { MediaPlayer.create(this, R.raw.grilo)?.apply { setOnCompletionListener { it.release() }; start() } }
        lifecycleScope.launch {
            delay(850)
            if (limpa.isNotEmpty() && ttsPronto) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { runOnUiThread { encerrar(limpa, ouvido) } }
                    @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) { runOnUiThread { encerrar(limpa, ouvido) } }
                })
                tts?.speak(limpa, TextToSpeech.QUEUE_FLUSH, null, "grilo")
            } else {
                delay(1500); encerrar(limpa, ouvido)
            }
        }
    }

    /** The answer stays on the rear screen (paused "track"); the activity goes away. */
    private fun encerrar(frase: String, ouvido: String) {
        Grilo.mostrar(this, Grilo.Estado.QUIETO, frase.ifEmpty { "Grilo" }, ouvido.ifEmpty { "aperte o botão e fale" })
        finish()
    }

    override fun onDestroy() {
        reconhecedor?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "GriloActivity"
    }
}
