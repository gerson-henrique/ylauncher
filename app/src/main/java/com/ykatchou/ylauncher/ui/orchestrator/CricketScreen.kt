package com.ykatchou.ylauncher.ui.orchestrator

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykatchou.ylauncher.data.ruby.Despacho
import com.ykatchou.ylauncher.data.ruby.Pedido
import com.ykatchou.ylauncher.data.ruby.Sessao
import com.ykatchou.ylauncher.data.ruby.SessaoEstado
import com.ykatchou.ylauncher.ui.theme.ProverbBrush
import com.ykatchou.ylauncher.ui.theme.Y

/**
 * Cricket (蛩) — the pocket foreman, as a MURAL, not a chat. A feed of cards you mostly tap: an
 * agent waiting on your permission, the fleet at a glance, what is running, and Cricket's remarks.
 * The keyboard only appears when you tap the command bar to send a free-text order (送). Tinta:
 * ink on warm paper, seal red the only accent.
 */
@Composable
fun CricketScreen(viewModel: OrchestratorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Y.paperTop, Y.paperBottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        when (state.phase) {
            Phase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Y.seal)
            }
            Phase.PAIRING -> Pairing(state.baseUrl, state.pairingError, viewModel::pair)
            Phase.READY, Phase.UNREACHABLE -> Mural(state, viewModel)
        }
    }
}

@Composable
private fun Mural(state: OrchestratorState, vm: OrchestratorViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = Y.space.lg)) {
        Header(offline = state.phase == Phase.UNREACHABLE, connected = state.connected)

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Y.space.sm),
            contentPadding = PaddingValues(vertical = Y.space.md),
        ) {
            // Approvals first — the reason the mural exists.
            items(state.pedidos, key = { "p_${it.id}" }) { p -> ApprovalCard(p, vm::decidir) }

            // Fleet at a glance.
            val running = state.sessoes.count { it.estado == SessaoEstado.RODANDO }
            val stuck = state.sessoes.count { it.estado.atencao }
            if (state.sessoes.isNotEmpty()) {
                item(key = "fleet") { FleetCard(running, stuck) }
            }

            // What is running/paused.
            val active = state.despachos.filter { it.estado.ativo || it.estado.atencao }.take(4)
            items(active, key = { "d_${it.id}" }) { d -> DespachoCard(d) }

            // Cricket's remarks + your commands.
            items(state.recados, key = { "r_${it.id}" }) { r -> RecadoCard(r) }

            if (state.pedidos.isEmpty() && state.sessoes.isEmpty() && state.recados.isEmpty()) {
                item(key = "empty") { Empty(if (state.phase == Phase.UNREACHABLE) "fora da rede — normal, tentando…" else "tudo tranquilo") }
            }
        }

        CommandBar(onSend = vm::mandar)
    }
}

@Composable
private fun Header(offline: Boolean, connected: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Y.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.rotate(-3f).size(24.dp).border(1.5.dp, Y.seal, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("蛩", style = Y.type.subtitle.copy(fontFamily = ProverbBrush), color = Y.seal) }
        Spacer(Modifier.width(Y.space.sm))
        Text("Cricket", style = Y.type.title, color = Y.inkStrong, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        val (dot, label) = when {
            offline -> Y.inkFaint to "fora da rede"
            connected -> Y.jade to "frota viva"
            else -> Y.accent to "religando…"
        }
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Y.type.caption, color = dot, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ApprovalCard(p: Pedido, onDecide: (String, Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Y.radius.card))
            .background(Y.paperTop.copy(alpha = 0.7f))
            .border(1.dp, Y.seal.copy(alpha = 0.5f), RoundedCornerShape(Y.radius.card))
            .padding(Y.space.md),
    ) {
        Text("travado · espera decisão", style = Y.type.caption, color = Y.seal, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(Y.space.xs))
        Text(p.titulo, style = Y.type.subtitle.copy(fontFamily = ProverbBrush), color = Y.inkStrong, fontWeight = FontWeight.Bold)
        if (p.detalhe.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(p.detalhe, style = Y.type.bodySm, color = Y.inkDim, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Y.space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Y.space.lg), modifier = Modifier.align(Alignment.End)) {
            Text("Negar", style = Y.type.label, color = Y.inkDim, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDecide(p.id, false) })
            Text("Permitir", style = Y.type.label, color = Y.seal, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDecide(p.id, true) })
        }
    }
}

