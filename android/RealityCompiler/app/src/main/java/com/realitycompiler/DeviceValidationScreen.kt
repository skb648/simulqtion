package com.realitycompiler

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.ar.core.ArCoreApk

@Composable
fun DeviceValidationScreen(
    context: Context,
    capabilities: DeviceCapabilities?,
    latestFrame: CapturedFrameMetadata?,
    frameCount: Int,
    rejectedFrames: Int,
    reconstructionStatus: String,
    onBack: () -> Unit
) {
    val arCoreVersion = try {
        context.packageManager.getPackageInfo("com.google.ar.core", 0).versionName ?: "unknown"
    } catch (_: Throwable) { "not installed" }
    val network = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.activeNetwork != null
    } catch (_: Throwable) { false }
    val calibration = latestFrame?.calibration
    val pose = latestFrame?.arCore
    val status = try { ArCoreApk.getInstance().checkAvailability(context).name } catch (_: Throwable) { "UNKNOWN" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PHYSICAL DEVICE VALIDATION", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onBack) { Text("Back") }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Device", style = MaterialTheme.typography.titleMedium)
                Text("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
                Text("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                Text("ARCore version: $arCoreVersion")
                Text("ARCore availability: $status")
                Text("Shared Camera: ${capabilities?.sharedCamera ?: false}")
                Text("Depth: ${capabilities?.depth ?: false}")
                Text("Gyroscope: ${capabilities?.gyroscope ?: false}")
                Text("Accelerometer: ${capabilities?.accelerometer ?: false}")
                Text("Magnetometer: ${capabilities?.magnetometer ?: false}")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Capture / Pose", style = MaterialTheme.typography.titleMedium)
                Text("Captured frames: $frameCount")
                Text("Rejected frames: $rejectedFrames")
                Text("Tracking: ${pose?.trackingState ?: "NOT MEASURED"}")
                Text("Pose available: ${pose != null}")
                Text("Image timestamp: ${latestFrame?.imageTimestampNs ?: "NOT MEASURED"}")
                Text("ARCore timestamp: ${latestFrame?.arCoreTimestampNs ?: "NOT MEASURED"}")
                Text("Timestamp delta: ${latestFrame?.timestampDeltaNs?.let { "%.3f ms".format(it / 1_000_000.0) } ?: "NOT MEASURED"}")
                Text("Camera FPS: NOT MEASURED")
                Text("Dropped frames: NOT MEASURED")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Camera calibration", style = MaterialTheme.typography.titleMedium)
                Text("Resolution: ${calibration?.imageWidth ?: "NOT MEASURED"} × ${calibration?.imageHeight ?: "NOT MEASURED"}")
                Text("fx: ${calibration?.focalLengthX ?: "NOT MEASURED"}")
                Text("fy: ${calibration?.focalLengthY ?: "NOT MEASURED"}")
                Text("cx: ${calibration?.principalPointX ?: "NOT MEASURED"}")
                Text("cy: ${calibration?.principalPointY ?: "NOT MEASURED"}")
                Text("Distortion: ${calibration?.distortion?.joinToString(prefix = "[", postfix = "]") ?: "NOT MEASURED"}")
                Text("Source: ${calibration?.source ?: "NOT MEASURED"}")
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Connectivity / pipeline", style = MaterialTheme.typography.titleMedium)
                Text("Network: ${if (network) "AVAILABLE" else "UNAVAILABLE"}")
                Text("Backend latency: NOT MEASURED")
                Text("Reconstruction: $reconstructionStatus")
                Spacer(Modifier.height(2.dp))
                Text("All values marked NOT MEASURED require physical capture evidence; they are never inferred.")
            }
        }
    }
}
