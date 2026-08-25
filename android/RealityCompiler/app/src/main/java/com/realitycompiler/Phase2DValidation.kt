package com.realitycompiler

import org.json.JSONArray
import org.json.JSONObject

enum class ValidationState { NOT_STARTED, DEVICE_CHECK, SCANNING, RECONSTRUCTION, MEASUREMENT, REPEATABILITY, MOTOR_VALIDATION, SIMULATION_VALIDATION, COMPLETE, BLOCKED, FAILED }
enum class EvidenceKind { SOFTWARE_VERIFIED, DEVICE_CAPABILITY_REPORTED, DEVICE_MEASURED, PHYSICAL_REFERENCE_MEASURED, RECONSTRUCTION_MEASURED, SIMULATION_EXECUTED, USER_REPORTED, UNKNOWN }
enum class CapabilityStatus { AVAILABLE, UNAVAILABLE, NOT_TESTED, UNKNOWN }

data class ValidationEvidence(val name: String, val kind: EvidenceKind, val value: String?, val source: String?)
data class ReferenceMeasurementRecord(val referenceValue: Double, val unit: String, val axis: String, val measurementMethod: String, val confidence: Double, val pointA: List<Double> = emptyList(), val pointB: List<Double> = emptyList(), val reconstructedValue: Double? = null, val absoluteError: Double? = null, val relativeErrorPct: Double? = null, val scaleFactor: Double? = null)
data class TimestampEvidence(val totalFrames: Int, val acceptedFrames: Int, val rejectedFrames: Int, val deltasMs: List<Double>, val trackingLossFrames: Int)
data class ValidationSession(
    val sessionId: String,
    val device: Map<String, String?> = emptyMap(),
    val softwareVersion: String? = null,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val scanId: String? = null,
    val referenceMeasurements: List<ReferenceMeasurementRecord> = emptyList(),
    val scanFrames: Int = 0,
    val poseStatistics: Map<String, Double?> = emptyMap(),
    val calibration: Map<String, Any?> = emptyMap(),
    val reconstructionMetrics: Map<String, Any?> = emptyMap(),
    val scaleMetrics: Map<String, Any?> = emptyMap(),
    val coverageMetrics: Map<String, Any?> = emptyMap(),
    val digitalTwinId: String? = null,
    val simulationId: String? = null,
    val whatIfId: String? = null,
    val result: ValidationState = ValidationState.NOT_STARTED,
    val blockingReasons: List<String> = emptyList(),
    val evidence: List<ValidationEvidence> = emptyList()
) {
    fun canComplete(): Boolean = result == ValidationState.SIMULATION_VALIDATION &&
        scanId != null && scanFrames > 0 &&
        referenceMeasurements.any { it.reconstructedValue != null && it.absoluteError != null && it.relativeErrorPct != null } &&
        evidence.any { it.kind == EvidenceKind.RECONSTRUCTION_MEASURED } &&
        digitalTwinId != null && simulationId != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("sessionId", sessionId)
        put("timestampEpochMs", timestampEpochMs)
        put("softwareVersion", softwareVersion)
        put("scanId", scanId)
        put("device", JSONObject(device))
        put("scanFrames", scanFrames)
        put("result", result.name)
        put("blockingReasons", JSONArray(blockingReasons))
        put("digitalTwinId", digitalTwinId)
        put("simulationId", simulationId)
        put("whatIfId", whatIfId)
        put("referenceMeasurements", JSONArray(referenceMeasurements.map { JSONObject().apply {
            put("referenceValue", it.referenceValue); put("unit", it.unit); put("axis", it.axis)
            put("measurementMethod", it.measurementMethod); put("confidence", it.confidence)
            put("pointA", JSONArray(it.pointA)); put("pointB", JSONArray(it.pointB))
            put("reconstructedValue", it.reconstructedValue); put("absoluteError", it.absoluteError)
            put("relativeErrorPct", it.relativeErrorPct); put("scaleFactor", it.scaleFactor)
        } }))
        put("poseStatistics", JSONObject(poseStatistics))
        put("reconstructionMetrics", JSONObject(reconstructionMetrics))
        put("scaleMetrics", JSONObject(scaleMetrics))
        put("coverageMetrics", JSONObject(coverageMetrics))
        put("calibration", JSONObject(calibration))
        put("evidence", JSONArray(evidence.map { JSONObject().apply {
            put("name", it.name); put("kind", it.kind.name); put("value", it.value); put("source", it.source)
        } }))
    }
}

object ValidationStateMachine {
    private val allowed = mapOf(
        ValidationState.NOT_STARTED to setOf(ValidationState.DEVICE_CHECK, ValidationState.BLOCKED),
        ValidationState.DEVICE_CHECK to setOf(ValidationState.SCANNING, ValidationState.BLOCKED, ValidationState.FAILED),
        ValidationState.SCANNING to setOf(ValidationState.RECONSTRUCTION, ValidationState.BLOCKED, ValidationState.FAILED),
        ValidationState.RECONSTRUCTION to setOf(ValidationState.MEASUREMENT, ValidationState.BLOCKED, ValidationState.FAILED),
        ValidationState.MEASUREMENT to setOf(ValidationState.REPEATABILITY, ValidationState.MOTOR_VALIDATION, ValidationState.BLOCKED, ValidationState.FAILED),
        ValidationState.REPEATABILITY to setOf(ValidationState.MOTOR_VALIDATION, ValidationState.SIMULATION_VALIDATION, ValidationState.BLOCKED, ValidationState.FAILED),
        ValidationState.MOTOR_VALIDATION to setOf(ValidationState.SIMULATION_VALIDATION, ValidationState.BLOCKED, ValidationState.FAILED),
        ValidationState.SIMULATION_VALIDATION to setOf(ValidationState.COMPLETE, ValidationState.BLOCKED, ValidationState.FAILED),
        ValidationState.COMPLETE to emptySet(), ValidationState.BLOCKED to setOf(ValidationState.DEVICE_CHECK), ValidationState.FAILED to setOf(ValidationState.DEVICE_CHECK)
    )
    fun transition(from: ValidationState, to: ValidationState, session: ValidationSession): ValidationState {
        require(to in (allowed[from] ?: emptySet())) { "Invalid validation transition: $from -> $to" }
        require(to != ValidationState.COMPLETE || session.copy(result = from).canComplete()) { "Cannot complete without mandatory physical evidence" }
        return to
    }
}

object ReferenceMeasurementMath {
    fun compare(reference: Double, reconstructed: Double): ReferenceMeasurementRecord {
        require(reference > 0.0 && reconstructed > 0.0)
        val abs = kotlin.math.abs(reconstructed - reference)
        return ReferenceMeasurementRecord(reference, "mm", "explicit-endpoints", "reconstructed-point-distance", 1.0, reconstructedValue = reconstructed, absoluteError = abs, relativeErrorPct = abs / reference * 100.0, scaleFactor = reconstructed / reference)
    }
}
