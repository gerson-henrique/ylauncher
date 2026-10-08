package com.ykatchou.ylauncher.ui.radar

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykatchou.ylauncher.data.ponte.Ponte
import com.ykatchou.ylauncher.ui.theme.ProverbBrush
import com.ykatchou.ylauncher.ui.theme.Y

/**
 * Sala de Máquinas (機關) — bench tools the system hides. It shows Shizuku's health and lets you
 * open it, flips wireless debugging on/off through Shizuku (the reconnection pain, killed), and
 * opens the QR reader. All in Tinta: ink on warm paper, seal red the only accent.
 */
@Composable
fun RadarScreen(viewModel: NetRadarViewModel = hiltViewModel()) {
    val shizuku by viewModel.shizuku.collectAsStateWithLifecycle()
    val wifiDebugOn by viewModel.wifiDebugOn.collectAsStateWithLifecycle()
    val adbPort by viewModel.adbPort.collectAsStateWithLifecycle()
    val ponteName by viewModel.ponteName.collectAsStateWithLifecycle()
    val macUp by viewModel.macUp.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }

    val openShizuku = {
        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Unit
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Y.paperTop, Y.paperBottom)))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(Y.space.lg)) {
            Header()
            Spacer(Modifier.height(Y.space.md))
            Controls(
                shizuku = shizuku,
                wifiDebugOn = wifiDebugOn,
                adbPort = adbPort,
                onShizukuTap = {
                    when (shizuku) {
                        ShizukuState.NEEDS_PERMISSION -> viewModel.requestShizukuPermission()
                        else -> openShizuku()
                    }
                },
                onWifiToggle = viewModel::toggleWifiDebug,
            )
            Spacer(Modifier.height(Y.space.md))
            ToolTile(
                glyph = "読",
                label = "ler QR",
                sub = "link, wi‑fi, pix, texto",
                onClick = {
                    context.startActivity(
                        Intent(context, QrScannerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
            Spacer(Modifier.height(Y.space.md))
            PonteCard(
                name = ponteName,
                up = macUp,
                onMirror = viewModel::mirror,
                onControl = {
                    viewModel.controlMac(context) { host ->
                        val vnc = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("vnc://$host:5900"))
                            .setPackage(VNC_PACKAGE)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(vnc) }.onFailure {
                            Toast.makeText(context, "instale o bVNC pra ver a tela", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                onLock = viewModel::lockMac,
                onSleep = viewModel::sleepMac,
                onPlay = viewModel::playPause,
                onVolume = viewModel::volume,
                onSendClip = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    viewModel.sendClip(Ponte.shareableText(cm.primaryClip))
                },
                onForget = viewModel::forgetMac,
            )
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.rotate(-3f).size(24.dp)
                .border(1.5.dp, Y.seal, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("機", style = Y.type.subtitle.copy(fontFamily = ProverbBrush), color = Y.seal)
        }
        Spacer(Modifier.width(Y.space.sm))
        Text("Máquinas", style = Y.type.title, color = Y.inkStrong, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Controls(
    shizuku: ShizukuState,
    wifiDebugOn: Boolean?,
    adbPort: Int?,
    onShizukuTap: () -> Unit,
    onWifiToggle: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Y.space.sm)) {
        // Shizuku status chip — tap to open it (or grant permission).
        val (dot, label, sub) = when (shizuku) {
            ShizukuState.UP -> Triple(Y.jade, "Shizuku", "de pé")
            ShizukuState.NEEDS_PERMISSION -> Triple(Y.accent, "Shizuku", "toca pra permitir")
            ShizukuState.DOWN -> Triple(Y.seal, "Shizuku", "caiu · abrir")
        }
        ControlChip(modifier = Modifier.weight(1f), onClick = onShizukuTap) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(7.dp))
            Column {
                Text(label.uppercase(), style = Y.type.caption, color = Y.inkFaint, fontFamily = FontFamily.Monospace)
                Text(sub, style = Y.type.bodySm, color = Y.ink)
            }
        }
        // Wi-Fi debug toggle — only meaningful when Shizuku is up.
        val enabled = shizuku == ShizukuState.UP
        ControlChip(modifier = Modifier.weight(1f), onClick = { if (enabled) onWifiToggle() }) {
            Column(Modifier.weight(1f)) {
                Text("WI-FI DEBUG", style = Y.type.caption, color = Y.inkFaint, fontFamily = FontFamily.Monospace)
                Text(
                    when {
                        !enabled -> "precisa do Shizuku"
                        wifiDebugOn == true -> adbPort?.let { "ligado · :$it" } ?: "ligado"
                        wifiDebugOn == false -> "desligado"
                        else -> "—"
                    },
                    style = Y.type.bodySm,
                    color = if (enabled) Y.ink else Y.inkFaint,
                )
            }
            Toggle(on = enabled && wifiDebugOn == true, dim = !enabled)
        }
    }
}

@Composable
private fun ControlChip(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Y.radius.card))
            .background(Y.paperTop.copy(alpha = 0.6f))
            .border(1.dp, Y.inkFaint.copy(alpha = 0.4f), RoundedCornerShape(Y.radius.card))
            .clickable { onClick() }
            .padding(horizontal = Y.space.md, vertical = Y.space.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Toggle(on: Boolean, dim: Boolean) {
    val track = when {
        dim -> Y.inkFaint.copy(alpha = 0.35f)
        on -> Y.jade
        else -> Y.inkFaint.copy(alpha = 0.5f)
    }
    Box(
        Modifier.width(30.dp).height(17.dp).clip(RoundedCornerShape(999.dp)).background(track),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.padding(2.dp).size(13.dp).clip(CircleShape).background(Y.paperTop))
    }
}

/**
 * The bridge to the Mac (橋): who it is paired with and whether it answers, over the remote
 * buttons. Unpaired, it says how to pair instead — there is nothing to press until then.
 */
@Composable
private fun PonteCard(
    name: String?,
    up: Boolean?,
    onMirror: () -> Unit,
    onControl: () -> Unit,
    onLock: () -> Unit,
    onSleep: () -> Unit,
    onPlay: () -> Unit,
    onVolume: (Int) -> Unit,
    onSendClip: () -> Unit,
    onForget: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Y.radius.card))
            .background(Y.paperTop.copy(alpha = 0.6f))
            .border(1.dp, Y.inkFaint.copy(alpha = 0.4f), RoundedCornerShape(Y.radius.card))
            .padding(Y.space.md),
    ) {
        var confirmForget by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.rotate(-3f).size(30.dp)
                    .border(1.5.dp, Y.sealIndigo, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("橋", style = Y.type.subtitle.copy(fontFamily = ProverbBrush), color = Y.sealIndigo)
            }
            Spacer(Modifier.width(Y.space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    ("ponte" + (name?.let { " · $it" } ?: "")).uppercase(),
                    style = Y.type.caption,
                    color = Y.inkFaint,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (name != null) {
                        val dot = when (up) { true -> Y.jade; false -> Y.seal; null -> Y.inkFaint }
                        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        when {
                            name == null -> "rode hashi parear no Mac e leia o QR"
                            up == true -> "ao alcance"
                            up == false -> "fora de alcance"
                            else -> "procurando…"
                        },
                        style = Y.type.bodySm,
                        color = Y.ink,
                    )
                }
            }
            if (name != null) {
                Text(
                    if (confirmForget) "esquecer?" else "×",
                    style = Y.type.bodySm,
                    color = if (confirmForget) Y.seal else Y.inkFaint,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { if (confirmForget) onForget() else confirmForget = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (name != null) {
            Spacer(Modifier.height(Y.space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BridgeButton("鏡", "espelhar", onMirror, Modifier.weight(1f))
                BridgeButton("鎖", "bloquear", onLock, Modifier.weight(1f))
                BridgeButton("眠", "suspender", onSleep, Modifier.weight(1f))
                BridgeButton("写", "enviar clip", onSendClip, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BridgeButton("操", "controlar", onControl, Modifier.weight(1f))
                BridgeButton("⏯", "tocar", onPlay, Modifier.weight(1f))
                BridgeButton("−", "volume", { onVolume(-10) }, Modifier.weight(1f))
                BridgeButton("＋", "volume", { onVolume(10) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BridgeButton(glyph: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Y.inkFaint.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(glyph, style = Y.type.subtitle.copy(fontFamily = ProverbBrush), color = Y.sealIndigo)
        Text(label, style = Y.type.caption, color = Y.inkDim, maxLines = 1)
    }
}

/** bVNC (open source, from its author's GitHub releases) — the viewer for the Mac's screen. */
private const val VNC_PACKAGE = "com.iiordanov.freebVNC"

/** A bench tool: a seal glyph, its name and what it handles. */
@Composable
private fun ToolTile(glyph: String, label: String, sub: String, onClick: () -> Unit) {
    ControlChip(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Box(
            modifier = Modifier.rotate(-3f).size(30.dp).background(Y.seal, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, style = Y.type.subtitle.copy(fontFamily = ProverbBrush), color = Y.paperTop)
        }
        Spacer(Modifier.width(Y.space.md))
        Column {
            Text(label, style = Y.type.body, color = Y.inkStrong, fontWeight = FontWeight.Bold)
            Text(sub, style = Y.type.caption, color = Y.inkDim)
        }
    }
}
