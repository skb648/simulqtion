package com.realitycompiler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class PoseMathTest {
    @Test fun acceptsTwentyMillisecondSyncBoundary() {
        assertTrue(PoseMath.isSynchronized(20_000_000L))
    }

    @Test fun rejectsLargeTimestampDelta() {
        assertFalse(PoseMath.isSynchronized(20_000_001L))
    }

    @Test fun rejectsNegativeTimestampDelta() {
        assertFalse(PoseMath.isSynchronized(-1L))
    }

    @Test fun computesMetricTranslationDistance() {
        assertEquals(0.5f, PoseMath.translationDistance(floatArrayOf(0f, 0f, 0f), floatArrayOf(0.3f, 0.4f, 0f)), 1e-6f)
    }
}
