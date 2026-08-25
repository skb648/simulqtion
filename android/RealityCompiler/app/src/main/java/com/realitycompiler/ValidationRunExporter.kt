package com.realitycompiler

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ValidationRunExporter {
    fun export(
        context: Context,
        scanId: String,
        frames: List<CapturedFrameMetadata>,
        artifactId: String?,
        digitalTwinId: String?,
        simulationId: String?,
        testResult: String,
        outputDirectory: File = File(context.filesDir, "validation-runs")
    ): File {
        outputDirectory.mkdirs()
        val root = JSONObject()
            .put("artifactSchemaVersion", "2.1")
            .put("validationSchemaVersion", "2D.1")
            .put("scanId", scanId)
            .put("softwareVersion", BuildConfig.VERSION_NAME)
            .put("createdAtEpochMs", System.currentTimeMillis())
            .put("device", JSONObject()
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("androidVersion", Build.VERSION.RELEASE)
                .put("apiLevel", Build.VERSION.SDK_INT))
            .put("artifactId", artifactId)
            .put("digitalTwinId", digitalTwinId)
            .put("simulationId", simulationId)
            .put("testResult", testResult)

        val frameArray = JSONArray()
        frames.forEach { frame ->
            val calibration = frame.calibration
            frameArray.put(JSONObject()
                .put("frameId", frame.id)
                .put("imageTimestampNs", frame.imageTimestampNs)
                .put("arCoreTimestampNs", frame.arCoreTimestampNs)
                .put("timestampDeltaNs", frame.timestampDeltaNs)
                .put("width", frame.width)
                .put("height", frame.height)
                .put("trackingState", frame.arCore?.trackingState)
                .put("trackingFailureReason", frame.arCore?.trackingFailureReason)
                .put("translationM", JSONArray(frame.arCore?.translationM?.toList() ?: emptyList<Float>()))
                .put("rotationXyzw", JSONArray(frame.arCore?.rotationXyzw?.toList() ?: emptyList<Float>()))
                .put("poseMatrix", JSONArray(frame.arCore?.poseMatrix ?: emptyList<Float>()))
                .put("calibrationSource", calibration.source.toString())
                .put("imageWidth", calibration.imageWidth)
                .put("imageHeight", calibration.imageHeight)
                .put("focalLengthX", calibration.focalLengthX)
                .put("focalLengthY", calibration.focalLengthY)
                .put("principalPointX", calibration.principalPointX)
                .put("principalPointY", calibration.principalPointY)
                .put("distortion", JSONArray(calibration.distortion)))
        }
        root.put("frames", frameArray)
        val file = File(outputDirectory, "$scanId.json")
        file.writeText(root.toString(2))
        return file
    }
}
