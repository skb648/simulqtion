package com.realitycompiler

import org.json.JSONArray
import org.json.JSONObject

data class CapturedFrameMetadata(
    val id: String,
    val timestampNs: Long,
    val width: Int,
    val height: Int,
    val calibration: CameraCalibration,
    val imu: ImuSnapshot
)

class ScanMetadataBuilder(
    private val scanId: String,
    private val frames: List<CapturedFrameMetadata>,
    private val referenceDimensionM: Double?
) {
    fun toJson(): String {
        val root = JSONObject().apply {
            put("scan_id", scanId)
            put("coordinate_system", "RIGHT_HANDED_CAMERA_OR_WORLD")
            put("reference_scale", JSONObject().apply {
                put("status", if (referenceDimensionM != null) "ESTIMATED" else "UNKNOWN")
                if (referenceDimensionM != null) put("dimension_m", referenceDimensionM)
                put("observed_extent_axis", "largest")
                put("confidence", if (referenceDimensionM != null) 0.75 else 0.0)
                put("source", if (referenceDimensionM != null) "USER_MEASURED_REFERENCE" else "UNKNOWN")
            })
        }
        val array = JSONArray()
        frames.forEach { frame ->
            array.put(JSONObject().apply {
                put("id", frame.id)
                put("timestamp_ns", frame.timestampNs)
                put("width", frame.width)
                put("height", frame.height)
                put("quality", 0.5)
                put("feature_points", 0)
                put("calibration", JSONObject().apply {
                    put("image_width", frame.calibration.imageWidth)
                    put("image_height", frame.calibration.imageHeight)
                    frame.calibration.focalLengthX?.let { put("focal_length_x", it) }
                    frame.calibration.focalLengthY?.let { put("focal_length_y", it) }
                    frame.calibration.principalPointX?.let { put("principal_point_x", it) }
                    frame.calibration.principalPointY?.let { put("principal_point_y", it) }
                    put("distortion", JSONArray(frame.calibration.distortion))
                    put("source", frame.calibration.source.name)
                })
                frame.imu.accelerometer?.let { put("accelerometer", JSONArray(it)) }
                frame.imu.gyroscope?.let { put("gyroscope", JSONArray(it)) }
                frame.imu.magnetometer?.let { put("magnetometer", JSONArray(it)) }
                frame.imu.rotationVector?.let { put("rotation_vector", JSONArray(it)) }
            })
        }
        root.put("frames", array)
        return root.toString()
    }
}
