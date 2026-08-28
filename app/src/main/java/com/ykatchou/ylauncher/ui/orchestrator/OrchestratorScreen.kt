package com.ykatchou.ylauncher.ui.orchestrator

import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykatchou.ylauncher.data.ruby.Despacho
import com.ykatchou.ylauncher.data.ruby.DespachoEstado
import com.ykatchou.ylauncher.data.ruby.Pedido
import com.ykatchou.ylauncher.data.ruby.Sessao
import com.ykatchou.ylauncher.data.ruby.SessaoEstado
import com.ykatchou.ylauncher.ui.theme.Y
import com.ykatchou.ylauncher.ui.theme.glass

private val PageBackground = Color(0xFF0F0E14)
private val OkGreen = Color(0xFF8FC07A)

private enum class Tab(val label: String) { FROTA("Frota"), PEDIDOS("Pedidos"), DESPACHOS("Despachos") }

@Composable
fun OrchestratorScreen(viewModel: OrchestratorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Run the connection only while the page is on screen.
    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }

    // The Ruby's voice, shown once.
    LaunchedEffect(state.fala) {
        state.fala?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeFala()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        when (state.phase) {
            Phase.LOADING -> Loading()
            Phase.PAIRING -> PairingView(
                initialBaseUrl = state.baseUrl,
                error = state.pairingError,
                onPair = viewModel::pair,
            )
            Phase.READY, Phase.UNREACHABLE -> ReadyView(state, viewModel)
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Y.accent)
    }
}

@Composable
private fun ReadyView(state: OrchestratorState, vm: OrchestratorViewModel) {
    var tab by remember { mutableStateOf(Tab.PEDIDOS) }

    Column(Modifier.fillMaxSize().padding(Y.space.lg)) {
        Header(
            offline = state.phase == Phase.UNREACHABLE,
            connected = state.connected,
            pedidos = state.pedidos.size,
        )
        Spacer(Modifier.height(Y.space.md))
        SegTabs(tab, onSelect = { tab = it })
        Spacer(Modifier.height(Y.space.md))

        when (tab) {
            Tab.FROTA -> FrotaList(state.sessoes, state.agora)
            Tab.PEDIDOS -> PedidosList(state.pedidos, onDecide = vm::decidir)
            Tab.DESPACHOS -> DespachosList(state.despachos, state.fluxos, onDisparar = vm::disparar)
        }
    }
}

