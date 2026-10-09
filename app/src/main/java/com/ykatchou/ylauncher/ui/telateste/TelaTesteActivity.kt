package com.ykatchou.ylauncher.ui.telateste

import android.app.Presentation
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.ykatchou.ylauncher.data.running.ShizukuShell
import com.ykatchou.ylauncher.util.YLogger

/**
 * POC: can a plain app draw on the BV9300 Pro's round rear screen?
 *
 * The rear screen shows up to the system as a secondary display named "Overlay&preloaded #1" — an
 * overlay display, not a private virtual one, which (if public) any app may put a [Presentation]
 * on. This activity lists every display and tries to raise a Tinta test card on the one that is
 * not the main screen. It reports, on the main screen, exactly what it saw and whether it worked,
 * so we learn the real behaviour instead of guessing.
 */
class TelaTesteActivity : ComponentActivity() {

    private var presentation: Presentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = TextView(this).apply {
            setPadding(40, 80, 40, 40); textSize = 13f; setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.MONOSPACE
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK); addView(report)
        })

        val dm = getSystemService(DisplayManager::class.java)
        val sb = StringBuilder()

        // 1. Shizuku first: it can flip the overlay_display_devices setting / poke the rear screen
        //    on, and later borrow the shell to present if the app itself is refused.
        sb.appendLine("Shizuku pronto: ${ShizukuShell.isReady()}")

        // 2. Every display this app is allowed to see.
        val displays = dm.displays
        sb.appendLine("\nTODOS (${displays.size}):")
        displays.forEach { d -> sb.appendLine("  #${d.displayId} \"${d.name}\" ${flags(d)} st=${d.state} ${w(d)}x${h(d)}") }

        // SystemUI finds the rear screen through the PRESENTATION category, not the plain list.
        val pres = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        sb.appendLine("\nPRESENTATION (${pres.size}):")
        pres.forEach { d -> sb.appendLine("  #${d.displayId} \"${d.name}\" ${flags(d)} st=${d.state} ${w(d)}x${h(d)}") }

        // 3. The rear screen = any display that is not the built-in main one.
        val todos = (pres.toList() + displays.toList()).distinctBy { it.displayId }
        val traseira = todos.firstOrNull { it.name.contains("Overlay", true) || it.name.contains("preloaded", true) }
            ?: todos.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && !it.name.contains("scrcpy", true) }

        if (traseira == null) {
            sb.appendLine("\nNENHUMA tela secundária visível agora.")
            sb.appendLine("Acenda a tela traseira (bloqueie a frente) e abra de novo.")
        } else {
            sb.appendLine("\nTentando desenhar na #${traseira.displayId} \"${traseira.name}\"…")
            try {
                presentation = Presentation(this, traseira).apply {
                    setContentView(cartao(traseira))
                    show()
                }
                sb.appendLine("✓ Presentation.show() NÃO deu erro. Olhe a tela traseira.")
            } catch (t: Throwable) {
                sb.appendLine("✗ falhou: ${t.javaClass.simpleName}: ${t.message}")
                YLogger.e(TAG, "presentation failed", t as? Exception ?: Exception(t))
            }
        }
        report.text = sb.toString()
    }

    /** The Tinta bubble, big enough to be obvious on the round screen. */
    private fun cartao(d: Display): LinearLayout = LinearLayout(createDisplayContext(d)).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.rgb(0xB2, 0x3B, 0x2E)) // seal red: unmistakable if it lights up
        addView(TextView(context).apply {
            text = "蛩"; setTextColor(Color.rgb(0xF6, 0xF0, 0xE3)); textSize = 120f
            gravity = Gravity.CENTER
        })
        addView(TextView(context).apply {
            text = "GRILO"; setTextColor(Color.rgb(0xF6, 0xF0, 0xE3)); textSize = 28f
            gravity = Gravity.CENTER
        })
    }

    private fun flags(d: Display): String = buildList {
        val f = d.flags
        if (f and Display.FLAG_PRIVATE != 0) add("PRIVATE")
        if (f and Display.FLAG_PRESENTATION != 0) add("PRESENTATION")
        if (f and Display.FLAG_SECURE != 0) add("SECURE")
    }.joinToString("|").ifEmpty { "-" }

    private fun w(d: Display) = android.graphics.Point().also { @Suppress("DEPRECATION") d.getRealSize(it) }.x
    private fun h(d: Display) = android.graphics.Point().also { @Suppress("DEPRECATION") d.getRealSize(it) }.y

    override fun onDestroy() {
        presentation?.dismiss()
        super.onDestroy()
    }

    private companion object { const val TAG = "TelaTeste" }
}
