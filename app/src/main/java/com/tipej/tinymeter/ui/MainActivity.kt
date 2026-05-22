package com.tipej.tinymeter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.FocusMeteringAction
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.tipej.tinymeter.databinding.ActivityMainBinding
import com.tipej.tinymeter.model.MeteringMode
import com.tipej.tinymeter.model.PriorityMode
import com.tipej.tinymeter.utils.LuminosityAnalyzer
import com.tipej.tinymeter.viewmodel.LightMeterViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.log2

@ExperimentalCamera2Interop
class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LightMeterViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private lateinit var analyzer: LuminosityAnalyzer

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showPermissionDenied()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(_binding!!.root)
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupControls()
        observeViewModel()
        checkPermission()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        _binding = null
    }

    // Camera setup

    private fun checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else requestPermission.launch(Manifest.permission.CAMERA)
    }

    // Start the camera and request permissions if needed
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }

            analyzer = LuminosityAnalyzer(viewModel)

            // Build ImageAnalysis
            val analysisBuilder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)

            Camera2Interop.Extender(analysisBuilder)
                .setSessionCaptureCallback(object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: android.hardware.camera2.CameraCaptureSession,
                        request: android.hardware.camera2.CaptureRequest,
                        result: android.hardware.camera2.TotalCaptureResult
                    ) {
                        val exposureNs  = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
                        val iso         = result.get(CaptureResult.SENSOR_SENSITIVITY)   ?: 0
                        // LENS_APERTURE may be null on fixed-aperture lenses; read from
                        // characteristics as fallback (set once after bind, see below)
                        val aperture    = result.get(CaptureResult.LENS_APERTURE)
                        if (aperture != null) analyzer.sensorAperture = aperture
                        analyzer.sensorExposureTimeNs = exposureNs
                        analyzer.sensorIso            = iso
                    }
                })

            val imageAnalysis = analysisBuilder.build().also {
                it.setAnalyzer(cameraExecutor, analyzer)
            }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )

                // seed the aperture so the first few frames (before any CaptureResult arrives) already have a sane value.
                val cam2Info = Camera2CameraInfo.from(camera!!.cameraInfo)
                val apertures = cam2Info.getCameraCharacteristic(
                    CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES
                )
                apertures?.firstOrNull()?.let { analyzer.sensorAperture = it }

                viewModel.setCameraActive(true)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showPermissionDenied() {
        Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
    }

    // Controls
    private fun setupControls() {
        binding.btnMeteringAverage.setOnClickListener { viewModel.setMeteringMode(MeteringMode.AVERAGE) }
        binding.btnMeteringSpot.setOnClickListener    { viewModel.setMeteringMode(MeteringMode.SPOT) }

        binding.btnModeAp.setOnClickListener     { viewModel.setPriorityMode(PriorityMode.APERTURE_PRIORITY) }
        binding.btnModeSp.setOnClickListener     { viewModel.setPriorityMode(PriorityMode.SHUTTER_PRIORITY) }
        binding.btnModeManual.setOnClickListener { viewModel.setPriorityMode(PriorityMode.MANUAL) }

        binding.btnIsoDown.setOnClickListener { viewModel.stepIso(false) }
        binding.btnIsoUp.setOnClickListener   { viewModel.stepIso(true) }

        binding.btnApertureDown.setOnClickListener { viewModel.stepAperture(true) }
        binding.btnApertureUp.setOnClickListener   { viewModel.stepAperture(false) }

        binding.btnShutterDown.setOnClickListener { viewModel.stepShutter(false) }
        binding.btnShutterUp.setOnClickListener   { viewModel.stepShutter(true) }

        binding.spotOverlay.onSpotMoved = { x, y -> viewModel.setSpotRegion(x, y) }

        binding.btnIso100.setOnClickListener  { viewModel.setIso(100) }
        binding.btnIso400.setOnClickListener  { viewModel.setIso(400) }
        binding.btnIso800.setOnClickListener  { viewModel.setIso(800) }
        binding.btnIso1600.setOnClickListener { viewModel.setIso(1600) }
        binding.btnIso3200.setOnClickListener { viewModel.setIso(3200) }

        binding.btnAp14.setOnClickListener { viewModel.setAperture(1.4) }
        binding.btnAp28.setOnClickListener { viewModel.setAperture(2.8) }
        binding.btnAp56.setOnClickListener { viewModel.setAperture(5.6) }
        binding.btnAp8.setOnClickListener  { viewModel.setAperture(8.0) }
        binding.btnAp11.setOnClickListener  { viewModel.setAperture(11.0) }
        binding.btnAp16.setOnClickListener { viewModel.setAperture(16.0) }

        binding.btnSh30.setOnClickListener   { viewModel.setShutter(30.0) }
        binding.btnSh125.setOnClickListener  { viewModel.setShutter(1.0 / 125) }
        binding.btnSh250.setOnClickListener  { viewModel.setShutter(1.0 / 250) }
        binding.btnSh500.setOnClickListener  { viewModel.setShutter(1.0 / 500) }
        binding.btnSh1000.setOnClickListener { viewModel.setShutter(1.0 / 1000) }

        // Wire EC touch callback — view calls this whenever the user drags the scale
        binding.exposureScale.onCompensationChanged = { ev ->
            viewModel.setEvCompensation(ev)
        }
    }

    // Observers
    private fun observeViewModel() {
        viewModel.exposureReading.observe(this) { reading ->
            reading ?: return@observe

            binding.tvEv.text               = reading.evDisplay
            binding.tvLux.text              = reading.luxDisplay
            binding.tvLightDescription.text = reading.lightDescription
            binding.tvIsoValue.text         = reading.isoDisplay
            binding.tvApertureValue.text    = reading.apertureDisplay
            binding.tvShutterValue.text     = reading.shutterSpeedDisplay

            val priority = viewModel.priorityMode.value ?: PriorityMode.APERTURE_PRIORITY
            val ev100 = if (reading.lux > 0) log2(reading.lux * 2.55) else 0.0
            val settingsEv = if (reading.shutterSpeed > 0 && reading.iso > 0)
                log2((reading.aperture * reading.aperture) / reading.shutterSpeed) - log2(reading.iso / 100.0)
            else 0.0

            binding.exposureScale.evDeviation = when (priority) {
                PriorityMode.MANUAL -> (ev100 - settingsEv).toFloat().coerceIn(-3f, 3f)
                else -> 0f
            }

            binding.tvUnderExposed.visibility = if (reading.isUnderExposed) View.VISIBLE else View.GONE
            binding.tvOverExposed.visibility  = if (reading.isOverExposed)  View.VISIBLE else View.GONE
        }

        viewModel.meteringMode.observe(this) { mode ->
            val isSpot = mode == MeteringMode.SPOT
            binding.spotOverlay.isSpotMode        = isSpot
            binding.btnMeteringAverage.isSelected = !isSpot
            binding.btnMeteringSpot.isSelected    = isSpot
            binding.spotOverlay.isClickable       = isSpot
            if (isSpot) {
                val region = viewModel.spotRegion.value ?: return@observe
                updateMeteringPoint(region.x, region.y)
            } else {
                resetToAverageMetering()
            }
        }
        // Wait for selected spot to change and then update the metering point
        viewModel.spotRegion.observe(this) { region ->
            binding.spotOverlay.spotX = region.x
            binding.spotOverlay.spotY = region.y
            if (viewModel.meteringMode.value == MeteringMode.SPOT) {
                updateMeteringPoint(region.x, region.y)
            }
        }

        viewModel.priorityMode.observe(this) { mode ->
            binding.btnModeAp.isSelected     = mode == PriorityMode.APERTURE_PRIORITY
            binding.btnModeSp.isSelected     = mode == PriorityMode.SHUTTER_PRIORITY
            binding.btnModeManual.isSelected = mode == PriorityMode.MANUAL

            val apMode = mode == PriorityMode.APERTURE_PRIORITY
            val spMode = mode == PriorityMode.SHUTTER_PRIORITY

            binding.btnApertureDown.isEnabled = !spMode
            binding.btnApertureUp.isEnabled   = !spMode
            binding.tvApertureValue.alpha     = if (!spMode) 1f else 0.5f

            binding.btnShutterDown.isEnabled = !apMode
            binding.btnShutterUp.isEnabled   = !apMode
            binding.tvShutterValue.alpha     = if (!apMode) 1f else 0.5f

            binding.tvModeLabel.text = when (mode) {
                PriorityMode.APERTURE_PRIORITY -> "Aperture Priority  (Av)"
                PriorityMode.SHUTTER_PRIORITY -> "Shutter Priority  (Tv)"
                PriorityMode.MANUAL -> "Manual  (M)"
            }

            binding.tvCalculatedShutter.visibility  = if (apMode) View.VISIBLE else View.GONE
            binding.tvCalculatedAperture.visibility = if (spMode) View.VISIBLE else View.GONE

            // Enable EC interaction only in priority modes
            binding.exposureScale.ecEnabled = apMode || spMode
        }

        viewModel.spotRegion.observe(this) { region ->
            binding.spotOverlay.spotX = region.x
            binding.spotOverlay.spotY = region.y
        }

        // Keep the scale's EC marker in sync with the ViewModel (e.g. when mode resets it to 0)
        viewModel.evCompensation.observe(this) { ec ->
            binding.exposureScale.evCompensation = ec
        }
    }
    private fun resetToAverageMetering() {
        camera?.cameraControl?.cancelFocusAndMetering()
    }
    private fun updateMeteringPoint(x: Float, y: Float) {
        val camera = camera ?: return
        val factory = binding.cameraPreview.meteringPointFactory
        val point = factory.createPoint(
            x * binding.cameraPreview.width,
            y * binding.cameraPreview.height,
            0.1f
        )
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AE)
            .disableAutoCancel()
            .build()
        camera.cameraControl.startFocusAndMetering(action)
    }
}