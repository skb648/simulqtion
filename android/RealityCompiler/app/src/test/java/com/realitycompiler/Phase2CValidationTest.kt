package com.realitycompiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2CValidationTest {
    @Test fun referenceErrorIsComputedWithoutRounding() {
        val result = Phase2CValidation.compareReference(100.0, 102.3)
        assertEquals(2.3, result.absoluteError, 1e-9)
        assertEquals(2.3, result.relativeErrorPct, 1e-9)
        assertEquals(1.023, result.scaleFactor, 1e-9)
    }

    @Test fun repeatabilityStatisticsUseRawMeasurements() {
        val result = Phase2CValidation.statistics(listOf(101.2, 99.7, 100.8))
        assertEquals(3, result.sampleCount)
        assertEquals(100.5666666667, result.mean, 1e-9)
        assertEquals(100.8, result.median, 1e-9)
        assertEquals(99.7, result.minimum, 1e-9)
        assertEquals(101.2, result.maximum, 1e-9)
        assertEquals(1.5, result.range, 1e-9)
        assertTrue(result.standardDeviation > 0.0)
    }

    @Test fun timestampStatisticsReportsTwentyMillisecondAcceptanceRate() {
        val result = Phase2CValidation.timestampStatistics(listOf(5_000_000, 10_000_000, 25_000_000, 40_000_000))
        assertEquals(5.0, result.minimumMs, 1e-9)
        assertEquals(40.0, result.maximumMs, 1e-9)
        assertEquals(50.0, result.withinThresholdPct, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroReferenceIsRejected() {
        Phase2CValidation.compareReference(0.0, 1.0)
    }
}
