package com.realitycompiler

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.StatFs
import com.google.ar.core.ArCoreApk

data class CapabilityCheck(val name: String, val status: CapabilityStatus, val detail: String? = null)
data class CapabilityReport(val checks: List<CapabilityCheck>) {
    fun status(name: String): CapabilityStatus = checks.firstOrNull { it.name == name }?.status ?: CapabilityStatus.UNKNOWN

    fun withStatus(name: String, status: CapabilityStatus, detail: String? = null): CapabilityReport =
        CapabilityReport(checks.map { if (it.name == name) it.copy(status = status, detail = detail) else it })

    fun blockingReasons(): List<String> = REQUIRED_PHYSICAL_VALIDATION_CAPABILITIES.mapNotNull { name ->
        val check = checks.firstOrNull { it.name == name } ?: return@mapNotNull "$name: UNKNOWN"
        if (check.status == CapabilityStatus.AVAILABLE) null
        else "$name: ${check.status.name}${check.detail?.let { " — $it" } ?: ""}"
    }

    fun isReadyForPhysicalValidation(): Boolean = blockingReasons().isEmpty()

    companion object {
        val REQUIRED_PHYSICAL_VALIDATION_CAPABILITIES = listOf(
            "Camera",
            "ARCore",
            "Shared Camera",
            "Gyroscope",
            "Accelerometer",
            "Camera intrinsics",
            "Network"
        )
    }
}

object ValidationCapabilityValidator {
    fun check(context: Context, capabilities: DeviceCapabilities? = null): CapabilityReport {
        val detected = capabilities ?: CapabilityDetector.detect(context)
        val arStatus = runCatching { ArCoreApk.getInstance().checkAvailability(context).name }.getOrElse { "UNKNOWN" }
        val camera = context.packageManager.hasSystemFeature("android.hardware.camera.any")
        val camera2 = context.packageManager.hasSystemFeature("android.hardware.camera.any")
        val storage = runCatching { StatFs(context.filesDir.path).availableBytes > 50L * 1024L * 1024L }.getOrDefault(false)
        val network = runCatching { (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).activeNetwork != null }.getOrDefault(false)
        fun b(v: Boolean) = if (v) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE
        return CapabilityReport(listOf(
            CapabilityCheck("Android", CapabilityStatus.AVAILABLE, "${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}"),
            CapabilityCheck("Camera", b(camera)),
            CapabilityCheck("ARCore", if (detected.arCore) CapabilityStatus.AVAILABLE else if (arStatus == "UNKNOWN") CapabilityStatus.UNKNOWN else CapabilityStatus.UNAVAILABLE, arStatus),
            CapabilityCheck("ARCore version", if (arStatus == "UNKNOWN") CapabilityStatus.UNKNOWN else CapabilityStatus.AVAILABLE),
            CapabilityCheck("Shared Camera", b(detected.sharedCamera)),
            CapabilityCheck("Camera2", b(camera2)),
            CapabilityCheck("CPU image reader", if (detected.sharedCamera) CapabilityStatus.AVAILABLE else CapabilityStatus.UNAVAILABLE),
            CapabilityCheck("Depth", b(detected.depth)),
            CapabilityCheck("Gyroscope", b(detected.gyroscope)),
            CapabilityCheck("Accelerometer", b(detected.accelerometer)),
            CapabilityCheck("Magnetometer", b(detected.magnetometer)),
            CapabilityCheck("Camera intrinsics", if (detected.cameraIntrinsics) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_TESTED),
            CapabilityCheck("Network", b(network)),
            CapabilityCheck("Storage", b(storage), "at least 50 MiB free")
        ))
    }
}

object ValidationChecklist {
    fun build(report: CapabilityReport, physicalEvidencePresent: Boolean): List<ValidationEvidence> = listOf(
        ValidationEvidence("Android available", EvidenceKind.SOFTWARE_VERIFIED, report.status("Android").name, "capability-api"),
        ValidationEvidence("Camera available", EvidenceKind.DEVICE_CAPABILITY_REPORTED, report.status("Camera").name, "PackageManager"),
        ValidationEvidence("ARCore installed/supported", EvidenceKind.DEVICE_CAPABILITY_REPORTED, report.status("ARCore").name, "ArCoreApk"),
        ValidationEvidence("Shared Camera supported", EvidenceKind.DEVICE_CAPABILITY_REPORTED, report.status("Shared Camera").name, "ARCore Session"),
        ValidationEvidence("Gyroscope available", EvidenceKind.DEVICE_CAPABILITY_REPORTED, report.status("Gyroscope").name, "SensorManager"),
        ValidationEvidence("Accelerometer available", EvidenceKind.DEVICE_CAPABILITY_REPORTED, report.status("Accelerometer").name, "SensorManager"),
        ValidationEvidence("Physical tracking stability", if (physicalEvidencePresent) EvidenceKind.DEVICE_MEASURED else EvidenceKind.UNKNOWN, if (physicalEvidencePresent) "MEASURED" else "NOT MEASURED", "physical-run"),
        ValidationEvidence("Metric scale accuracy", if (physicalEvidencePresent) EvidenceKind.RECONSTRUCTION_MEASURED else EvidenceKind.UNKNOWN, if (physicalEvidencePresent) "MEASURED" else "NOT MEASURED", "physical-run")
    )
}
