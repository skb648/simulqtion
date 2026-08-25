package com.realitycompiler

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import java.util.EnumSet

data class DeviceCapabilities(
    val arCore: Boolean,
    val arCoreAvailability: String,
    val sharedCamera: Boolean,
    val depth: Boolean,
    val accelerometer: Boolean,
    val gyroscope: Boolean,
    val magnetometer: Boolean,
    val cameraIntrinsics: Boolean,
    val notes: List<String> = emptyList()
)

object CapabilityDetector {
    fun detect(context: Context): DeviceCapabilities {
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val gyroscope = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        val magnetometer = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
        var arCore = false
        var sharedCamera = false
        var depth = false
        var availabilityName = "UNKNOWN"
        val notes = mutableListOf<String>()
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            availabilityName = availability.name
            arCore = availability.isSupported
            if (arCore) {
                val session = Session(context, EnumSet.of(Session.Feature.SHARED_CAMERA))
                try {
                    sharedCamera = true
                    depth = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                } finally {
                    session.close()
                }
                if (availability.name != "SUPPORTED_INSTALLED") {
                    notes += "ARCore is supported but runtime install/update state is ${availability.name}."
                }
            } else {
                notes += "ARCore unavailable; using CameraX/device-sensor fallback."
            }
        } catch (t: Throwable) {
            notes += "ARCore/shared-camera capability check failed safely: ${t.javaClass.simpleName}."
        }
        return DeviceCapabilities(arCore, availabilityName, sharedCamera, depth, accelerometer, gyroscope, magnetometer, false, notes)
    }
}
