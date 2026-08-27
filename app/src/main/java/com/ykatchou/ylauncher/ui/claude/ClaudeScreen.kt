package com.ykatchou.ylauncher.ui.claude

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykatchou.ylauncher.data.running.ShizukuShell
import com.ykatchou.ylauncher.ui.theme.Y
import com.ykatchou.ylauncher.ui.theme.glass

private const val SHIZUKU_REQUEST_CODE = 6001

/** The page background: a solid near-black so a scrolling transcript stays readable over any wallpaper. */
private val PageBackground = Color(0xFF14121A)

@Composable
fun ClaudeScreen(viewModel: ClaudeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val shizukuReady by viewModel.shizukuReady.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle(initialValue = true)

    LaunchedEffect(Unit) { viewModel.refreshShizuku() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                shizukuReady = shizukuReady,
                onFixShizuku = {
                    ShizukuShell.requestPermission(SHIZUKU_REQUEST_CODE)
                    context.launchShizuku()
                    viewModel.refreshShizuku()
                },
                showReset = hasKey,
                onReset = viewModel::clearCredentials,
            )

            Transcript(lines = lines, modifier = Modifier.weight(1f))

            pending?.let { PendingBanner(it, onResolve = viewModel::resolvePending) }

            if (!hasKey) {
                ApiKeyPrompt(onSave = viewModel::saveCredentials)
            } else {
                InputRow(busy = busy, onSend = viewModel::send)
            }
        }
    }
}

@Composable
private fun Header(
    shizukuReady: Boolean,
    onFixShizuku: () -> Unit,
    showReset: Boolean,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Y.space.lg, vertical = Y.space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "Claude", style = Y.type.heading, color = Y.text)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Y.space.md),
        ) {
            if (showReset) {
                Text(
                    text = "trocar chave",
                    style = Y.type.caption,
                    color = Y.textDim,
                    modifier = Modifier.clickable { onReset() },
                )
            }
            val dot = if (shizukuReady) Y.accent else Y.warn
            val label = if (shizukuReady) "Shizuku ok" else "Shizuku fora — tocar pra ligar"
            Row(
                modifier = Modifier
                    .then(if (shizukuReady) Modifier else Modifier.clickable { onFixShizuku() })
                    .clip(RoundedCornerShape(Y.radius.pill))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = Y.space.md, vertical = Y.space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Y.space.sm),
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dot))
                Text(text = label, style = Y.type.caption, color = Y.textDim)
            }
        }
    }
}

@Composable
private fun Transcript(lines: List<ChatLine>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }
    if (lines.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Peça pro Claude olhar o aparelho.\nEle executa via Shizuku, e pede confirmação\nantes de qualquer comando destrutivo.",
                style = Y.type.bodySm,
                color = Y.textFaint,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Y.space.lg),
        verticalArrangement = Arrangement.spacedBy(Y.space.md),
    ) {
        itemsIndexed(lines) { _, line -> Bubble(line) }
    }
}

@Composable
private fun Bubble(line: ChatLine) {
    when (line.speaker) {
        Speaker.USER -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = line.text,
                style = Y.type.body,
                color = Y.text,
                modifier = Modifier
                    .clip(RoundedCornerShape(Y.radius.card))
                    .background(Y.accent.copy(alpha = 0.16f))
                    .border(0.8.dp, Y.accent.copy(alpha = 0.35f), RoundedCornerShape(Y.radius.card))
                    .padding(horizontal = Y.space.md, vertical = Y.space.sm),
            )
        }

        Speaker.CLAUDE -> Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = line.text,
                style = Y.type.body,
                color = Y.text,
                modifier = Modifier
                    .glass(RoundedCornerShape(Y.radius.card))
                    .padding(horizontal = Y.space.md, vertical = Y.space.sm),
            )
        }

        Speaker.TOOL -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Y.radius.chip))
                .background(Color.Black.copy(alpha = 0.30f))
                .border(0.8.dp, Y.glassEdge, RoundedCornerShape(Y.radius.chip))
                .padding(Y.space.md),
            verticalArrangement = Arrangement.spacedBy(Y.space.xs),
        ) {
            Text(text = "\$ ${line.text}", style = Y.type.caption, color = Y.accent)
            line.detail?.let { Text(text = it, style = Y.type.caption, color = Y.textDim, maxLines = 12) }
        }

        Speaker.ERROR -> Text(text = line.text, style = Y.type.bodySm, color = Y.warn)
    }
}

