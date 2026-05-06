package com.tipej.tinymeter.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
TODO: add documentation
 */

//TODO: THIS DOESNT SEEM TO WORK CORRECTLY SINCE THE SCALE APPEARS FLIPPED AND/OR OTHERWISE INACCURATE...
class ExposureScaleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    //Deviation from correct EV: negative = under, positive = over
    var evDeviation: Float = 0f
        set(value) { field = value.coerceIn(-3f, 3f); invalidate() }

    private val dp = resources.displayMetrics.density

    private val scalePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCA28")
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textSize = 9f * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val centerLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val scaleW = w * 0.85f
        val scaleLeft = (w - scaleW) / 2f
        val scaleRight = scaleLeft + scaleW
        val midY = h * 0.45f

        // Baseline
        canvas.drawLine(scaleLeft, midY, scaleRight, midY, scalePaint)

        // Ticks: -3, -2, -1, 0, +1, +2, +3
        for (i in -3..3) {
            val x = cx + (i / 3f) * (scaleW / 2f)
            val tickH = if (i == 0) h * 0.40f else if (i % 1 == 0) h * 0.28f else h * 0.18f
            canvas.drawLine(x, midY - tickH / 2, x, midY + tickH / 2, tickPaint)
            if (i != 0) {
                val label = if (i > 0) "+$i" else "$i"
                canvas.drawText(label, x, midY + tickH / 2 + 14f * dp, textPaint)
            }
        }

        // Center marker
        canvas.drawLine(cx, midY - h * 0.40f, cx, midY + h * 0.40f, centerLinePaint)

        // Needle
        val needleX = cx + (evDeviation / 3f) * (scaleW / 2f)
        val nw = 6f * dp
        val nh = 10f * dp
        val path = Path().apply {
            moveTo(needleX, midY + nh)
            lineTo(needleX - nw / 2, midY)
            lineTo(needleX + nw / 2, midY)
            close()
        }
        // Color the needle: yellow at 0, red at extremes
        needlePaint.color = when {
            Math.abs(evDeviation) < 0.5f -> Color.parseColor("#69F0AE")  // green – correct
            Math.abs(evDeviation) < 1.5f -> Color.parseColor("#FFCA28")  // amber
            else -> Color.parseColor("#FF5252")                           // red
        }
        canvas.drawPath(path, needlePaint)

        // EV deviation label
        val devText = when {
            Math.abs(evDeviation) < 0.1f -> "±0"
            evDeviation > 0 -> String.format("+%.1f EV", evDeviation)
            else -> String.format("%.1f EV", evDeviation)
        }
        textPaint.color = needlePaint.color
        textPaint.textSize = 10f * dp
        canvas.drawText(devText, cx, h * 0.15f, textPaint)
    }
}
