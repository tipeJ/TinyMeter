package com.tipej.tinymeter.utils

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.tipej.tinymeter.model.MeteringMode
import com.tipej.tinymeter.viewmodel.LightMeterViewModel
import java.nio.ByteBuffer

/**
CameraX ImageAnalysis.

Brightness is read from YUV Y-plane pixels.
 */
class LuminosityAnalyzer(
    private val viewModel: LightMeterViewModel
) : ImageAnalysis.Analyzer {

    // Latest sensor metadata, updated from the Camera2Interop capture callback
    @Volatile var sensorExposureTimeNs: Long = 0L
    @Volatile var sensorIso: Int = 0
    @Volatile var sensorAperture: Float = 0f

    override fun analyze(image: ImageProxy) {
        try {
            val yPlane = image.planes[0]
            val buffer: ByteBuffer = yPlane.buffer
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride

            val meteringMode = viewModel.meteringMode.value ?: MeteringMode.AVERAGE
            val spotRegion = viewModel.spotRegion.value

            val brightness = when (meteringMode) {
                MeteringMode.AVERAGE -> computeAverageBrightness(
                    buffer, width, height, rowStride, pixelStride
                )
                MeteringMode.SPOT -> {
                    val cx = ((spotRegion?.x ?: 0.5f) * width).toInt()
                    val cy = ((spotRegion?.y ?: 0.5f) * height).toInt()
                    val radius = ((spotRegion?.radius ?: 0.15f) * minOf(width, height)).toInt()
                    computeSpotBrightness(buffer, width, height, rowStride, pixelStride, cx, cy, radius)
                }
            }

            val expNs  = sensorExposureTimeNs
            val iso    = sensorIso
            val fNumber = sensorAperture

            if (expNs > 0L && iso > 0 && fNumber > 0f) {
                viewModel.onSensorData(brightness, expNs, iso, fNumber)
            } else {
                // If not available derive lux from just the pixel brightness
                viewModel.onBrightnessOnly(brightness)
            }
        } finally {
            image.close()
        }
    }

    // pixel sampling
    private fun computeAverageBrightness(
        buffer: ByteBuffer, width: Int, height: Int,
        rowStride: Int, pixelStride: Int
    ): Double {
        buffer.rewind()
        var sum = 0L
        var count = 0
        val stepY = 4
        val stepX = 4
        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val index = y * rowStride + x * pixelStride
                if (index < buffer.limit()) {
                    sum += buffer.get(index).toInt() and 0xFF
                    count++
                }
            }
        }
        return if (count > 0) sum.toDouble() / count / 255.0 else 0.5
    }

    // spot brightness calculation from the given subset of pixels
    private fun computeSpotBrightness(
        buffer: ByteBuffer, width: Int, height: Int,
        rowStride: Int, pixelStride: Int,
        cx: Int, cy: Int, radius: Int
    ): Double {
        buffer.rewind()
        var sum = 0L
        var count = 0
        val x0 = (cx - radius).coerceAtLeast(0)
        val x1 = (cx + radius).coerceAtMost(width - 1)
        val y0 = (cy - radius).coerceAtLeast(0)
        val y1 = (cy + radius).coerceAtMost(height - 1)
        for (y in y0..y1 step 2) {
            for (x in x0..x1 step 2) {
                val dx = x - cx; val dy = y - cy
                if (dx * dx + dy * dy <= radius * radius) {
                    val index = y * rowStride + x * pixelStride
                    if (index < buffer.limit()) {
                        sum += buffer.get(index).toInt() and 0xFF
                        count++
                    }
                }
            }
        }
        return if (count > 0) sum.toDouble() / count / 255.0 else 0.5
    }
}
