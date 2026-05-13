package com.tipej.tinymeter.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
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

    /**
     * Current exposure compensation in EV stops. Only shown and interactive
     * when [ecEnabled] is true (i.e. Aperture or Shutter priority mode).
     * Range: -3 to +3 EV in 1/3-stop increments.
     */
    var evCompensation: Float = 0f
        set(value) { field = value.coerceIn(-3f, 3f); invalidate() }

    /**
     * When true the scale accepts touch input to set [evCompensation] and
     * draws the EC marker. Set to true in Av/Tv modes, false in Manual.
     */
    var ecEnabled: Boolean = false
        set(value) { field = value; isClickable = value; isFocusable = value; invalidate() }

    /** Called when the user taps/drags to set a new compensation value. */
    var onCompensationChanged: ((Float) -> Unit)? = null

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

    // Paint for the EC diamond marker
    private val ecMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
        style = Paint.Style.FILL
    }
    private val ecMarkerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val ecTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40C4FF")
        textSize = 9f * dp
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }

    // Cached scale geometry so onTouchEvent can map touch X → EV without re-computing
    private var cachedCx: Float = 0f
    private var cachedScaleHalfW: Float = 0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val scaleW = w * 0.85f
        val scaleLeft = (w - scaleW) / 2f
        val scaleRight = scaleLeft + scaleW
        val midY = h * 0.45f

        cachedCx = cx
        cachedScaleHalfW = scaleW / 2f

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

        // EC marker — only drawn in Av/Tv mode
        if (ecEnabled) {
            drawEcMarker(canvas, cx, scaleW, midY, h)
        }
    }

    /**
     * Draws a small diamond above the scale baseline at the EC position,
     * plus a "±X.X" label above the deviation label.
     */
    private fun drawEcMarker(canvas: Canvas, cx: Float, scaleW: Float, midY: Float, h: Float) {
        val ecX = cx + (evCompensation / 3f) * (scaleW / 2f)
        val ds = 5f * dp   // diamond half-size

        // Draw above the baseline
        val diamondTop = midY - h * 0.40f - ds * 0.5f
        val diamondPath = Path().apply {
            moveTo(ecX, diamondTop - ds)        // top
            lineTo(ecX + ds, diamondTop)         // right
            lineTo(ecX, diamondTop + ds)         // bottom
            lineTo(ecX - ds, diamondTop)         // left
            close()
        }
        canvas.drawPath(diamondPath, ecMarkerPaint)
        canvas.drawPath(diamondPath, ecMarkerStrokePaint)

        // EC value label — above the EV deviation label
        val ecText = when {
            Math.abs(evCompensation) < 0.05f -> "EC ±0"
            evCompensation > 0 -> String.format("EC +%.1f", evCompensation)
            else -> String.format("EC %.1f", evCompensation)
        }
        ecTextPaint.textSize = 9f * dp
        canvas.drawText(ecText, cx, h * 0.05f, ecTextPaint)

        // Hint text so the user knows the scale is interactive
        if (Math.abs(evCompensation) < 0.05f) {
            val hintPaint = Paint(ecTextPaint).apply {
                color = Color.argb(100, 64, 196, 255)
                textSize = 8f * dp
            }
            canvas.drawText("tap to set EC", cx, h * 0.98f, hintPaint)
        }
    }

    // -------------------------------------------------------------------------
    // Touch handling
    // -------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!ecEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val raw = (event.x - cachedCx) / cachedScaleHalfW * 3f
                // Snap to nearest 1/3-stop increment
                val snapped = (Math.round(raw * 3f) / 3f).toFloat().coerceIn(-3f, 3f)
                if (snapped != evCompensation) {
                    evCompensation = snapped
                    onCompensationChanged?.invoke(snapped)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Required for accessibility when overriding onTouchEvent
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}