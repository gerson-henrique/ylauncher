package com.ykatchou.ylauncher.data.ponte

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives copies reported by the Tinta keyboard and sends them to the Mac.
 *
 * Only the input method may read the clipboard in the background on Android 10+, and the keyboard
 * has no network permission — so it hands the text here, the app that does. The receiver is
 * guarded by a signature permission: only an app signed with the same key can deliver to it.
 */
@AndroidEntryPoint
class PonteClipReceiver : BroadcastReceiver() {

    @Inject lateinit var ponte: Ponte

    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT)?.takeIf { it.isNotEmpty() } ?: return
        val pending = goAsync()
        ponte.scope.launch {
            try {
                ponte.sendClip(text)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.ykatchou.ylauncher.ponte.CLIP"
        const val EXTRA_TEXT = "texto"
    }
}
