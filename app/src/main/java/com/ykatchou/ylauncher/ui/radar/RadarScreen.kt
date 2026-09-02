package com.ykatchou.ylauncher.ui.radar

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykatchou.ylauncher.ui.theme.ProverbBrush
import com.ykatchou.ylauncher.ui.theme.Y

/**
 * Sala de Máquinas (機關) — the network readout that also *acts*. It shows Shizuku's health and lets
 * you open it, flips wireless debugging on/off through Shizuku (the reconnection pain, killed), and
 * lists the live connections. All in Tinta: ink on warm paper, seal red the only accent.
 */
@Composable
fun RadarScreen(viewModel: NetRadarViewModel = hiltViewModel()) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val shizuku by viewModel.shizuku.collectAsStateWithLifecycle()
    val wifiDebugOn by viewModel.wifiDebugOn.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                onShizukuTap = {
                    when (shizuku) {
                        ShizukuState.NEEDS_PERMISSION -> viewModel.requestShizukuPermission()
                        else -> openShizuku()
                    }
                },
                onWifiToggle = viewModel::toggleWifiDebug,
            )
            Spacer(Modifier.height(Y.space.md))
            SectionLabel("conexões")
            Feed(feed = feed, available = available, modifier = Modifier.weight(1f))
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
                        wifiDebugOn == true -> "ligado"
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = Y.type.caption,
        color = Y.inkFaint,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(bottom = Y.space.xs),
    )
}

@Composable
private fun Feed(feed: List<RadarLine>, available: Boolean, modifier: Modifier = Modifier) {
    if (!available) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Shizuku fora — sem leitura de rede", style = Y.type.bodySm, color = Y.inkFaint)
        }
        return
    }
    if (feed.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("quieto — nenhuma conexão nova", style = Y.type.bodySm, color = Y.inkFaint)
        }
        return
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = Y.space.xl)) {
        items(feed, key = { it.id }) { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = Y.space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(5.dp).clip(CircleShape)
                        .background(if (line.fresh) Y.seal else Y.inkFaint),
                )
                Spacer(Modifier.width(Y.space.sm))
                Text(
                    line.app,
                    style = Y.type.bodySm,
                    color = if (line.fresh) Y.seal else Y.ink,
                    fontWeight = if (line.fresh) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Y.space.sm))
                Text(
                    line.owner ?: "${line.remote}:${line.port}",
                    style = Y.type.caption,
                    color = Y.inkDim,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
