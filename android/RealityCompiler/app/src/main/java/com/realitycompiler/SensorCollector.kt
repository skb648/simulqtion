package com.realitycompiler

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

data class ImuSnapshot(
    val timestampNs: Long,
    val accelerometer: List<Float>?,
    val gyroscope: List<Float>?,
    val magnetometer: List<Float>?
)

/** Keeps the newest sensor sample from each available sensor. Camera capture time is the join key. */
class SensorCollector(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var accel: Pair<Long, List<Float>>? = null
    private var gyro: Pair<Long, List<Float>>? = null
    private var magnet: Pair<Long, List<Float>>? = null

    fun start() {
        register(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME)
        register(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_GAME)
        register(Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun register(type: Int, rate: Int) {
        manager.getDefaultSensor(type)?.let { manager.registerListener(this, it, rate) }
    }

    fun snapshot(cameraTimestampNs: Long): ImuSnapshot {
        fun near(sample: Pair<Long, List<Float>>?): List<Float>? {
            if (sample == null) return null
            // Keep samples close enough to the image timestamp to be useful without inventing interpolation.
            return if (abs(sample.first - cameraTimestampNs) <= 100_000_000L) sample.second else null
        }
        return ImuSnapshot(cameraTimestampNs, near(accel), near(gyro), near(magnet))
    }

    fun stop() { manager.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent) {
        val value = event.values.toList()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accel = event.timestamp to value
            Sensor.TYPE_GYROSCOPE -> gyro = event.timestamp to value
            Sensor.TYPE_MAGNETIC_FIELD -> magnet = event.timestamp to value
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
