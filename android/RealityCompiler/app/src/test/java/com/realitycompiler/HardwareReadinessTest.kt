package com.realitycompiler

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareReadinessTest {
    private val ready = DeviceCapabilities(
        arCore = true,
        arCoreAvailability = "SUPPORTED_INSTALLED",
        sharedCamera = true,
        depth = false,
        accelerometer = true,
        gyroscope = true,
        magnetometer = false,
        cameraIntrinsics = true
    )

    @Test
    fun requiredCapabilitiesMustAllBeAvailable() {
        val report = DeviceCapabilityReport.from(
            ready,
            cameraPermission = true,
            camera2 = CapabilityState.AVAILABLE,
            cpuImage = CapabilityState.AVAILABLE,
            intrinsics = CapabilityState.AVAILABLE,
            network = CapabilityState.AVAILABLE
        )
        assertTrue(report.isValidationReady())
    }

    @Test
    fun missingSharedCameraBlocksValidation() {
        val report = DeviceCapabilityReport.from(
            ready.copy(sharedCamera = false),
            cameraPermission = true,
            camera2 = CapabilityState.AVAILABLE,
            cpuImage = CapabilityState.AVAILABLE,
            intrinsics = CapabilityState.AVAILABLE,
            network = CapabilityState.AVAILABLE
        )
        assertFalse(report.isValidationReady())
        assertTrue(report.blockingReasons().any { it.startsWith("SHARED_CAMERA:") })
    }

    @Test
    fun untestedCapabilityNeverCountsAsReady() {
        val report = DeviceCapabilityReport.from(
            ready,
            cameraPermission = true,
            camera2 = CapabilityState.AVAILABLE,
            cpuImage = CapabilityState.AVAILABLE,
            intrinsics = CapabilityState.NOT_TESTED,
            network = CapabilityState.AVAILABLE
        )
        assertFalse(report.isValidationReady())
    }

    @Test
    fun optionalDepthDoesNotBlockValidation() {
        val report = DeviceCapabilityReport.from(
            ready,
            cameraPermission = true,
            camera2 = CapabilityState.AVAILABLE,
            cpuImage = CapabilityState.AVAILABLE,
            intrinsics = CapabilityState.AVAILABLE,
            network = CapabilityState.AVAILABLE
        )
        assertTrue(report.isValidationReady())
    }
}
