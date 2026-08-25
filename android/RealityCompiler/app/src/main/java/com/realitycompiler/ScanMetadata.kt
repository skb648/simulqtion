package com.realitycompiler

import org.json.JSONArray
import org.json.JSONObject

data class CapturedFrameMetadata(
    val id: String,
    val timestampNs: Long,
    val width: Int,
    val height: Int,
    val calibration: CameraCalibration,
    val imu: ImuSnapshot,
    val arCore: ArCorePoseSnapshot? = null,
    val imageTimestampNs: Long = timestampNs,
    val arCoreTimestampNs: Long? = null,
    val timestampDeltaNs: Long? = null
)

class ScanMetadataBuilder(
    private val scanId: String,
    private val frames: List<CapturedFrameMetadata>,
    private val referenceDimensionM: Double?
) {
    fun toJson(): String {
        val root = JSONObject().apply {
            put("artifact_schema_version", "2.1")
            put("scan_id", scanId)
            put("coordinate_system", "ARCORE_WORLD_RIGHT_HANDED_X_RIGHT_Y_UP_CAMERA_FORWARD_NEGATIVE_Z")
            put("pose_convention", "CAMERA_TO_WORLD")
            put("units", "meters")
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
                put("image_timestamp_ns", frame.imageTimestampNs)
                frame.arCoreTimestampNs?.let { put("arcore_timestamp_ns", it) }
                frame.timestampDeltaNs?.let { put("timestamp_delta_ns", it) }
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
                frame.arCore?.let { pose ->
                    put("pose", JSONObject().apply {
                        put("timestamp_ns", pose.timestampNs)
                        put("translation_m", JSONArray(pose.translationM.toList()))
                        put("rotation_xyzw", JSONArray(pose.rotationXyzw.toList()))
                        put("pose_matrix_4x4", JSONArray(pose.poseMatrix))
                        put("tracking_state", pose.trackingState)
                        pose.trackingFailureReason?.let { put("tracking_failure_reason", it) }
                        put("source", "ARCORE")
                        put("coordinate_system", "ARCORE_WORLD_RIGHT_HANDED")
                        put("pose_convention", "CAMERA_TO_WORLD")
                        put("units", "meters")
                    })
                }
                put("imu_timestamp_ns", frame.imu.timestampNs)
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
