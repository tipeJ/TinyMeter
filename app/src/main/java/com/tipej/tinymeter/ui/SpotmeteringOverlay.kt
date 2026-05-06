package com.tipej.tinymeter.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.tipej.tinymeter.R

/**
 Transparent overlay drawn over the camera preview.
 In AVERAGE mode: vignette + crosshair grid lines.
 In SPOT mode: draggable spot-metering circle.
 */
class SpotMeteringOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var isSpotMode: Boolean = false
        set(value) { field = value; invalidate() }

    var spotX: Float = 0.5f  // normalized 0..1
        set(value) { field = value; invalidate() }

    var spotY: Float = 0.5f  // normalized 0..1
        set(value) { field = value; invalidate() }

    var onSpotMoved: ((Float, Float) -> Unit)? = null

    private val spotRadiusDp = 48f
    private val spotRadiusPx get() = spotRadiusDp * resources.displayMetrics.density

    // Outer ring
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
    }
    // Corner brackets
    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.SQUARE
    }
    // Center dot
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    // Grid/crosshair
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (isSpotMode) drawSpotMeter(canvas)
        else drawAverageMeter(canvas)
    }

    private fun drawSpotMeter(canvas: Canvas) {
        val cx = spotX * width
        val cy = spotY * height
        val r = spotRadiusPx

        // Outer circle
        canvas.drawCircle(cx, cy, r, ringPaint)

        // Bracket corners (top-left, top-right, bottom-left, bottom-right)
        val bl = 18f * resources.displayMetrics.density
        val gap = r + 12f * resources.displayMetrics.density
        // Top-left
        canvas.drawLine(cx - gap - bl, cy - gap, cx - gap, cy - gap, bracketPaint)
        canvas.drawLine(cx - gap, cy - gap - bl, cx - gap, cy - gap, bracketPaint)
        // Top-right
        canvas.drawLine(cx + gap, cy - gap - bl, cx + gap, cy - gap, bracketPaint)
        canvas.drawLine(cx + gap, cy - gap, cx + gap + bl, cy - gap, bracketPaint)
        // Bottom-left
        canvas.drawLine(cx - gap - bl, cy + gap, cx - gap, cy + gap, bracketPaint)
        canvas.drawLine(cx - gap, cy + gap, cx - gap, cy + gap + bl, bracketPaint)
        // Bottom-right
        canvas.drawLine(cx + gap, cy + gap, cx + gap + bl, cy + gap, bracketPaint)
        canvas.drawLine(cx + gap, cy + gap, cx + gap, cy + gap + bl, bracketPaint)

        // Center cross
        val cs = 10f * resources.displayMetrics.density
        canvas.drawLine(cx - cs, cy, cx + cs, cy, bracketPaint)
        canvas.drawLine(cx, cy - cs, cx, cy + cs, bracketPaint)

        // Center dot
        canvas.drawCircle(cx, cy, 3f * resources.displayMetrics.density, dotPaint)
    }

    private fun drawAverageMeter(canvas: Canvas) {
        // Rule-of-thirds grid
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawLine(w / 3, 0f, w / 3, h, gridPaint)
        canvas.drawLine(2 * w / 3, 0f, 2 * w / 3, h, gridPaint)
        canvas.drawLine(0f, h / 3, w, h / 3, gridPaint)
        canvas.drawLine(0f, 2 * h / 3, w, 2 * h / 3, gridPaint)
        // TODO: Add golden rule?

        // Center crosshair
        val cx = w / 2f
        val cy = h / 2f
        val cs = 24f * resources.displayMetrics.density
        canvas.drawLine(cx - cs, cy, cx + cs, cy, bracketPaint)
        canvas.drawLine(cx, cy - cs, cx, cy + cs, bracketPaint)
        canvas.drawCircle(cx, cy, cs * 0.6f, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isSpotMode) return false // Move the spotter if on spot mode
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val nx = (event.x / width).coerceIn(0f, 1f)
                val ny = (event.y / height).coerceIn(0f, 1f)
                spotX = nx
                spotY = ny
                onSpotMoved?.invoke(nx, ny)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
