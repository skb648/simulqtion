package com.realitycompiler

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/** ARCore Shared Camera: ARCore and the app ImageReader consume the same Camera2 stream. */
class ArCoreSharedCameraController(
    private val context: Context,
    private val imuSnapshotProvider: (Long) -> ImuSnapshot,
    private val onFrameCaptured: (File, CapturedFrameMetadata) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val handlerThread = HandlerThread("RealityCompiler-ARCore")
    private lateinit var handler: Handler
    private var session: Session? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var textureView: TextureView? = null
    private var cameraId: String? = null
    private var running = false
    private var lastPose: ArCorePoseSnapshot? = null
    private var frameCounter = 0
    private val pendingCaptureTimestamps = ConcurrentHashMap<Long, Long>()

    fun start(preview: TextureView) {
        textureView = preview
        if (!handlerThread.isAlive) handlerThread.start()
        handler = Handler(handlerThread.looper)
        if (!ArCoreApk.getInstance().checkAvailability(context).isSupported) {
            onStatus("ARCore unavailable; shared-camera mode disabled.")
            return
        }
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = startSessionAndCamera(surface, width, height)
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        if (preview.isAvailable) startSessionAndCamera(preview.surfaceTexture!!, preview.width, preview.height)
    }

    @SuppressLint("MissingPermission")
    private fun startSessionAndCamera(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        if (running) return
        try {
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            } ?: throw IllegalStateException("No back camera available")
            val id = cameraId ?: throw IllegalStateException("No camera available")
            val arSession = Session(context)
            val config = arSession.config
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            if (arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) config.depthMode = Config.DepthMode.AUTOMATIC
            arSession.configure(config)
            session = arSession
            val previewSurface = Surface(surfaceTexture)
            imageReader = ImageReader.newInstance(width.coerceAtLeast(640), height.coerceAtLeast(480), android.graphics.ImageFormat.JPEG, 4)
            imageReader?.setOnImageAvailableListener({ handleImage(it) }, handler)
            arSession.sharedCamera.setAppSurfaces(id, listOf(previewSurface, imageReader!!.surface))
            cameraManager.openCamera(id, cameraStateCallback, handler)
            running = true
            onStatus("ARCore shared camera starting…")
        } catch (t: Throwable) {
            onStatus("ARCore shared camera failed: ${t.message ?: t.javaClass.simpleName}")
            stop()
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            try {
                session?.resume()
                session?.sharedCamera?.setCaptureCallback(captureCallback, handler)
                session?.sharedCamera?.createCaptureSession(camera, object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                            textureView?.surfaceTexture?.let { request.addTarget(Surface(it)) }
                            imageReader?.surface?.let { request.addTarget(it) }
                            session.setRepeatingRequest(request.build(), captureCallback, handler)
                            onStatus("ARCore tracking active; shared camera configured.")
                        } catch (t: Throwable) {
                            onStatus("Camera request failed: ${t.message ?: t.javaClass.simpleName}")
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) = onStatus("ARCore shared camera capture session failed.")
                }, handler)
            } catch (t: Throwable) {
                onStatus("ARCore camera startup failed: ${t.message ?: t.javaClass.simpleName}")
            }
        }
        override fun onDisconnected(camera: CameraDevice) { onStatus("Camera disconnected."); camera.close(); cameraDevice = null }
        override fun onError(camera: CameraDevice, error: Int) { onStatus("Camera error: $error"); camera.close(); cameraDevice = null }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
            val timestamp = result.get(CaptureRequest.SENSOR_TIMESTAMP) ?: return
            pendingCaptureTimestamps[timestamp] = timestamp
            updateArCorePose(timestamp)
        }
    }

    private fun updateArCorePose(cameraTimestampNs: Long) {
        val arSession = session ?: return
        try {
            val frame: Frame = arSession.update()
            val camera = frame.camera
            val pose = camera.pose
            val matrix = FloatArray(16)
            pose.toMatrix(matrix, 0)
            lastPose = ArCorePoseSnapshot(frame.timestamp, pose.translation.clone(), pose.rotationQuaternion.clone(), matrix.toList(), camera.trackingState.name, camera.trackingFailureReason?.name)
        } catch (_: Throwable) { }
    }

    private fun handleImage(reader: ImageReader) {
        val image = try { reader.acquireLatestImage() } catch (_: Throwable) { null } ?: return
        image.use {
            val sensorTimestamp = it.timestamp
            val pose = lastPose ?: run { onStatus("Capture rejected: no ARCore pose available."); return }
            val delta = abs(sensorTimestamp - pose.timestampNs)
            if (delta > 20_000_000L) { onStatus("Capture rejected: timestamp delta ${delta / 1_000_000.0} ms."); return }
            if (pose.trackingState != "TRACKING") { onStatus("Capture rejected: ARCore tracking state is ${pose.trackingState}."); return }
            val plane = it.planes.firstOrNull() ?: return
            val bytes = ByteArray(plane.buffer.remaining())
            plane.buffer.get(bytes)
            val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}_$frameCounter.jpg")
            FileOutputStream(file).use { out -> out.write(bytes) }
            val width = it.width.coerceAtLeast(1)
            val height = it.height.coerceAtLeast(1)
            val calibration = cameraId?.let { id -> CameraCalibrationProvider.from(cameraManager, id, width, height, preferArCore = true) }
                ?: CameraCalibration(width, height, null, null, null, null, emptyList(), CalibrationSource.UNKNOWN)
            val metadata = CapturedFrameMetadata(
                id = "frame-$frameCounter", timestampNs = sensorTimestamp, width = width, height = height,
                calibration = calibration, imu = imuSnapshotProvider(sensorTimestamp), arCore = pose,
                imageTimestampNs = sensorTimestamp, arCoreTimestampNs = pose.timestampNs, timestampDeltaNs = delta
            )
            frameCounter += 1
            onFrameCaptured(file, metadata)
        }
    }

    fun stop() {
        try { captureSession?.close() } catch (_: Throwable) {}
        try { cameraDevice?.close() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { session?.pause() } catch (_: Throwable) {}
        try { session?.close() } catch (_: Throwable) {}
        captureSession = null; cameraDevice = null; imageReader = null; session = null; running = false
        if (handlerThread.isAlive) handlerThread.quitSafely()
    }
}

data class ArCorePoseSnapshot(
    val timestampNs: Long,
    val translationM: FloatArray,
    val rotationXyzw: FloatArray,
    val poseMatrix: List<Float>,
    val trackingState: String,
    val trackingFailureReason: String?
)
