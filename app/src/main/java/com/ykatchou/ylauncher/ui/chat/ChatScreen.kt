package com.ykatchou.ylauncher.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykatchou.ylauncher.data.ruby.Conversa
import com.ykatchou.ylauncher.ui.theme.Y
import com.ykatchou.ylauncher.ui.theme.glass
import kotlinx.coroutines.launch

private val PageBackground = Color(0xFF12100F)
private val DrawerBackground = Color(0xFF1A1614)

@Composable
fun ChatScreen(viewModel: ChatViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.setActive(true)
        onDispose { viewModel.setActive(false) }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Conversa?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Only the ☰ button (and a scrim tap) work the drawer. Edge-swipe-to-open would eat the
        // pager's horizontal swipe, trapping you on the Ruby page with no way back to the home.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = DrawerBackground) {
                ConversasDrawer(
                    conversas = state.conversas,
                    activeId = state.activeId,
                    onOpen = { id -> viewModel.openConversa(id); scope.launch { drawerState.close() } },
                    onNova = { viewModel.novaConversa(); scope.launch { drawerState.close() } },
                    onDelete = { pendingDelete = it },
                )
            }
        },
    ) {
        Chat(
            state = state,
            onMenu = { scope.launch { drawerState.open() } },
            onNova = viewModel::novaConversa,
            onSend = viewModel::send,
            onDecide = viewModel::decide,
        )
    }

    pendingDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                TextButton(onClick = { viewModel.excluirConversa(conv.id); pendingDelete = null }) {
                    Text("Excluir", color = Y.warn)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar", color = Y.textDim) } },
            title = { Text("Excluir conversa?", color = Y.text) },
            text = { Text("\"${conv.titulo}\" e as mensagens dela somem do dispositivo.", color = Y.textDim) },
            containerColor = DrawerBackground,
        )
    }
}

@Composable
private fun Chat(
    state: ChatState,
    onMenu: () -> Unit,
    onNova: () -> Unit,
    onSend: (String) -> Unit,
    onDecide: (String, Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.items.size, state.thinking) {
        val count = state.items.size + if (state.thinking) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    val title = state.conversas.firstOrNull { it.id == state.activeId }?.titulo ?: "Ruby"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = Y.space.lg),
    ) {
        Header(title = title, connected = state.connected, onMenu = onMenu, onNova = onNova)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Y.space.sm),
            contentPadding = PaddingValues(vertical = Y.space.md),
        ) {
            if (state.items.isEmpty()) item { Hint() }
            items(state.items, key = { it.key }) { item ->
                when (item) {
                    is UserMsg -> Bubble(item.texto, mine = true)
                    is RubyMsg -> RubyBubble(item)
                    is SystemLine -> SystemRow(item)
                    is ApprovalCard -> Approval(item, onDecide = onDecide)
                }
            }
            if (state.thinking) item { Thinking() }
        }

        InputBar(onSend = onSend)
    }
}

@Composable
private fun Header(title: String, connected: Boolean, onMenu: () -> Unit, onNova: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Y.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton("☰", onMenu) // hamburger
        Spacer(Modifier.width(Y.space.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = Y.type.heading, color = Y.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (connected) Y.ok else Y.accent))
                Spacer(Modifier.width(5.dp))
                Text(if (connected) "ao vivo" else "religando…", style = Y.type.caption, color = if (connected) Y.ok else Y.accent)
            }
        }
        GlyphButton("+", onNova)
    }
}

@Composable
private fun GlyphButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = Y.type.title, color = Y.text)
    }
}