@Composable
private fun Header(offline: Boolean, connected: Boolean, pedidos: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Capataz", style = Y.type.title, color = Y.text, fontWeight = FontWeight.Bold)
            val status = when {
                offline -> "fora da rede — normal, tentando…"
                connected -> "ao vivo"
                else -> "religando…"
            }
            val statusColor = when {
                offline -> Y.textDim
                connected -> OkGreen
                else -> Y.accent
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(statusColor),
                )
                Spacer(Modifier.width(6.dp))
                Text(status, style = Y.type.caption, color = statusColor)
            }
        }
        if (pedidos > 0) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(Y.radius.pill))
                    .background(Y.accent)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("$pedidos", style = Y.type.label, color = Y.onAccent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SegTabs(selected: Tab, onSelect: (Tab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Y.space.sm)) {
        Tab.entries.forEach { t ->
            val on = t == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Y.radius.chip))
                    .then(if (on) Modifier.background(Y.accent) else Modifier.glass(RoundedCornerShape(Y.radius.chip)))
                    .clickable { onSelect(t) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t.label,
                    style = Y.type.label,
                    color = if (on) Y.onAccent else Y.textDim,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// --- Frota -------------------------------------------------------------------------------------

@Composable
private fun FrotaList(sessoes: List<Sessao>, agora: Long) {
    if (sessoes.isEmpty()) {
        Empty("nenhuma sessão na frota")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Y.space.sm),
        contentPadding = PaddingValues(bottom = Y.space.xxl),
    ) {
        items(sessoes, key = { it.id }) { s -> SessaoRow(s, agora) }
    }
}

@Composable
private fun SessaoRow(s: Sessao, agora: Long) {
    val (glyph, color) = when (s.estado) {
        SessaoEstado.TRAVADA -> "◆" to Y.warn
        SessaoEstado.PERDIDA -> "✕" to Y.warn
        SessaoEstado.RODANDO -> "●" to OkGreen
        SessaoEstado.OCIOSA -> "○" to Y.textDim
        SessaoEstado.TERMINADA -> "·" to Y.textFaint
        SessaoEstado.DESCONHECIDO -> "?" to Y.textFaint
    }
    Row(
        Modifier.glass(RoundedCornerShape(Y.radius.card)).fillMaxWidth().padding(Y.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, color = color, style = Y.type.subtitle)
        Spacer(Modifier.width(Y.space.md))
        Column(Modifier.weight(1f)) {
            Text(s.nome.ifEmpty { s.id.take(8) }, style = Y.type.body, color = Y.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${s.maquina} · ${s.cwd}",
                style = Y.type.caption,
                color = Y.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Y.space.sm))
        // Two honest clocks. The primary is time-in-state (agora − vista_em) — all the server can
        // affirm. The secondary is how long the session has existed (agora − iniciada_em): the
        // shocking "50 dias" context. We never claim "travada há 50 dias" — the server does not
        // know when it actually stuck (it was already blocked when Ruby first saw it), and inventing
        // that precision would be a lie. Only show the second clock when it adds something.
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatDur(agora - s.vistaEm),
                style = Y.type.caption,
                color = if (s.estado.atencao) Y.warn else Y.textDim,
            )
            val aberta = agora - s.iniciadaEm
            if (aberta - (agora - s.vistaEm) > 3600) {
                Text(
                    "aberta há ${formatDur(aberta)}",
                    style = Y.type.caption,
                    color = Y.textFaint,
                )
            }
        }
    }
}

// --- Pedidos -----------------------------------------------------------------------------------

@Composable
private fun PedidosList(pedidos: List<Pedido>, onDecide: (String, Boolean) -> Unit) {
    if (pedidos.isEmpty()) {
        Empty("nada esperando você — tudo tranquilo")
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Y.space.md),
        contentPadding = PaddingValues(bottom = Y.space.xxl),
    ) {
        items(pedidos, key = { it.id }) { p -> PedidoCard(p, onDecide) }
    }
}

@Composable
private fun PedidoCard(p: Pedido, onDecide: (String, Boolean) -> Unit) {
    Column(Modifier.glass().fillMaxWidth().padding(Y.space.lg)) {
        Text(p.titulo, style = Y.type.subtitle, color = Y.text, fontWeight = FontWeight.Bold)
        if (p.detalhe.isNotBlank()) {
            Spacer(Modifier.height(Y.space.sm))
            // Model-written text: rendered as plain text, never HTML. Can be long; the list scrolls.
            Text(p.detalhe, style = Y.type.bodySm, color = Y.textDim)
        }
        Spacer(Modifier.height(Y.space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Y.space.sm)) {
            ActionButton("Negar", filled = false, modifier = Modifier.weight(1f)) { onDecide(p.id, false) }
            ActionButton("Permitir", filled = true, modifier = Modifier.weight(1f)) { onDecide(p.id, true) }
        }
    }
}

@Composable
private fun ActionButton(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(Y.radius.pill))
            .then(
                if (filled) Modifier.background(Y.accent)
                else Modifier.border(1.dp, Y.glassEdge, RoundedCornerShape(Y.radius.pill)),
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Y.type.label,
            color = if (filled) Y.onAccent else Y.text,
            fontWeight = FontWeight.Bold,
        )
    }
}

// --- Despachos ---------------------------------------------------------------------------------

