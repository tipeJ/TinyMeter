package com.tipej.tinymeter.utils

import com.tipej.tinymeter.model.ApertureValues
import com.tipej.tinymeter.model.ExposureReading
import com.tipej.tinymeter.model.ShutterSpeedValues
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

/**
Photographic exposure calculations using the APEX / EV system.
 */
object ExposureCalculator {

    // Reflected-light calibration constant (ISO 12232, K = 12.5 is the Canon/Nikon standard)
    private const val K = 12.5

    // EV | lux
    fun luxToEv100(lux: Double): Double {
        if (lux <= 0.0) return 0.0
        // E = 2^EV100 / 2.55  →  EV100 = log2(E * 2.55)
        return log2(lux * 2.55)
    }

    // lux from ev100
    fun ev100ToLux(ev100: Double): Double {
        return 2.0.pow(ev100) / 2.55
    }

    // EV from sensor metadata
    fun ev100FromSensorMetadata(
        exposureTimeNs: Long,   // SENSOR_EXPOSURE_TIME in nanoseconds
        iso: Int,               // SENSOR_SENSITIVITY
        fNumber: Float          // LENS_APERTURE
    ): Double {
        val t = exposureTimeNs / 1_000_000_000.0   // convert ns → seconds
        if (t <= 0.0 || iso <= 0 || fNumber <= 0f) return 0.0
        // Camera's own AE gives us the scene EV
        val evAtIso = log2((fNumber * fNumber).toDouble() / t)
        // Normalize to ISO 100
        val ev100 = evAtIso - log2(iso / 100.0)
        return ev100
    }

    // Lux from the sensor metadata
    fun luxFromSensorMetadata(exposureTimeNs: Long, iso: Int, fNumber: Float): Double {
        val ev100 = ev100FromSensorMetadata(exposureTimeNs, iso, fNumber)
        return ev100ToLux(ev100)
    }

    // If sensor metadata is unavailable we can use this to infer the lux
    fun luxFromBrightnessOnly(brightness: Double): Double {
        // EV100 ranges roughly from -4 (night) to 17 (bright sun)
        val ev100 = brightness * 21.0 - 4.0
        return ev100ToLux(ev100)
    }

    // Priority-mode calculations

    // A-P, calculate shutter speed
    fun shutterFromEvApertureIso(ev100: Double, aperture: Double, iso: Int): Double {
        val evAtIso = ev100 + log2(iso / 100.0)
        val t = (aperture * aperture) / 2.0.pow(evAtIso)
        return t.coerceIn(1.0 / 32000.0, 30.0)
    }

    // S-P, calculate aperture
    fun apertureFromEvShutterIso(ev100: Double, shutterSpeed: Double, iso: Int): Double {
        val evAtIso = ev100 + log2(iso / 100.0)
        val nSquared = shutterSpeed * 2.0.pow(evAtIso)
        return sqrt(nSquared).coerceIn(1.0, 64.0)
    }

    // builders
    fun buildAperturePriorityReading(lux: Double, iso: Int, aperture: Double): ExposureReading {
        val ev100 = luxToEv100(lux)
        val shutter = shutterFromEvApertureIso(ev100, aperture, iso)
        return ExposureReading(
            iso = iso,
            aperture = aperture,
            shutterSpeed = shutter,
            ev = ev100,
            lux = lux
        )
    }

    fun buildShutterPriorityReading(lux: Double, iso: Int, shutterSpeed: Double): ExposureReading {
        val ev100 = luxToEv100(lux)
        val aperture = apertureFromEvShutterIso(ev100, shutterSpeed, iso)
        return ExposureReading(
            iso = iso,
            aperture = aperture,
            shutterSpeed = shutterSpeed,
            ev = ev100,
            lux = lux
        )
    }

    fun buildManualReading(lux: Double, iso: Int, aperture: Double, shutterSpeed: Double): ExposureReading {
        val ev100 = luxToEv100(lux)
        val settingsEv = log2((aperture * aperture) / shutterSpeed) - log2(iso / 100.0)
        return ExposureReading(
            iso = iso,
            aperture = aperture,
            shutterSpeed = shutterSpeed,
            ev = ev100,
            lux = lux,
            isUnderExposed = settingsEv < ev100 - 1.0,
            isOverExposed  = settingsEv > ev100 + 1.0
        )
    }

    // Nearest-value snapping helpers TODO: remove?
    fun snapToNearestAperture(aperture: Double): Double =
        ApertureValues.values.minByOrNull { kotlin.math.abs(it - aperture) } ?: aperture

    fun snapToNearestShutter(shutterSpeed: Double): Double =
        ShutterSpeedValues.values.minByOrNull { kotlin.math.abs(it - shutterSpeed) } ?: shutterSpeed
}
