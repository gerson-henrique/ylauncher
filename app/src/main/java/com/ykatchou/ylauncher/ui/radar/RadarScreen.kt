package com.ykatchou.ylauncher.ui.radar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykatchou.ylauncher.ui.theme.Y

private val PageBackground = Color(0xFF0F0E14)
private val FeedBackground = Color(0xFF0A0910)

@Composable
fun RadarScreen(viewModel: NetRadarViewModel = hiltViewModel()) {
    val feed by viewModel.feed.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()

    // Sample only while this page is on screen — the whole point is not to run in the background.
    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(Y.space.lg)) {
            Header(available = available, paused = paused, onPause = viewModel::togglePause)
            SummaryBar(summary)
            Feed(feed = feed, available = available, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Header(available: Boolean, paused: Boolean, onPause: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Y.space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        androidx.compose.material3.Text(text = "Radar", style = Y.type.heading, color = Y.text)
        val (dot, label) = when {
            !available -> Y.warn to "Shizuku fora"
            paused -> Y.textDim to "pausado"
            else -> Y.warn to "ao vivo"
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Y.radius.pill))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable { onPause() }
                .padding(horizontal = Y.space.md, vertical = Y.space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Y.space.sm),
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dot))
            androidx.compose.material3.Text(text = label, style = Y.type.caption, color = Y.textDim)
        }
    }
}

@Composable
private fun SummaryBar(s: RadarSummary) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Y.space.md),
        horizontalArrangement = Arrangement.spacedBy(Y.space.sm),
    ) {
        Chip(s.callsPerMin.toString(), "chamadas/min", Modifier.weight(1f))
        Chip(s.activeConns.toString(), "conexões", Modifier.weight(1f))
        Chip(s.topApp, "quem mais fala", Modifier.weight(1f))
    }
}

@Composable
private fun Chip(value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Y.radius.chip))
            .background(Color.White.copy(alpha = 0.04f))
            .border(0.8.dp, Y.glassEdge, RoundedCornerShape(Y.radius.chip))
            .padding(vertical = Y.space.sm, horizontal = Y.space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Text(
            text = value, style = Y.type.title, color = Y.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        androidx.compose.material3.Text(text = label, style = Y.type.caption, color = Y.textFaint)
    }
}

@Composable
private fun Feed(feed: List<RadarLine>, available: Boolean, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Y.radius.card))
            .background(FeedBackground)
            .border(0.8.dp, Y.glassEdge, RoundedCornerShape(Y.radius.card)),
    ) {
        when {
            !available -> Centered("Shizuku fora — o radar lê a tabela de sockets por ele.\nAtive o Shizuku pra ligar o radar.")
            feed.isEmpty() -> Centered("Ouvindo a rede…\nAs chamadas aparecem aqui quando os apps falarem.")
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(Y.space.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(feed, key = { it.id }) { line -> FeedRow(line) }
            }
        }
    }
}

@Composable
private fun FeedRow(line: RadarLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Y.radius.chip))
            .background(if (line.fresh) Y.accent.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = Y.space.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Y.space.sm),
    ) {
        Box(modifier = Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(colorFor(line.uid)))
        androidx.compose.material3.Text(
            text = line.app,
            style = Y.type.caption,
            color = Y.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.9f),
        )
        androidx.compose.material3.Text(text = "→", style = Y.type.caption, color = Y.textFaint)
        androidx.compose.material3.Text(
            text = if (line.remote.contains(':')) "[${line.remote}]:${line.port}" else "${line.remote}:${line.port}",
            style = Y.type.caption,
            color = Color(0xFF5AA1B8),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f),
        )
        androidx.compose.material3.Text(
            text = line.proto,
            style = Y.type.caption,
            color = Y.textFaint,
        )
    }
}

@Composable
private fun Centered(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            text = text, style = Y.type.bodySm, color = Y.textFaint, textAlign = TextAlign.Center,
            modifier = Modifier.padding(Y.space.xl),
        )
    }
}

/** A stable colour per app so the eye can track a talker down the feed. */
private fun colorFor(uid: Int): Color {
    val hue = Math.floorMod(uid * 47, 360).toFloat()
    return Color.hsv(hue, 0.5f, 0.7f)
}
