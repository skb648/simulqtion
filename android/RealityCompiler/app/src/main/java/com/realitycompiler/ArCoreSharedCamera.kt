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
import com.google.ar.core.TrackingState
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * ARCore Shared Camera controller.
 *
 * The same Camera2 sensor stream is used by ARCore and the application ImageReader.
 * JPEG sensor timestamps are matched against ARCore Frame.timestamp values; no
 * elapsedRealtime timestamp is substituted for the camera sensor clock.
 */
class ArCoreSharedCameraController(
    private val context: Context,
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
    private var captureRequest: CaptureRequest.Builder? = null
    private var lastPose: ArCorePoseSnapshot? = null
    private val pendingCaptureTimestamps = ConcurrentHashMap<Long, Long>()
    private var frameCounter = 0
    private var running = false

    fun start(preview: TextureView) {
        textureView = preview
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        if (ArCoreApk.getInstance().checkAvailability(context).isSupported.not()) {
            onStatus("ARCore unavailable; shared-camera mode disabled.")
            return
        }
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                startSessionAndCamera(surface, width, height)
            }
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
            val ids = cameraManager.cameraIdList
            cameraId = ids.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
            } ?: ids.first()
            val id = cameraId ?: throw IllegalStateException("No camera available")
            val session = Session(context)
            val config = session.config
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) config.depthMode = Config.DepthMode.AUTOMATIC
            session.configure(config)
            this.session = session
            val previewSurface = Surface(surfaceTexture)
            imageReader = ImageReader.newInstance(width.coerceAtLeast(640), height.coerceAtLeast(480), android.graphics.ImageFormat.JPEG, 4)
            imageReader?.setOnImageAvailableListener({ reader -> handleImage(reader) }, handler)
            session.sharedCamera.setAppSurfaces(id, listOf(previewSurface, imageReader!!.surface))
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
                            captureRequest = request
                            session.setRepeatingRequest(request.build(), captureCallback, handler)
                            onStatus("ARCore tracking active; shared camera configured.")
                        } catch (t: Throwable) {
                            onStatus("Camera request failed: ${t.message ?: t.javaClass.simpleName}")
                        }
                    }
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onStatus("ARCore shared camera capture session failed.")
                    }
                }, handler)
            } catch (t: Throwable) {
                onStatus("ARCore camera startup failed: ${t.message ?: t.javaClass.simpleName}")
            }
        }
        override fun onDisconnected(camera: CameraDevice) {
            onStatus("Camera disconnected.")
            camera.close()
            cameraDevice = null
        }
        override fun onError(camera: CameraDevice, error: Int) {
            onStatus("Camera error: $error")
            camera.close()
            cameraDevice = null
        }
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
            lastPose = ArCorePoseSnapshot(
                timestampNs = frame.timestamp,
                translationM = pose.translation.clone(),
                rotationXyzw = pose.rotationQuaternion.clone(),
                poseMatrix = pose.toMatrix(FloatArray(16), 0).toList(),
                trackingState = camera.trackingState.name,
                trackingFailureReason = camera.trackingFailureReason?.name
            )
        } catch (_: Throwable) {
            // ARCore can transiently reject update while the shared camera is reconfiguring.
        }
    }

    private fun handleImage(reader: ImageReader) {
        val image = try { reader.acquireLatestImage() } catch (_: Throwable) { null } ?: return
        image.use {
            val sensorTimestamp = it.timestamp
            val pose = lastPose ?: return
            val delta = abs(sensorTimestamp - pose.timestampNs)
            if (delta > 20_000_000L) {
                onStatus("Capture rejected: image/ARCore timestamp delta ${delta / 1_000_000.0} ms.")
                return
            }
            val plane = it.planes.firstOrNull() ?: return
            val bytes = ByteArray(plane.buffer.remaining())
            plane.buffer.get(bytes)
            val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}_$frameCounter.jpg")
            FileOutputStream(file).use { out -> out.write(bytes) }
            val width = it.width.coerceAtLeast(1)
            val height = it.height.coerceAtLeast(1)
            val calibration = cameraId?.let { id ->
                CameraCalibrationProvider.from(cameraManager, id, width, height, preferArCore = true)
            } ?: CameraCalibration(width, height, null, null, null, null, emptyList(), CalibrationSource.UNKNOWN)
            val metadata = CapturedFrameMetadata(
                id = "frame-$frameCounter",
                timestampNs = sensorTimestamp,
                width = width,
                height = height,
                calibration = calibration,
                imu = ImuSnapshot(null, null, null, null),
                arCore = pose,
                imageTimestampNs = sensorTimestamp,
                arCoreTimestampNs = pose.timestampNs,
                timestampDeltaNs = delta
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
        captureSession = null
        cameraDevice = null
        imageReader = null
        session = null
        running = false
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
