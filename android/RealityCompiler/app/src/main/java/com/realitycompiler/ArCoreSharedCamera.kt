package com.realitycompiler

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.EnumSet
import kotlin.math.abs

/**
 * ARCore Shared Camera controller based on the official shared-camera architecture:
 * ARCore owns its camera surfaces, while the app adds a CPU ImageReader surface and
 * a preview surface to the same Camera2 capture session.
 */
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
    private var captureRequested = false
    private var lastPose: ArCorePoseSnapshot? = null
    private var frameCounter = 0

    fun start(preview: TextureView) {
        textureView = preview
        if (!handlerThread.isAlive) handlerThread.start()
        handler = Handler(handlerThread.looper)
        if (!ArCoreApk.getInstance().checkAvailability(context).isSupported) {
            onStatus("ARCore unavailable; shared-camera mode disabled.")
            return
        }
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = openCamera(surface, width, height)
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        if (preview.isAvailable) openCamera(preview.surfaceTexture!!, preview.width, preview.height)
    }

    fun requestCapture() {
        if (!running) { onStatus("ARCore shared camera is not ready."); return }
        captureRequested = true
        onStatus("Capture requested; waiting for a synchronized tracked frame…")
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(previewTexture: SurfaceTexture, previewWidth: Int, previewHeight: Int) {
        if (running) return
        try {
            val arSession = Session(context, EnumSet.of(Session.Feature.SHARED_CAMERA))
            val config = arSession.config
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            if (arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) config.depthMode = Config.DepthMode.AUTOMATIC
            arSession.configure(config)
            session = arSession
            cameraId = arSession.cameraConfig.cameraId
            val imageSize = arSession.cameraConfig.imageSize
            imageReader = ImageReader.newInstance(imageSize.width, imageSize.height, ImageFormat.YUV_420_888, 2)
            imageReader?.setOnImageAvailableListener({ handleImage(it) }, handler)

            val sharedCamera = arSession.sharedCamera
            val appSurfaces = ArrayList<Surface>()
            appSurfaces.add(Surface(previewTexture))
            appSurfaces.add(imageReader!!.surface)
            sharedCamera.setAppSurfaces(cameraId!!, appSurfaces)

            val wrappedCallback = sharedCamera.createARDeviceStateCallback(cameraStateCallback, handler)
            cameraManager.openCamera(cameraId!!, wrappedCallback, handler)
            running = true
            onStatus("ARCore Shared Camera session starting…")
        } catch (t: Throwable) {
            onStatus("ARCore Shared Camera failed: ${t.message ?: t.javaClass.simpleName}")
            stop()
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            try {
                session?.resume()
                session?.sharedCamera?.setCaptureCallback(captureCallback, handler)
                createCaptureSession(camera)
            } catch (t: Throwable) {
                onStatus("ARCore camera startup failed: ${t.message ?: t.javaClass.simpleName}")
            }
        }
        override fun onDisconnected(camera: CameraDevice) { onStatus("Camera disconnected."); camera.close(); cameraDevice = null }
        override fun onError(camera: CameraDevice, error: Int) { onStatus("Camera error: $error"); camera.close(); cameraDevice = null }
    }

    private fun createCaptureSession(camera: CameraDevice) {
        try {
            val sharedCamera = session!!.sharedCamera
            val surfaces = ArrayList(sharedCamera.arCoreSurfaces)
            surfaces.add(Surface(textureView!!.surfaceTexture))
            surfaces.add(imageReader!!.surface)
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            surfaces.forEach { request.addTarget(it) }
            val wrappedCallback = sharedCamera.createARSessionStateCallback(object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    try {
                        session.setRepeatingRequest(request.build(), captureCallback, handler)
                        onStatus("ARCore tracking active; shared camera configured.")
                    } catch (t: Throwable) {
                        onStatus("Repeating camera request failed: ${t.message ?: t.javaClass.simpleName}")
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) = onStatus("ARCore shared camera capture session failed.")
            }, handler)
            camera.createCaptureSession(surfaces, wrappedCallback, handler)
        } catch (t: Throwable) {
            onStatus("Shared camera session creation failed: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
            updateArCorePose()
        }
    }

    private fun updateArCorePose() {
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
        if (!captureRequested) {
            try { reader.acquireLatestImage()?.close() } catch (_: Throwable) {}
            return
        }
        val image = try { reader.acquireLatestImage() } catch (_: Throwable) { null } ?: return
        image.use {
            val sensorTimestamp = it.timestamp
            val pose = lastPose ?: run { onStatus("Capture rejected: no ARCore pose available."); return }
            val delta = abs(sensorTimestamp - pose.timestampNs)
            if (delta > PoseMath.MAX_SYNC_DELTA_NS) { onStatus("Capture rejected: timestamp delta ${delta / 1_000_000.0} ms."); return }
            if (pose.trackingState != "TRACKING") { onStatus("Capture rejected: ARCore tracking state is ${pose.trackingState}."); return }
            val jpeg = yuv420ToJpeg(it) ?: run { onStatus("Capture rejected: YUV conversion failed."); return }
            val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}_$frameCounter.jpg")
            FileOutputStream(file).use { out -> out.write(jpeg) }
            val width = it.width.coerceAtLeast(1)
            val height = it.height.coerceAtLeast(1)
            val calibration = cameraId?.let { id -> CameraCalibrationProvider.from(cameraManager, id, width, height, preferArCore = true) }
                ?: CameraCalibration(width, height, null, null, null, null, emptyList(), CalibrationSource.UNKNOWN)
            val metadata = CapturedFrameMetadata(
                id = "frame-$frameCounter", timestampNs = sensorTimestamp, width = width, height = height,
                calibration = calibration, imu = imuSnapshotProvider(sensorTimestamp), arCore = pose,
                imageTimestampNs = sensorTimestamp, arCoreTimestampNs = pose.timestampNs, timestampDeltaNs = delta
            )
            captureRequested = false
            frameCounter += 1
            onFrameCaptured(file, metadata)
        }
    }

    private fun yuv420ToJpeg(image: Image): ByteArray? {
        return try {
            val y = image.planes[0]
            val u = image.planes[1]
            val v = image.planes[2]
            val nv21 = ByteArray(image.width * image.height * 3 / 2)
            var offset = 0
            for (row in 0 until image.height) {
                val rowStart = row * y.rowStride
                for (col in 0 until image.width) {
                    nv21[offset++] = y.buffer.get(rowStart + col * y.pixelStride)
                }
            }
            val chromaHeight = image.height / 2
            val chromaWidth = image.width / 2
            var uvOffset = image.width * image.height
            for (row in 0 until chromaHeight) {
                val uRow = row * u.rowStride
                val vRow = row * v.rowStride
                for (col in 0 until chromaWidth) {
                    nv21[uvOffset++] = v.buffer.get(vRow + col * v.pixelStride)
                    nv21[uvOffset++] = u.buffer.get(uRow + col * u.pixelStride)
                }
            }
            val output = ByteArrayOutputStream()
            YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null).compressToJpeg(Rect(0, 0, image.width, image.height), 92, output)
            output.toByteArray()
        } catch (_: Throwable) { null }
    }

    fun stop() {
        try { captureSession?.close() } catch (_: Throwable) {}
        try { cameraDevice?.close() } catch (_: Throwable) {}
        try { imageReader?.close() } catch (_: Throwable) {}
        try { session?.pause() } catch (_: Throwable) {}
        try { session?.close() } catch (_: Throwable) {}
        captureSession = null; cameraDevice = null; imageReader = null; session = null; running = false; captureRequested = false
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
