package com.nekodrive.app.ui.galeria

import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.core.graphics.drawable.toBitmap
import com.nekodrive.app.data.ProvedorMidia
import com.nekodrive.app.data.drive.DriveFile

/** Quadrado arredondado com a miniatura (ou só a cor da mídia) que acompanha o dedo no arrastar. */
private class SombraMidia(view: View, private val miniatura: Bitmap?, private val cor: Int) : View.DragShadowBuilder(view) {
    private val lado = (view.resources.displayMetrics.density * 110).toInt()

    override fun onProvideShadowMetrics(size: Point, toque: Point) {
        size.set(lado, lado)
        toque.set(lado / 2, lado / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val destino = RectF(0f, 0f, lado.toFloat(), lado.toFloat())
        val raio = lado * 0.14f
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(destino, raio, raio, Path.Direction.CW) })
        if (miniatura != null) {
            // recorte central quadrado
            val m = minOf(miniatura.width, miniatura.height)
            val origem = Rect((miniatura.width - m) / 2, (miniatura.height - m) / 2, (miniatura.width + m) / 2, (miniatura.height + m) / 2)
            canvas.drawBitmap(miniatura, origem, destino, null)
        } else {
            canvas.drawRect(destino, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cor })
        }
        canvas.restore()
    }
}

/** Bitmap pequeno (lado maior 256 px) de uma imagem já carregada, mantendo a proporção. */
fun miniaturaPequena(d: Drawable?): Bitmap? {
    d ?: return null
    val w = d.intrinsicWidth
    val h = d.intrinsicHeight
    if (w <= 0 || h <= 0) return null
    val r = w.toFloat() / h
    val bw = if (r >= 1f) 256 else (256 * r).toInt().coerceAtLeast(1)
    val bh = if (r >= 1f) (256 / r).toInt().coerceAtLeast(1) else 256
    return try { d.toBitmap(bw, bh) } catch (e: Exception) { null }
}

/**
 * Começa o arrastar e soltar do sistema com a foto ou o vídeo. Funciona com o dedo ainda na tela e leva o
 * arquivo para apps que estejam em tela dividida ou janela flutuante.
 */
fun iniciarArrasto(view: View, f: DriveFile, miniatura: Bitmap?, cor: Int): Boolean {
    val ctx = view.context
    val clip = ClipData.newUri(ctx.contentResolver, f.name, ProvedorMidia.uriDe(ctx, f))
    val ok = view.startDragAndDrop(
        clip,
        SombraMidia(view, miniatura, cor),
        null,
        View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ,
    )
    if (ok) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    return ok
}
