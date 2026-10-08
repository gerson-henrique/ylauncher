package com.ykatchou.ylauncher.data.grilo

import android.content.Intent
import android.media.browse.MediaBrowser
import android.os.Bundle
import android.service.media.MediaBrowserService
import com.ykatchou.ylauncher.data.ruby.RubyApi
import com.ykatchou.ylauncher.data.ruby.RubyResult
import com.ykatchou.ylauncher.ui.grilo.GriloActivity
import com.ykatchou.ylauncher.util.YLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * What makes Grilo an "audio app": the rear screen's player picker lists apps that offer a
 * MediaBrowserService, and once picked it binds here and shows our session. There is nothing to
 * browse — the empty tree is honest; the point is the session token.
 */
@AndroidEntryPoint
class GriloMediaService : MediaBrowserService() {
    @Inject lateinit var api: RubyApi
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        sessionToken = Grilo.sessao(this).sessionToken
        // Rear buttons decide the single open request; with several, the screen is the place.
        Grilo.aoDecidir = { ok ->
            escopo.launch {
                val abertos = (api.pedidos() as? RubyResult.Ok)?.value.orEmpty()
                when {
                    abertos.isEmpty() -> Grilo.mostrar(this@GriloMediaService, Grilo.Estado.QUIETO, "Nada esperando você.", "Grilo")
                    abertos.size > 1 -> Grilo.mostrar(this@GriloMediaService, Grilo.Estado.QUIETO, "Tem ${abertos.size} pedidos.", "decida pela tela")
                    else -> {
                        api.decidir(abertos[0].id, ok)
                        Grilo.mostrar(this@GriloMediaService, Grilo.Estado.FALANDO,
                            "${if (ok) "Aprovado" else "Negado"}: ${abertos[0].titulo}", "Grilo")
                    }
                }
            }
        }
        Grilo.aoOuvir = {
            // Android usually blocks a background activity start; this is a best effort.
            runCatching {
                startActivity(Intent(this, GriloActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { YLogger.d(TAG, "rear play could not start listening: ${it.message}") }
        }
        YLogger.d(TAG, "rear screen bound")
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot =
        BrowserRoot("grilo", null)

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        result.sendResult(mutableListOf())
    }

    override fun onDestroy() {
        escopo.cancel()
        super.onDestroy()
    }

    private companion object { const val TAG = "GriloMedia" }
}
