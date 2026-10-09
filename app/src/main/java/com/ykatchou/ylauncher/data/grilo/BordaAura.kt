package com.ykatchou.ylauncher.data.grilo

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.ykatchou.ylauncher.util.YLogger

/**
 * A borda-aura: o Grilo no celular não é uma bolinha, é uma luz que acende em
 * volta da tela (overlay sempre no topo), com as MESMAS cores e o mesmo
 * comportamento do desktop — verde ouvindo/pensando, verde pulsando falando,
 * vermelho quando não entende. Precisa da permissão "sobrepor outros apps".
 *
 * Único ponto de verdade: [Grilo.mostrar] chama [atualizar] junto da tela de
 * trás, então aura, tela traseira e (depois) núcleo contam a mesma coisa.
 */
object BordaAura {
    private const val TAG = "BordaAura"
    private var view: AuraView? = null
    private var wm: WindowManager? = null

    fun atualizar(context: Context, estado: Grilo.Estado) {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(app)) {
            return  // sem permissão de overlay: silencioso (o usuário concede uma vez)
        }
        val cor = when (estado) {
            Grilo.Estado.QUIETO -> { esconder(); return }
            Grilo.Estado.OUVINDO, Grilo.Estado.PENSANDO -> Color.rgb(0x5F, 0x8A, 0x4F) // jade
            Grilo.Estado.FALANDO -> Color.rgb(0x5F, 0x8A, 0x4F)                         // verde (pulsa)
            Grilo.Estado.INVALIDO -> Color.rgb(0xB2, 0x3B, 0x2E)                        // selo
        }
        val pulsa = estado == Grilo.Estado.FALANDO
        try {
            if (view == null) {
                wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val v = AuraView(app)
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                    PixelFormat.TRANSLUCENT,
                )
                lp.gravity = Gravity.TOP or Gravity.START
                wm?.addView(v, lp); view = v
            }
            view?.definir(cor, pulsa)
        } catch (t: Throwable) {
            YLogger.e(TAG, "falha na aura", t as? Exception ?: Exception(t))
        }
    }

    fun esconder() {
        try { view?.let { wm?.removeView(it) } } catch (_: Throwable) {}
        view?.parar(); view = null
    }

    /** Desenha a aura: traço com brilho (blur) em volta da tela; pulsa na fala. */
    private class AuraView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private var cor = Color.TRANSPARENT
        private var fator = 1f
        private var anim: ValueAnimator? = null

        fun definir(cor: Int, pulsa: Boolean) {
            this.cor = cor
            anim?.cancel(); anim = null
            if (pulsa) {
                anim = ValueAnimator.ofFloat(0.6f, 1f).apply {
                    duration = 650; repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { fator = it.animatedValue as Float; invalidate() }
                    start()
                }
            } else { fator = 1f }
            invalidate()
        }
        fun parar() { anim?.cancel(); anim = null }

        override fun onDraw(canvas: Canvas) {
            if (cor == Color.TRANSPARENT) return
            val larg = 10f * resources.displayMetrics.density * fator
            paint.strokeWidth = larg
            paint.color = cor
            paint.maskFilter = BlurMaskFilter(larg * 1.4f, BlurMaskFilter.Blur.NORMAL)
            val o = larg / 2f
            val raio = 44f * resources.displayMetrics.density
            canvas.drawRoundRect(RectF(o, o, width - o, height - o), raio, raio, paint)
        }
    }
}
