package com.realitycompiler

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

enum class CalibrationSource { DEVICE, ARCORE, ESTIMATION, UNKNOWN }

data class CameraCalibration(
    val imageWidth: Int,
    val imageHeight: Int,
    val focalLengthX: Float?,
    val focalLengthY: Float?,
    val principalPointX: Float?,
    val principalPointY: Float?,
    val distortion: List<Float> = emptyList(),
    val source: CalibrationSource
)

object CameraCalibrationProvider {
    fun from(manager: CameraManager, cameraId: String, width: Int, height: Int, preferArCore: Boolean = false): CameraCalibration {
        return try {
            val chars = manager.getCameraCharacteristics(cameraId)
            val intrinsic = chars.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
            val distortion = chars.get(CameraCharacteristics.LENS_DISTORTION)?.toList() ?: emptyList()
            if (intrinsic != null && intrinsic.size >= 4) {
                CameraCalibration(
                    width, height, intrinsic[0], intrinsic[1], intrinsic[2], intrinsic[3], distortion,
                    if (preferArCore) CalibrationSource.ARCORE else CalibrationSource.DEVICE
                )
            } else {
                CameraCalibration(width, height, null, null, null, null, distortion, CalibrationSource.UNKNOWN)
            }
        } catch (_: Throwable) {
            CameraCalibration(width, height, null, null, null, null, emptyList(), CalibrationSource.UNKNOWN)
        }
    }
}
