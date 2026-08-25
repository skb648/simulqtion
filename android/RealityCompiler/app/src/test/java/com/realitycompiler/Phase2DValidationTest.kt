package com.realitycompiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2DValidationTest {
    @Test fun incompleteSessionCannotBecomeComplete() {
        val session = ValidationSession("s1")
        assertFalse(session.canComplete())
    }

    @Test fun referenceMeasurementUsesRawValues() {
        val result = ReferenceMeasurementMath.compare(100.0, 102.3)
        assertEquals(2.3, result.absoluteError!!, 1e-9)
        assertEquals(2.3, result.relativeErrorPct!!, 1e-9)
        assertEquals(1.023, result.scaleFactor!!, 1e-9)
    }

    @Test fun stateMachineAllowsDeviceCheckButNotDirectCompletion() {
        val session = ValidationSession("s1")
        assertEquals(ValidationState.DEVICE_CHECK, ValidationStateMachine.transition(ValidationState.NOT_STARTED, ValidationState.DEVICE_CHECK, session))
        var rejected = false
        try { ValidationStateMachine.transition(ValidationState.DEVICE_CHECK, ValidationState.COMPLETE, session) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun checklistDoesNotPromoteMissingPhysicalEvidence() {
        val report = CapabilityReport(listOf(CapabilityCheck("Android", CapabilityStatus.AVAILABLE)))
        val evidence = ValidationChecklist.build(report, physicalEvidencePresent = false)
        assertTrue(evidence.any { it.name == "Metric scale accuracy" && it.kind == EvidenceKind.UNKNOWN })
    }
}
