package com.tipej.tinymeter.model

// Two metering modes, average of the scene and spot
enum class MeteringMode {
    AVERAGE, SPOT
}

// Priority modes
enum class PriorityMode {
    MANUAL,           // User controls all three values
    APERTURE_PRIORITY, // User sets ISO + aperture, shutter speed is calculated
    SHUTTER_PRIORITY   // User sets ISO + shutter speed, aperture is calculated
}

data class ExposureReading(
    val iso: Int,
    val aperture: Double,   // f-stop number e.g. 2.8
    val shutterSpeed: Double, // in seconds e.g. 0.001 for 1/1000
    val ev: Double,           // Exposure Value
    val lux: Double,          // Estimated lux
    val isUnderExposed: Boolean = false,
    val isOverExposed: Boolean = false
) {
    val shutterSpeedDisplay: String
        get() = when {
            shutterSpeed >= 1.0 -> "${shutterSpeed.toInt()}s"
            shutterSpeed >= 0.5 -> "1/${(1.0 / shutterSpeed).toInt()}"
            else -> "1/${(1.0 / shutterSpeed).toInt()}"
        }

    val apertureDisplay: String
        get() = "f/${formatAperture(aperture)}"

    val isoDisplay: String
        get() = "ISO $iso"

    val evDisplay: String
        get() = String.format("EV %.1f", ev)

    val luxDisplay: String
        get() = when {
            lux >= 1000 -> String.format("%.0f klx", lux / 1000)
            else -> String.format("%.0f lx", lux)
        }

    val lightDescription: String
        get() = when {
            ev < 0  -> "Very dark / night scene"
            ev < 3  -> "Night / candlelight"
            ev < 5  -> "Indoor – dim"
            ev < 8  -> "Indoor – typical"
            ev < 11 -> "Overcast outdoors"
            ev < 13 -> "Hazy sunlight"
            ev < 15 -> "Full sunlight"
            else    -> "Extremely bright"
        }

    private fun formatAperture(f: Double): String {
        return if (f == f.toLong().toDouble()) f.toLong().toString()
        else String.format("%.1f", f)
    }
}


// ISO values TODO: ADD??
object IsoValues {
    val values = listOf(25, 50, 100, 200, 400, 800, 1600, 3200, 6400, 12800, 25600)
}


// (full stops) f-stop aperture values. TODO: Add intermediaries?

object ApertureValues {
    val values = listOf(1.0, 1.4, 2.0, 2.8, 4.0, 5.6, 8.0, 11.0, 16.0, 22.0)
}

// Usual shutter speeds
object ShutterSpeedValues {
    val values = listOf(
        30.0, 15.0, 8.0, 4.0, 2.0, 1.0,
        1.0/2, 1.0/4, 1.0/8, 1.0/15, 1.0/30,
        1.0/60, 1.0/125, 1.0/250, 1.0/500,
        1.0/1000, 1.0/2000, 1.0/4000
    )

    fun display(seconds: Double): String = when {
        seconds >= 1.0 -> "${seconds.toInt()}s"
        else -> "1/${(1.0 / seconds).toInt()}"
    }
}
