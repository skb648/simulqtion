package com.realitycompiler

import kotlin.math.abs
import kotlin.math.sqrt

data class ReferenceMeasurement(
    val referenceValue: Double,
    val referenceUnit: String,
    val referenceAxis: String,
    val measurementMethod: String,
    val measurementConfidence: Double
)

data class ScaleMeasurement(
    val referenceValue: Double,
    val measuredValue: Double,
    val absoluteError: Double,
    val relativeErrorPct: Double,
    val scaleFactor: Double
)

data class RepeatabilityStatistics(
    val sampleCount: Int,
    val mean: Double,
    val median: Double,
    val standardDeviation: Double,
    val minimum: Double,
    val maximum: Double,
    val range: Double,
    val percentile95: Double?
)

object Phase2CValidation {
    fun compareReference(reference: Double, measured: Double): ScaleMeasurement {
        require(reference > 0.0) { "Reference dimension must be positive" }
        require(measured >= 0.0) { "Measured dimension cannot be negative" }
        val absolute = abs(measured - reference)
        return ScaleMeasurement(reference, measured, absolute, absolute / reference * 100.0, measured / reference)
    }

    fun statistics(values: List<Double>): RepeatabilityStatistics {
        require(values.isNotEmpty()) { "At least one measurement is required" }
        val sorted = values.sorted()
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        val median = if (sorted.size % 2 == 0) {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        } else sorted[sorted.size / 2]
        val percentile95 = if (sorted.size >= 2) {
            val index = 0.95 * (sorted.size - 1)
            val lower = index.toInt()
            val upper = kotlin.math.ceil(index).toInt()
            if (lower == upper) sorted[lower] else sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower)
        } else null
        return RepeatabilityStatistics(values.size, mean, median, sqrt(variance), sorted.first(), sorted.last(), sorted.last() - sorted.first(), percentile95)
    }

    fun timestampStatistics(deltasNs: List<Long>, thresholdNs: Long = 20_000_000L): TimestampStatistics {
        require(deltasNs.isNotEmpty()) { "At least one timestamp delta is required" }
        val values = deltasNs.map { it.toDouble() / 1_000_000.0 }
        val sorted = values.sorted()
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        val median = if (sorted.size % 2 == 0) (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0 else sorted[sorted.size / 2]
        return TimestampStatistics(
            minimumMs = sorted.first(), maximumMs = sorted.last(), meanMs = mean, medianMs = median,
            standardDeviationMs = sqrt(variance), withinThresholdPct = values.count { it <= thresholdNs / 1_000_000.0 } * 100.0 / values.size
        )
    }
}

data class TimestampStatistics(
    val minimumMs: Double,
    val maximumMs: Double,
    val meanMs: Double,
    val medianMs: Double,
    val standardDeviationMs: Double,
    val withinThresholdPct: Double
)
