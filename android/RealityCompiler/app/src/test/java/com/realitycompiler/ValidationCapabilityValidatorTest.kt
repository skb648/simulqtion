package com.realitycompiler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationCapabilityValidatorTest {
    private fun report(
        intrinsics: CapabilityStatus = CapabilityStatus.AVAILABLE,
        sharedCamera: CapabilityStatus = CapabilityStatus.AVAILABLE
    ) = CapabilityReport(listOf(
        CapabilityCheck("Camera", CapabilityStatus.AVAILABLE),
        CapabilityCheck("ARCore", CapabilityStatus.AVAILABLE),
        CapabilityCheck("Shared Camera", sharedCamera),
        CapabilityCheck("Gyroscope", CapabilityStatus.AVAILABLE),
        CapabilityCheck("Accelerometer", CapabilityStatus.AVAILABLE),
        CapabilityCheck("Camera intrinsics", intrinsics),
        CapabilityCheck("Network", CapabilityStatus.AVAILABLE)
    ))

    @Test
    fun readyWhenAllRequiredCapabilitiesAreAvailable() {
        assertTrue(report().isReadyForPhysicalValidation())
        assertTrue(report().blockingReasons().isEmpty())
    }

    @Test
    fun missingSharedCameraBlocksValidation() {
        val result = report(sharedCamera = CapabilityStatus.UNAVAILABLE)
        assertFalse(result.isReadyForPhysicalValidation())
        assertTrue(result.blockingReasons().any { it.startsWith("Shared Camera:") })
    }

    @Test
    fun untestedIntrinsicsNeverCountAsReady() {
        val result = report(intrinsics = CapabilityStatus.NOT_TESTED)
        assertFalse(result.isReadyForPhysicalValidation())
    }
}
