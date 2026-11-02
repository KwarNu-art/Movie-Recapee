package com.movierecapee.data.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.movierecapee.domain.CaptionAlign
import com.movierecapee.domain.CaptionStyle
import kotlin.math.ceil
import kotlin.math.max

/**
 * Draws caption lines onto a transparent bitmap the size of the video frame.
 * Android's own text engine is used, so Myanmar script is shaped correctly.
 * The preview in the editor and the exported video use this same code, so they look identical.
 */
object CaptionRenderer {
    fun render(frameW: Int, frameH: Int, lines: List<String>, style: CaptionStyle): Bitmap {
        val bmp = Bitmap.createBitmap(frameW.coerceAtLeast(1), frameH.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        if (lines.isEmpty()) return bmp
        val canvas = Canvas(bmp)
        val base = frameH * style.fontSize
        val maxTextW = (frameW * 0.9f).toInt().coerceAtLeast(40)
        val pad = base * style.boxPadding
        val gap = base * 0.12f
        val face = if (style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val cs = style

        class Item(val fill: StaticLayout, val stroke: StaticLayout?, val width: Float, val height: Float)

        val items = lines.mapIndexed { i, line ->
            val size = if (i == 0) base else base * 0.82f
            fun paint(stroke: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.typeface = face
                if (stroke) {
                    this.style = Paint.Style.STROKE
                    strokeWidth = size * cs.strokeWidth * 2f
                    strokeJoin = Paint.Join.ROUND
                    color = cs.strokeColor
                } else {
                    this.style = Paint.Style.FILL
                    color = cs.textColor
                }
            }
            val alignment = when (style.align) {
                CaptionAlign.LEFT -> Layout.Alignment.ALIGN_NORMAL
                CaptionAlign.CENTER -> Layout.Alignment.ALIGN_CENTER
                CaptionAlign.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            }
            fun layout(p: TextPaint, w: Int) = StaticLayout.Builder.obtain(line, 0, line.length, p, w)
                .setAlignment(alignment).setIncludePad(false).build()
            val probe = layout(paint(false), maxTextW)
            var widest = 0f
            for (l in 0 until probe.lineCount) widest = max(widest, probe.getLineWidth(l))
            val w = ceil(widest).toInt().coerceAtLeast(1)
            val fill = layout(paint(false), w)
            val stroke = if (style.strokeWidth > 0f) layout(paint(true), w) else null
            Item(fill, stroke, w.toFloat(), fill.height.toFloat())
        }

        val boxAlpha = (style.boxOpacity.coerceIn(0f, 1f) * 255).toInt()
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(boxAlpha, Color.red(cs.boxColor), Color.green(cs.boxColor), Color.blue(cs.boxColor))
        }
        val total = items.sumOf { (it.height + pad * 2).toDouble() }.toFloat() + gap * (items.size - 1)
        var top = frameH * (1f - style.offsetY) - total
        val margin = frameW * 0.05f
        for (it in items) {
            val boxW = it.width + pad * 2
            val boxH = it.height + pad * 2
            val left = when (style.align) {
                CaptionAlign.LEFT -> margin
                CaptionAlign.CENTER -> (frameW - boxW) / 2f
                CaptionAlign.RIGHT -> frameW - margin - boxW
            }
            if (boxAlpha > 0) canvas.drawRoundRect(RectF(left, top, left + boxW, top + boxH), pad, pad, boxPaint)
            canvas.save()
            canvas.translate(left + pad, top + pad)
            it.stroke?.draw(canvas)
            it.fill.draw(canvas)
            canvas.restore()
            top += boxH + gap
        }
        return bmp
    }
}