@Composable
private fun DespachosList(despachos: List<Despacho>, fluxos: List<String>, onDisparar: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        if (fluxos.isNotEmpty()) {
            Text("disparar", style = Y.type.caption, color = Y.textFaint)
            Spacer(Modifier.height(Y.space.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Y.space.sm)) {
                fluxos.forEach { f -> FluxoChip(f) { onDisparar(f) } }
            }
            Spacer(Modifier.height(Y.space.md))
        }
        if (despachos.isEmpty()) {
            Empty("nenhum despacho ainda")
            return
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Y.space.sm),
            contentPadding = PaddingValues(bottom = Y.space.xxl),
        ) {
            items(despachos, key = { it.id }) { d -> DespachoRow(d) }
        }
    }
}

@Composable
private fun FluxoChip(fluxo: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(Y.radius.chip))
            .border(1.dp, Y.accent, RoundedCornerShape(Y.radius.chip))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text("▸ $fluxo", style = Y.type.label, color = Y.accent, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun DespachoRow(d: Despacho) {
    val pillColor = when {
        d.estado.atencao -> Y.warn
        d.estado.ativo -> OkGreen
        else -> Y.textDim
    }
    Row(
        Modifier.glass(RoundedCornerShape(Y.radius.card)).fillMaxWidth().padding(Y.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(d.fluxo, style = Y.type.body, color = Y.text, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = d.motivo ?: d.no
            if (sub != null) {
                Text(sub, style = Y.type.caption, color = Y.textFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Y.space.sm))
        Box(
            Modifier
                .clip(RoundedCornerShape(Y.radius.pill))
                .border(1.dp, pillColor, RoundedCornerShape(Y.radius.pill))
                .padding(horizontal = 9.dp, vertical = 3.dp),
        ) {
            Text(d.estado.name.lowercase(), style = Y.type.caption, color = pillColor)
        }
    }
}

// --- Pairing -----------------------------------------------------------------------------------

@Composable
private fun PairingView(initialBaseUrl: String, error: String?, onPair: (String, String, String) -> Unit) {
    var baseUrl by remember { mutableStateOf(initialBaseUrl) }
    var codigo by remember { mutableStateOf("") }
    var nome by remember { mutableStateOf("celular do gero") }

    Column(
        Modifier.fillMaxSize().padding(Y.space.xl),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Parear com a Ruby", style = Y.type.title, color = Y.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Y.space.sm))
        Text(
            "O código de 6 hex aparece no log do servidor. Ele é de uso único.",
            style = Y.type.bodySm,
            color = Y.textDim,
        )
        Spacer(Modifier.height(Y.space.xl))

        Field("Endereço do Dell", baseUrl, onChange = { baseUrl = it })
        Spacer(Modifier.height(Y.space.md))
        Field("Código de pareamento", codigo, onChange = { codigo = it.uppercase() }, caps = true)
        Spacer(Modifier.height(Y.space.md))
        Field("Nome deste aparelho", nome, onChange = { nome = it })

        if (error != null) {
            Spacer(Modifier.height(Y.space.md))
            Text(error, style = Y.type.bodySm, color = Y.warn)
        }

        Spacer(Modifier.height(Y.space.xl))
        ActionButton("Parear", filled = true, modifier = Modifier.fillMaxWidth()) {
            onPair(baseUrl, codigo, nome)
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, caps: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Y.textDim) },
        singleLine = true,
        keyboardOptions = if (caps) {
            KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        } else {
            KeyboardOptions.Default
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Y.text,
            unfocusedTextColor = Y.text,
            focusedBorderColor = Y.accent,
            unfocusedBorderColor = Y.glassEdge,
            cursorColor = Y.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

// --- shared ------------------------------------------------------------------------------------

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = Y.type.body, color = Y.textFaint)
    }
}

/** "parado há X" in the coarsest honest unit. Negative clocks (skew) read as "agora". */
private fun formatDur(seconds: Long): String = when {
    seconds < 60 -> "agora"
    seconds < 3600 -> "${seconds / 60} min"
    seconds < 86_400 -> "${seconds / 3600} h"
    else -> "${seconds / 86_400} d"
}
