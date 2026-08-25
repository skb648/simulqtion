package com.realitycompiler

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> cameraGranted = granted }
    private var cameraGranted by mutableStateOf(false)
    private var imageCapture: ImageCapture? = null
    private var boundCamera: Camera? = null
    private var captureCount by mutableStateOf(0)
    private val capturedFiles = mutableListOf<File>()
    private val capturedMetadata = mutableListOf<CapturedFrameMetadata>()
    private var lastMessage by mutableStateOf("Move around the motor and capture distinct views.")
    private var capabilities by mutableStateOf<DeviceCapabilities?>(null)
    private lateinit var api: MotorApi
    private lateinit var sensorCollector: SensorCollector
    private val scanId = "scan-${UUID.randomUUID()}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = MotorApi(BackendConfig.load(this))
        sensorCollector = SensorCollector(this)
        sensorCollector.start()
        cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
        Thread { capabilities = CapabilityDetector.detect(this) }.start()
        setContent { RealityCompilerScreen() }
    }

    override fun onDestroy() {
        sensorCollector.stop()
        executor.shutdown()
        super.onDestroy()
    }

    @Composable
    private fun RealityCompilerScreen() {
        val context = LocalContext.current
        var showResults by remember { mutableStateOf(false) }
        var voltage by remember { mutableFloatStateOf(12f) }
        var twinStatus by remember { mutableStateOf("No digital twin yet") }
        var referenceDimension by remember { mutableStateOf("") }
        var backendUrl by remember { mutableStateOf(BackendConfig.load(context)) }
        var pointCloud by remember { mutableStateOf<List<FloatArray>>(emptyList()) }
        Surface(modifier = Modifier.fillMaxSize()) {
            if (showResults) {
                SimulationScreen(voltage = voltage, twinStatus = twinStatus, points = pointCloud, onBack = { showResults = false })
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (cameraGranted) AndroidView(factory = { PreviewView(it).also { view -> bindCamera(view) } }, modifier = Modifier.fillMaxSize())
                        else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Camera permission is required to scan a motor.")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant camera") }
                        }
                        Card(Modifier.align(Alignment.TopCenter).padding(16.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("DC MOTOR SCAN", style = MaterialTheme.typography.titleMedium)
                                Text("Views captured: $captureCount")
                                Text(lastMessage)
                                capabilities?.let {
                                    Text("ARCore: ${if (it.arCore) "AVAILABLE" else "UNAVAILABLE"}  Depth: ${if (it.depth) "AVAILABLE" else "UNAVAILABLE"}")
                                    Text("IMU: accel=${it.accelerometer} gyro=${it.gyroscope} magnetometer=${it.magnetometer}")
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = referenceDimension, onValueChange = { referenceDimension = it }, label = { Text("Optional known motor dimension (m)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    OutlinedTextField(value = backendUrl, onValueChange = { backendUrl = it }, label = { Text("Backend URL") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { BackendConfig.save(context, backendUrl); api = MotorApi(BackendConfig.load(context)); lastMessage = "Backend endpoint saved." }, modifier = Modifier.weight(1f)) { Text("Save endpoint") }
                        Button(onClick = { captureView(context) }, enabled = cameraGranted, modifier = Modifier.weight(1f)) { Text("Capture view") }
                    }
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            Thread {
                                try {
                                    val ref = referenceDimension.toDoubleOrNull()?.takeIf { it > 0 }
                                    val metadata = ScanMetadataBuilder(scanId, capturedMetadata.toList(), ref).toJson()
                                    val analysis = api.analyze(capturedFiles.toList(), metadata)
                                    val points = analysis.artifactId?.let { api.pointCloudPreview(it) } ?: emptyList()
                                    runOnUiThread { twinStatus = analysis.summary; pointCloud = points; lastMessage = if (points.isEmpty()) "Reconstruction completed without a point-cloud artifact." else "Cloud reconstruction complete: ${points.size} preview points loaded." }
                                } catch (e: Exception) {
                                    runOnUiThread { twinStatus = "Cloud reconstruction unavailable: ${e.message ?: "network error"}"; lastMessage = "Save Scan: captured images remain in local cache." }
                                }
                            }.start()
                        }, enabled = captureCount >= 3, modifier = Modifier.weight(1f)) { Text("Reconstruct") }
                        Button(onClick = { showResults = true }, enabled = captureCount >= 3, modifier = Modifier.weight(1f)) { Text("Open simulation") }
                    }
                }
            }
        }
    }

    private fun bindCamera(previewView: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = androidx.camera.core.Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            provider.unbindAll()
            boundCamera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureView(context: android.content.Context) {
        val capture = imageCapture ?: return
        val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val timestampNs = SystemClock.elapsedRealtimeNanos()
        capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                val width = options.outWidth.coerceAtLeast(1)
                val height = options.outHeight.coerceAtLeast(1)
                val calibration = boundCamera?.let { CameraCalibrationProvider.from(it, width, height) } ?: CameraCalibration(width, height, null, null, null, null, emptyList(), CalibrationSource.UNKNOWN)
                capturedFiles.add(file)
                capturedMetadata.add(CapturedFrameMetadata("frame-$captureCount", timestampNs, width, height, calibration, sensorCollector.snapshot(timestampNs)))
                captureCount += 1
                lastMessage = if (captureCount < 8) "Good. Rotate to a new side before the next capture." else "Capture set ready. Reconstruct to build the calibrated twin."
            }
            override fun onError(exception: ImageCaptureException) { lastMessage = "Capture failed: ${exception.message ?: "unknown camera error"}" }
        })
    }

    @Composable
    private fun SimulationScreen(voltage: Float, twinStatus: String, points: List<FloatArray>, onBack: () -> Unit) {
        var v by remember { mutableFloatStateOf(voltage) }
        var speed by remember { mutableFloatStateOf(0f) }
        var current by remember { mutableFloatStateOf(0f) }
        var status by remember { mutableStateOf("Simulation not run") }
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("RECONSTRUCTED DIGITAL TWIN", style = MaterialTheme.typography.headlineSmall)
            Text("Point cloud preview: ${points.size} points. Pinch/drag to inspect.")
            Box(Modifier.fillMaxWidth().height(240.dp)) { PointCloudView(points, Modifier.fillMaxSize()) }
            Text(twinStatus)
            Text("Observed: housing + shaft")
            Text("Inferred: brushed DC motor")
            Text("Unknown: winding configuration, magnet strength, exact inertia")
            Spacer(Modifier.height(10.dp))
            Text("Voltage: ${"%.1f".format(v)} V")
            Slider(value = v, onValueChange = { v = it }, valueRange = 1f..24f)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    Thread { try { val result = api.simulate(v); runOnUiThread { speed = result.speedRpm; current = result.currentA; status = "Real backend simulation complete" } } catch (e: Exception) { runOnUiThread { status = "Cloud processing unavailable: ${e.message ?: "network error"}" } } }.start()
                }) { Text("Run simulation") }
                Button(onClick = onBack) { Text("Back") }
            }
            Text(status)
            Text("Speed: ${"%.0f".format(speed)} rpm")
            Text("Current: ${"%.2f".format(current)} A")
            Button(onClick = {
                Thread { try { val result = api.compare(v, 24f); runOnUiThread { status = "What-If: 24 V → Δspeed=${"%.0f".format(result.speedDelta)} rpm, Δcurrent=${"%.2f".format(result.currentDelta)} A" } } catch (e: Exception) { runOnUiThread { status = "What-If unavailable: ${e.message ?: "network error"}" } } }.start()
            }) { Text("What If: 24 V") }
            Text("Simulation parameters are estimates unless measured.")
        }
    }
}
