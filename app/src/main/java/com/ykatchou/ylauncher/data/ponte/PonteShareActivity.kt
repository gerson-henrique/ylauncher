package com.ykatchou.ylauncher.data.ponte

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The two ponte entries in Android's share sheet. No UI of its own: it hands the shared item to
 * [Ponte] and closes, and a toast says how it went.
 *
 * - "Mandar pro Mac": files land in ~/Desktop/Do celular; plain text goes to the Mac's clipboard.
 * - "Abrir no Mac": the shared link opens in the Mac's browser.
 */
@AndroidEntryPoint
class PonteShareActivity : ComponentActivity() {

    @Inject lateinit var ponte: Ponte

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val open = intent.component?.className?.endsWith(OPEN_ALIAS) == true
        val uris = sharedUris(intent)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
        val app = applicationContext
        // Grants on the shared URIs belong to this activity; keep reading them after it closes.
        ponte.scope.launch {
            val message = when {
                open -> {
                    val url = text?.let(::firstUrl)
                    when {
                        url == null -> "nenhum link pra abrir"
                        ponte.openOnMac(url) -> "aberto no Mac"
                        else -> "Mac fora de alcance"
                    }
                }
                uris.isNotEmpty() -> {
                    val sent = uris.count { ponte.sendFile(it) != null }
                    when (sent) {
                        uris.size -> if (sent == 1) "mandado pro Mac" else "$sent arquivos mandados pro Mac"
                        0 -> "Mac fora de alcance"
                        else -> "$sent de ${uris.size} mandados"
                    }
                }
                !text.isNullOrEmpty() -> if (ponte.sendClip(text)) "copiado no Mac" else "Mac fora de alcance"
                else -> "nada pra mandar"
            }
            withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_SHORT).show() }
        }
        finish()
    }

    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM),
        )
        Intent.ACTION_SEND_MULTIPLE -> (
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            ).orEmpty()
        else -> emptyList()
    }

    private fun firstUrl(text: String): String? =
        Regex("""https?://\S+""", RegexOption.IGNORE_CASE).find(text)?.value

    private companion object {
        const val OPEN_ALIAS = "AbrirNoMac"
    }
}
