package com.realitycompiler

import android.os.Build
import org.json.JSONObject

enum class CapabilityState { AVAILABLE, UNAVAILABLE, UNKNOWN, NOT_TESTED }

data class CapabilityResult(
    val name: String,
    val state: CapabilityState,
    val detail: String? = null
)

data class DeviceCapabilityReport(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val capabilities: List<CapabilityResult>
) {
    fun isValidationReady(): Boolean = capabilities
        .filter { it.name in REQUIRED_VALIDATION_CAPABILITIES }
        .all { it.state == CapabilityState.AVAILABLE }

    fun blockingReasons(): List<String> = capabilities
        .filter { it.name in REQUIRED_VALIDATION_CAPABILITIES && it.state != CapabilityState.AVAILABLE }
        .map { "${it.name}: ${it.state.name}${it.detail?.let { detail -> " — $detail" } ?: ""}" }

    fun toJson(): JSONObject = JSONObject().apply {
        put("manufacturer", manufacturer)
        put("model", model)
        put("androidVersion", androidVersion)
        put("apiLevel", apiLevel)
        put("capabilities", org.json.JSONArray(capabilities.map { capability ->
            JSONObject().apply {
                put("name", capability.name)
                put("state", capability.state.name)
                put("detail", capability.detail)
            }
        }))
        put("validationReady", isValidationReady())
        put("blockingReasons", org.json.JSONArray(blockingReasons()))
    }

    companion object {
        val REQUIRED_VALIDATION_CAPABILITIES = setOf(
            "CAMERA",
            "ARCORE",
            "SHARED_CAMERA",
            "GYROSCOPE",
            "ACCELEROMETER",
            "CAMERA_INTRINSICS",
            "NETWORK"
        )

        fun from(
            capabilities: DeviceCapabilities?,
            cameraPermission: Boolean,
            camera2: CapabilityState,
            cpuImage: CapabilityState,
            intrinsics: CapabilityState,
            network: CapabilityState
        ): DeviceCapabilityReport {
            val base = listOf(
                CapabilityResult("CAMERA", if (cameraPermission) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE, "camera permission"),
                CapabilityResult("ARCORE", capabilities?.let { if (it.arCore) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE } ?: CapabilityState.NOT_TESTED, capabilities?.arCoreAvailability),
                CapabilityResult("SHARED_CAMERA", capabilities?.let { if (it.sharedCamera) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE } ?: CapabilityState.NOT_TESTED),
                CapabilityResult("DEPTH", capabilities?.let { if (it.depth) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE } ?: CapabilityState.NOT_TESTED),
                CapabilityResult("GYROSCOPE", capabilities?.let { if (it.gyroscope) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE } ?: CapabilityState.NOT_TESTED),
                CapabilityResult("ACCELEROMETER", capabilities?.let { if (it.accelerometer) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE } ?: CapabilityState.NOT_TESTED),
                CapabilityResult("MAGNETOMETER", capabilities?.let { if (it.magnetometer) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE } ?: CapabilityState.NOT_TESTED),
                CapabilityResult("CAMERA2", camera2),
                CapabilityResult("CPU_IMAGE_READER", cpuImage),
                CapabilityResult("CAMERA_INTRINSICS", intrinsics),
                CapabilityResult("NETWORK", network)
            )
            return DeviceCapabilityReport(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, base)
        }
    }
}