@Composable
private fun ConversasDrawer(
    conversas: List<Conversa>,
    activeId: String?,
    onOpen: (String) -> Unit,
    onNova: () -> Unit,
    onDelete: (Conversa) -> Unit,
) {
    Column(Modifier.fillMaxHeight().padding(Y.space.lg)) {
        Text("conversas", style = Y.type.caption, color = Y.textFaint)
        Spacer(Modifier.height(Y.space.md))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Y.radius.chip))
                .border(1.dp, Y.accent, RoundedCornerShape(Y.radius.chip))
                .clickable { onNova() }
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("+ nova conversa", style = Y.type.label, color = Y.accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(Y.space.md))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(Y.space.xs)) {
            items(conversas, key = { it.id }) { c ->
                val active = c.id == activeId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Y.radius.chip))
                        .then(if (active) Modifier.background(Y.glassFillTop) else Modifier)
                        .clickable { onOpen(c.id) }
                        .padding(horizontal = Y.space.md, vertical = Y.space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            c.titulo,
                            style = Y.type.body,
                            color = if (active) Y.accent else Y.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${c.mensagens} msg", style = Y.type.caption, color = Y.textFaint)
                    }
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).clickable { onDelete(c) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = Y.type.subtitle, color = Y.textDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint() {
    Box(Modifier.fillMaxWidth().padding(top = Y.space.xxl), contentAlignment = Alignment.Center) {
        Text("fale com a Ruby", style = Y.type.body, color = Y.textFaint)
    }
}

@Composable
private fun Bubble(texto: String, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape(mine))
                .then(if (mine) Modifier.background(Y.accent) else Modifier.glass(bubbleShape(false)))
                .padding(horizontal = Y.space.md, vertical = Y.space.sm),
        ) {
            Text(texto, style = Y.type.body, color = if (mine) Y.onAccent else Y.text)
        }
    }
}

@Composable
private fun RubyBubble(item: RubyMsg) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape(false))
                .glass(bubbleShape(false))
                .padding(horizontal = Y.space.md, vertical = Y.space.sm),
        ) {
            if (item.texto.isNotEmpty()) Text(item.texto, style = Y.type.body, color = Y.text)
            item.gesto?.let {
                Text(it, style = Y.type.bodySm, color = Y.ruby, fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

@Composable
private fun SystemRow(item: SystemLine) {
    val color = when (item.kind) {
        SysKind.ESTADO -> Y.textFaint
        SysKind.AVISO -> Y.accent
        SysKind.ERRO -> Y.warn
        SysKind.RECADO -> Y.ruby
    }
    val text = if (item.kind == SysKind.RECADO) "Ruby: ${item.texto}" else item.texto
    val style = if (item.kind == SysKind.RECADO) FontStyle.Italic else FontStyle.Normal
    Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(text, style = Y.type.caption, color = color, fontStyle = style)
    }
}

@Composable
private fun Approval(item: ApprovalCard, onDecide: (String, Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().glass().padding(Y.space.lg)) {
        Text(item.titulo, style = Y.type.subtitle, color = Y.text, fontWeight = FontWeight.Bold)
        if (item.detalhe.isNotBlank()) {
            Spacer(Modifier.height(Y.space.sm))
            Text(item.detalhe, style = Y.type.bodySm, color = Y.textDim)
        }
        Spacer(Modifier.height(Y.space.md))
        if (item.decided) {
            Text("respondido", style = Y.type.label, color = Y.textFaint)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Y.space.sm)) {
                Pill("Negar", filled = false, modifier = Modifier.weight(1f)) { onDecide(item.id, false) }
                Pill("Permitir", filled = true, modifier = Modifier.weight(1f)) { onDecide(item.id, true) }
            }
        }
    }
}

@Composable
private fun Pill(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(Y.radius.pill))
            .then(if (filled) Modifier.background(Y.accent) else Modifier.border(1.dp, Y.glassEdge, RoundedCornerShape(Y.radius.pill)))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Y.type.label, color = if (filled) Y.onAccent else Y.text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Thinking() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier.clip(bubbleShape(false)).glass(bubbleShape(false)).padding(horizontal = Y.space.md, vertical = Y.space.sm),
        ) {
            Text("pensando…", style = Y.type.body, color = Y.textFaint, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
private fun InputBar(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val fire = {
        if (text.isNotBlank()) {
            onSend(text)
            text = ""
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Y.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("mensagem…", color = Y.textFaint) },
            textStyle = LocalTextStyle.current.copy(color = Y.text),
            maxLines = 4,
            keyboardActions = KeyboardActions(onSend = { fire() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Y.glassFillTop,
                unfocusedContainerColor = Y.glassFillTop,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Y.accent,
            ),
            shape = RoundedCornerShape(Y.radius.panel),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Y.space.sm))
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Y.accent).clickable { fire() },
            contentAlignment = Alignment.Center,
        ) {
            Text("↑", style = Y.type.title, color = Y.onAccent, fontWeight = FontWeight.Bold)
        }
    }
}

private fun bubbleShape(mine: Boolean) = if (mine) {
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
} else {
    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
}