@Composable
private fun PendingBanner(pending: PendingCommand, onResolve: (Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Y.space.lg, vertical = Y.space.sm)
            .clip(RoundedCornerShape(Y.radius.card))
            .background(Y.warn.copy(alpha = 0.14f))
            .border(0.8.dp, Y.warn.copy(alpha = 0.5f), RoundedCornerShape(Y.radius.card))
            .padding(Y.space.md),
        verticalArrangement = Arrangement.spacedBy(Y.space.sm),
    ) {
        Text(text = "Comando perigoso (${pending.reason})", style = Y.type.label, color = Y.warn)
        Text(text = "\$ ${pending.command}", style = Y.type.caption, color = Y.text)
        Row(horizontalArrangement = Arrangement.spacedBy(Y.space.md)) {
            Pill(text = "Negar", tint = Y.textDim) { onResolve(false) }
            Pill(text = "Permitir", tint = Y.warn) { onResolve(true) }
        }
    }
}

@Composable
private fun Pill(text: String, tint: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = Y.type.label,
        color = tint,
        modifier = Modifier
            .clip(RoundedCornerShape(Y.radius.pill))
            .border(0.8.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(Y.radius.pill))
            .clickable { onClick() }
            .padding(horizontal = Y.space.lg, vertical = Y.space.sm),
    )
}

@Composable
private fun ApiKeyPrompt(onSave: (String, String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var workspace by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Y.space.lg),
        verticalArrangement = Arrangement.spacedBy(Y.space.sm),
    ) {
        Text(text = "Cole sua chave da API Anthropic", style = Y.type.label, color = Y.text)
        Text(
            text = "Fica cifrada no aparelho (Keystore), nunca no repositório.",
            style = Y.type.caption,
            color = Y.textDim,
        )
        TextField(
            value = key,
            onValueChange = { key = it },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text("sk-ant-…", style = Y.type.body, color = Y.textFaint) },
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Y.space.sm)) {
            TextField(
                value = workspace,
                onValueChange = { workspace = it },
                singleLine = true,
                placeholder = { Text("Workspace ID (se a chave pedir)", style = Y.type.bodySm, color = Y.textFaint) },
                colors = fieldColors(),
                modifier = Modifier.weight(1f),
            )
            SendButton(enabled = key.isNotBlank()) {
                onSave(key, workspace)
                key = ""
                workspace = ""
            }
        }
    }
}

@Composable
private fun InputRow(busy: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Y.space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Y.space.sm),
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Fale com o Claude…", style = Y.type.body, color = Y.textFaint) },
            colors = fieldColors(),
            modifier = Modifier.weight(1f),
            enabled = !busy,
        )
        if (busy) {
            CircularProgressIndicator(color = Y.accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        } else {
            SendButton(enabled = text.isNotBlank()) {
                onSend(text)
                text = ""
            }
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Y.accent else Y.textFaint
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (enabled) 1f else 0.2f))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "➤", style = Y.type.title, color = if (enabled) Y.onAccent else Y.textDim)
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.White.copy(alpha = 0.06f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
    disabledContainerColor = Color.White.copy(alpha = 0.03f),
    focusedTextColor = Y.text,
    unfocusedTextColor = Y.text,
    cursorColor = Y.accent,
    focusedIndicatorColor = Y.accent,
    unfocusedIndicatorColor = Color.Transparent,
)

private fun android.content.Context.launchShizuku() {
    val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent != null) startActivity(intent)
}
