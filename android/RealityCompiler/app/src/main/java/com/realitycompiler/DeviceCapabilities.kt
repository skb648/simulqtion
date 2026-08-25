package com.realitycompiler

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

data class DeviceCapabilities(
    val arCore: Boolean,
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
        var depth = false
        var cameraIntrinsics = false
        val notes = mutableListOf<String>()
        try {
            arCore = ArCoreApk.getInstance().checkAvailability(context).isSupported
            if (arCore) {
                val session = Session(context)
                try {
                    depth = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                } finally {
                    session.close()
                }
            } else {
                notes += "ARCore unavailable; using CameraX/device-sensor fallback."
            }
        } catch (t: Throwable) {
            notes += "ARCore capability check failed safely: ${t.javaClass.simpleName}."
        }
        return DeviceCapabilities(arCore, depth, accelerometer, gyroscope, magnetometer, cameraIntrinsics, notes)
    }
}
