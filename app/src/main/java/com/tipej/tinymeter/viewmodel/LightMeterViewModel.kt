package com.tipej.tinymeter.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.tipej.tinymeter.model.*
import com.tipej.tinymeter.utils.ExposureCalculator

class LightMeterViewModel : ViewModel() {

    private val _meteringMode = MutableLiveData(MeteringMode.AVERAGE)
    val meteringMode: LiveData<MeteringMode> = _meteringMode

    private val _priorityMode = MutableLiveData(PriorityMode.APERTURE_PRIORITY)
    val priorityMode: LiveData<PriorityMode> = _priorityMode

    private val _selectedIso = MutableLiveData(400)
    val selectedIso: LiveData<Int> = _selectedIso

    private val _selectedAperture = MutableLiveData(2.8)
    val selectedAperture: LiveData<Double> = _selectedAperture

    private val _selectedShutter = MutableLiveData(1.0 / 60)
    val selectedShutter: LiveData<Double> = _selectedShutter

    private val _currentLux = MutableLiveData(0.0)
    val currentLux: LiveData<Double> = _currentLux

    private val _exposureReading = MutableLiveData<ExposureReading>()
    val exposureReading: LiveData<ExposureReading> = _exposureReading

    private val _cameraActive = MutableLiveData(false)
    val cameraActive: LiveData<Boolean> = _cameraActive

    data class SpotRegion(val x: Float, val y: Float, val radius: Float = 0.15f)
    private val _spotRegion = MutableLiveData(SpotRegion(0.5f, 0.5f))
    val spotRegion: LiveData<SpotRegion> = _spotRegion

    /**
     * Exposure compensation in EV stops, applied on top of the metered value
     * in Aperture Priority and Shutter Priority modes. Range: -3 to +3.
     * Has no effect in Manual mode (the user controls all three values directly).
     */
    private val _evCompensation = MutableLiveData(0f)
    val evCompensation: LiveData<Float> = _evCompensation

    // Smoothing buffer
    private val luxBuffer = ArrayDeque<Double>()
    private val BUFFER_SIZE = 8

    // -------------------------------------------------------------------------
    // Camera data input
    // -------------------------------------------------------------------------

    /**
    Called when real Camera2 sensor metadata is available.
    This is the primary metering method
     */
    fun onSensorData(
        brightness: Double,
        exposureTimeNs: Long,
        sensitivity: Int,
        aperture: Float
    ) {
        val lux = ExposureCalculator.luxFromSensorMetadata(exposureTimeNs, sensitivity, aperture)
        updateLux(lux)
    }

    // If only brightness is available
    fun onBrightnessOnly(brightness: Double) {
        val lux = ExposureCalculator.luxFromBrightnessOnly(brightness)
        updateLux(lux)
    }

    private fun updateLux(rawLux: Double) {
        if (rawLux <= 0.0) return
        luxBuffer.addLast(rawLux)
        if (luxBuffer.size > BUFFER_SIZE) luxBuffer.removeFirst()
        val smoothed = luxBuffer.average()
        _currentLux.postValue(smoothed)
        recompute(smoothed)
    }

    // Settings
    fun setMeteringMode(mode: MeteringMode) { _meteringMode.value = mode }

    fun setPriorityMode(mode: PriorityMode) {
        _priorityMode.value = mode
        // Reset EC when switching to Manual — it has no meaning there
        if (mode == PriorityMode.MANUAL) _evCompensation.value = 0f
        recompute(_currentLux.value ?: 0.0)
    }

    fun setIso(iso: Int) {
        _selectedIso.value = iso
        recompute(_currentLux.value ?: 0.0)
    }

    fun setAperture(aperture: Double) {
        _selectedAperture.value = aperture
        recompute(_currentLux.value ?: 0.0)
    }

    fun setShutter(shutterSpeed: Double) {
        _selectedShutter.value = shutterSpeed
        recompute(_currentLux.value ?: 0.0)
    }

    fun setSpotRegion(x: Float, y: Float) { _spotRegion.value = SpotRegion(x, y) }
    fun setCameraActive(active: Boolean)  { _cameraActive.value = active }

    /**
     * Sets exposure compensation (EV). Only meaningful in Av and Tv modes.
     * Positive values brighten the calculated exposure; negative values darken it.
     */
    fun setEvCompensation(ev: Float) {
        _evCompensation.value = ev.coerceIn(-3f, 3f)
        recompute(_currentLux.value ?: 0.0)
    }

    // Computation
    private fun recompute(lux: Double) {
        if (lux <= 0.0) return
        val iso      = _selectedIso.value      ?: 400
        val aperture = _selectedAperture.value ?: 2.8
        val shutter  = _selectedShutter.value  ?: (1.0 / 60)
        val ec       = _evCompensation.value   ?: 0f

        // EC: positive EC = more light = longer shutter or wider aperture.
        // The priority calculators produce MORE exposure when lux is LOWER
        // (darker scene → open up). So +1 EC divides lux by 2, giving 1 stop more exposure.
        val compensatedLux = lux / Math.pow(2.0, ec.toDouble())

        val reading = when (_priorityMode.value ?: PriorityMode.APERTURE_PRIORITY) {
            PriorityMode.APERTURE_PRIORITY ->
                ExposureCalculator.buildAperturePriorityReading(compensatedLux, iso, aperture)
            PriorityMode.SHUTTER_PRIORITY ->
                ExposureCalculator.buildShutterPriorityReading(compensatedLux, iso, shutter)
            PriorityMode.MANUAL ->
                // Manual ignores EC — lux is used as-is for the deviation display
                ExposureCalculator.buildManualReading(lux, iso, aperture, shutter)
        }

        // Sync back the calculated value to update ui
        when (_priorityMode.value) {
            PriorityMode.APERTURE_PRIORITY -> _selectedShutter.postValue(reading.shutterSpeed)
            PriorityMode.SHUTTER_PRIORITY  -> _selectedAperture.postValue(reading.aperture)
            else -> {}
        }

        _exposureReading.postValue(reading)
    }

    // Steppers
    fun stepIso(up: Boolean) {
        val current = _selectedIso.value ?: 400
        val idx = IsoValues.values.indexOf(current).coerceAtLeast(0)
        val newIdx = if (up) (idx + 1).coerceAtMost(IsoValues.values.lastIndex)
        else    (idx - 1).coerceAtLeast(0)
        setIso(IsoValues.values[newIdx])
    }

    fun stepAperture(up: Boolean) {
        val current = _selectedAperture.value ?: 2.8
        val idx = ApertureValues.values.indexOfFirst { it >= current - 0.01 }.coerceAtLeast(0)
        val newIdx = if (up) (idx + 1).coerceAtMost(ApertureValues.values.lastIndex)
        else    (idx - 1).coerceAtLeast(0)
        setAperture(ApertureValues.values[newIdx])
    }

    fun stepShutter(up: Boolean) {
        val current = _selectedShutter.value ?: (1.0 / 60)
        // shutter speeds are ordered from slowest to fastest (30s first, 1/4000 last)
        // Why 1/4000 as the limit you may ask? Because that's the highest film camera shutter speed ever (Nikon F3A)
        val idx = ShutterSpeedValues.values.indexOfFirst { it <= current + 1e-9 }.coerceAtLeast(0)
        val newIdx = if (up) (idx - 1).coerceAtLeast(0)
        else    (idx + 1).coerceAtMost(ShutterSpeedValues.values.lastIndex)
        setShutter(ShutterSpeedValues.values[newIdx])
    }
}