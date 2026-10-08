package com.ykatchou.ylauncher.ui.radar

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.ykatchou.ylauncher.data.ponte.Ponte
import com.ykatchou.ylauncher.data.qr.QrContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ykatchou.ylauncher.ui.theme.ProverbBrush
import com.ykatchou.ylauncher.ui.theme.Y
import com.ykatchou.ylauncher.ui.theme.YLauncherTheme
import java.util.concurrent.Executors

/**
 * The Sala de Máquinas QR reader (読). Full-screen camera; the first code it reads freezes the
 * analysis and shows a card with the one action that fits — nothing is opened or joined without a
 * tap, since a QR code is untrusted input.
 */
@AndroidEntryPoint
class QrScannerActivity : ComponentActivity() {

    @Inject lateinit var ponte: Ponte

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YLauncherTheme {
                ScannerScreen(onClose = ::finish, onPair = ::pair)
            }
        }
    }

    /** Pair with the Mac whose QR was read; close the reader once it answers. */
    private fun pair(code: QrContent.Ponte) {
        val app = applicationContext
        ponte.scope.launch {
            val ok = ponte.pair(code)
            withContext(Dispatchers.Main) {
                Toast.makeText(app, if (ok) "pareado com ${code.name}" else "o Mac não respondeu — o hashi está rodando?", Toast.LENGTH_LONG).show()
                if (ok) finish()
            }
        }
    }
}

@Composable
private fun ScannerScreen(onClose: () -> Unit, onPair: (QrContent.Ponte) -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) askCamera.launch(Manifest.permission.CAMERA) }

    var result by remember { mutableStateOf<QrContent?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(paused = result != null, onCode = { if (result == null) result = QrContent.parse(it) })
            Viewfinder(Modifier.align(Alignment.Center))
        } else {
            Text(
                "o leitor precisa da câmera",
                style = Y.type.bodySm,
                color = Y.paperTop,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Row(
            Modifier.statusBarsPadding().padding(Y.space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.rotate(-3f).size(26.dp).background(Y.seal, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("読", style = Y.type.subtitle.copy(fontFamily = ProverbBrush), color = Y.paperTop)
            }
            Spacer(Modifier.size(Y.space.sm))
            Text("ler QR", style = Y.type.title, color = Y.paperTop, fontWeight = FontWeight.Bold)
        }

        result?.let { content ->
            ResultCard(
                content = content,
                onPair = onPair,
                onAgain = { result = null },
                onDone = onClose,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Corner brackets in seal red, so the eye knows where to put the code. */
@Composable
private fun Viewfinder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(240.dp)
            .border(2.dp, Y.seal.copy(alpha = 0.85f), RoundedCornerShape(18.dp)),
    )
}

@Composable
private fun CameraPreview(paused: Boolean, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val pausedNow by androidx.compose.runtime.rememberUpdatedState(paused)
    val onCodeNow by androidx.compose.runtime.rememberUpdatedState(onCode)

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            val reader = MultiFormatReader().apply {
                setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
            }
            analysis.setAnalyzer(executor) { image ->
                val text = if (pausedNow) null else decode(reader, image)
                image.close()
                if (text != null) ContextCompat.getMainExecutor(context).execute { onCodeNow(text) }
            }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Decode the luminance (Y) plane only — QR needs no colour, and ZXing reads codes at any rotation. */
private fun decode(reader: MultiFormatReader, image: ImageProxy): String? {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val width = image.width
    val height = image.height
    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
    val source = PlanarYUVLuminanceSource(bytes, rowStride, height, 0, 0, width, height, false)
    return try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }
}

@Composable
private fun ResultCard(
    content: QrContent,
    onPair: (QrContent.Ponte) -> Unit,
    onAgain: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val (kind, shown, actionLabel) = when (content) {
        is QrContent.Link -> Triple("link", content.raw, "abrir")
        is QrContent.Wifi -> Triple("wi‑fi", content.ssid, "conectar")
        is QrContent.Pix -> Triple("pix copia e cola", content.raw, "copiar")
        is QrContent.Ponte -> Triple("ponte 橋", content.name, "parear")
        is QrContent.Text -> Triple("texto", content.raw, "copiar")
    }
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(Y.space.md)
            .clip(RoundedCornerShape(18.dp))
            .background(Y.paperTop)
            .padding(Y.space.lg),
    ) {
        Text(kind.uppercase(), style = Y.type.caption, color = Y.inkFaint, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(4.dp))
        Text(shown, style = Y.type.body, color = Y.inkStrong, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Y.space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Y.space.sm)) {
            CardButton("ler outro", filled = false, onClick = onAgain, modifier = Modifier.weight(1f))
            CardButton(actionLabel, filled = true, modifier = Modifier.weight(1f), onClick = {
                if (content is QrContent.Ponte) onPair(content) else if (act(context, content)) onDone()
            })
        }
    }
}

@Composable
private fun CardButton(label: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) Y.seal else Color.Transparent)
            .border(1.dp, if (filled) Y.seal else Y.inkFaint, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Y.type.label, color = if (filled) Y.paperTop else Y.ink, fontWeight = FontWeight.Bold)
    }
}

/** Perform the card's action. Returns true when the reader is finished and should close. */
private fun act(context: Context, content: QrContent): Boolean = when (content) {
    is QrContent.Link -> {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(content.raw))) }.isSuccess
    }
    is QrContent.Wifi -> joinWifi(context, content)
    is QrContent.Pix -> copy(context, "Pix", content.raw, "Pix copiado")
    is QrContent.Text -> copy(context, "QR", content.raw, "copiado")
    is QrContent.Ponte -> false   // paired by the activity, which owns the bridge
}

/**
 * Hand the network to the system's "add networks" sheet (Android 11+), which asks the person to
 * confirm and saves it like any network typed in Settings. Older versions copy the password.
 */
private fun joinWifi(context: Context, wifi: QrContent.Wifi): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val builder = WifiNetworkSuggestion.Builder().setSsid(wifi.ssid).setIsHiddenSsid(wifi.hidden)
        val pass = wifi.password
        if (pass != null) {
            when (wifi.security?.uppercase()) {
                "SAE", "WPA3" -> builder.setWpa3Passphrase(pass)
                else -> builder.setWpa2Passphrase(pass)
            }
        }
        val intent = Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
            .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(builder.build()))
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
    }
    return copy(context, "Wi‑Fi", wifi.password ?: wifi.ssid, "senha copiada")
}

private fun copy(context: Context, label: String, text: String, toast: String): Boolean {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
    return true
}