@Composable
private fun FleetCard(running: Int, stuck: Int) {
    PaperCard {
        Text("FROTA", style = Y.type.caption, color = Y.inkFaint, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$running rodando", style = Y.type.body, color = Y.jade, fontWeight = FontWeight.Bold)
            if (stuck > 0) {
                Text("  ·  $stuck travado", style = Y.type.body, color = Y.seal, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DespachoCard(d: Despacho) {
    PaperCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.fluxo, style = Y.type.body, color = Y.ink, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                (d.motivo ?: d.no)?.let { Text(it, style = Y.type.caption, color = Y.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Text(d.estado.name.lowercase(), style = Y.type.caption,
                color = if (d.estado.atencao) Y.seal else Y.jade, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun RecadoCard(r: Recado) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (r.mine) Arrangement.End else Arrangement.Start) {
        Text(
            text = r.texto,
            style = Y.type.bodySm,
            color = if (r.mine) Y.inkDim else Y.ink,
            fontWeight = if (r.mine) FontWeight.Normal else FontWeight.Medium,
            modifier = Modifier
                .then(if (r.mine) Modifier.padding(start = Y.space.xl) else Modifier.padding(end = Y.space.xl)),
        )
    }
}

@Composable
private fun PaperCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(Y.radius.card))
            .background(Y.paperTop.copy(alpha = 0.6f))
            .border(1.dp, Y.inkFaint.copy(alpha = 0.35f), RoundedCornerShape(Y.radius.card))
            .padding(Y.space.md),
        content = content,
    )
}

@Composable
private fun CommandBar(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val fire = { if (text.isNotBlank()) { onSend(text); text = "" } }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Y.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("manda um comando…", color = Y.inkFaint) },
            textStyle = LocalTextStyle.current.copy(color = Y.ink),
            maxLines = 3,
            keyboardActions = KeyboardActions(onSend = { fire() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Y.paperTop.copy(alpha = 0.7f),
                unfocusedContainerColor = Y.paperTop.copy(alpha = 0.5f),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                cursorColor = Y.seal,
            ),
            shape = RoundedCornerShape(Y.radius.pill),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Y.space.sm))
        // Send = a seal stamp (送), rotated like the other stamps.
        Box(
            Modifier.rotate(-3f).size(38.dp).clip(RoundedCornerShape(7.dp)).background(Y.seal).clickable { fire() },
            contentAlignment = Alignment.Center,
        ) { Text("送", style = Y.type.subtitle.copy(fontFamily = ProverbBrush), color = Y.paperTop) }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = Y.space.xxl), contentAlignment = Alignment.Center) {
        Text(text, style = Y.type.body, color = Y.inkFaint)
    }
}

// --- pairing (first run) -----------------------------------------------------------------------

@Composable
private fun Pairing(initialBaseUrl: String, error: String?, onPair: (String, String, String) -> Unit) {
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var codigo by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("celular do gero") }
    Column(Modifier.fillMaxSize().padding(Y.space.xl), verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.rotate(-3f).size(26.dp).border(1.5.dp, Y.seal, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("蛩", style = Y.type.subtitle.copy(fontFamily = ProverbBrush), color = Y.seal) }
            Spacer(Modifier.width(Y.space.sm))
            Text("Parear o Cricket", style = Y.type.title, color = Y.inkStrong, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(Y.space.sm))
        Text("O código de 6 hex aparece no log do servidor. É de uso único.", style = Y.type.bodySm, color = Y.inkDim)
        Spacer(Modifier.height(Y.space.lg))
        PairField("Endereço do Dell", baseUrl) { baseUrl = it }
        Spacer(Modifier.height(Y.space.md))
        PairField("Código de pareamento", codigo) { codigo = it.uppercase() }
        Spacer(Modifier.height(Y.space.md))
        PairField("Nome deste aparelho", nome) { nome = it }
        if (error != null) {
            Spacer(Modifier.height(Y.space.md)); Text(error, style = Y.type.bodySm, color = Y.seal)
        }
        Spacer(Modifier.height(Y.space.lg))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Y.radius.pill)).background(Y.seal)
                .clickable { onPair(baseUrl, codigo, nome) }.padding(vertical = Y.space.md),
            contentAlignment = Alignment.Center,
        ) { Text("Parear", style = Y.type.label, color = Y.paperTop, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun PairField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Y.inkDim) },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = Y.inkStrong),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Y.seal,
            unfocusedBorderColor = Y.inkFaint,
            cursorColor = Y.seal,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
