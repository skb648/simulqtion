package com.realitycompiler

import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera

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
    fun from(camera: Camera, width: Int, height: Int): CameraCalibration {
        return try {
            val chars = Camera2CameraInfo.extractCameraCharacteristics(camera.cameraInfo)
            val intrinsic = chars.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
            val distortion = chars.get(CameraCharacteristics.LENS_DISTORTION)?.toList() ?: emptyList()
            if (intrinsic != null && intrinsic.size >= 4) {
                CameraCalibration(width, height, intrinsic[0], intrinsic[1], intrinsic[2], intrinsic[3], distortion, CalibrationSource.DEVICE)
            } else {
                CameraCalibration(width, height, null, null, null, null, distortion, CalibrationSource.UNKNOWN)
            }
        } catch (_: Throwable) {
            CameraCalibration(width, height, null, null, null, emptyList(), CalibrationSource.UNKNOWN)
        }
    }
}
