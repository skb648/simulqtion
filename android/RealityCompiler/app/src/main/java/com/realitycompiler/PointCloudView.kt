package com.realitycompiler

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PointCloudView(points: List<FloatArray>, modifier: Modifier = Modifier) {
    var yaw by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    Canvas(modifier.fillMaxSize().pointerInput(Unit) {
        detectTransformGestures { _, panChange, zoomChange, rotationChange ->
            pan += panChange
            zoom = (zoom * zoomChange).coerceIn(0.2f, 8f)
            yaw += rotationChange
        }
    }) {
        if (points.isEmpty()) return@Canvas
        val maxAbs = points.maxOf { maxOf(kotlin.math.abs(it[0]), kotlin.math.abs(it[1]), kotlin.math.abs(it[2])) }.coerceAtLeast(1e-6f)
        drawPoints3d(points, maxAbs, yaw, pitch, zoom, pan)
    }
}

private fun DrawScope.drawPoints3d(points: List<FloatArray>, maxAbs: Float, yawDeg: Float, pitchDeg: Float, zoom: Float, pan: Offset) {
    val yaw = Math.toRadians(yawDeg.toDouble())
    val pitch = Math.toRadians(pitchDeg.toDouble())
    val cy = cos(yaw); val sy = sin(yaw); val cp = cos(pitch); val sp = sin(pitch)
    val scale = size.minDimension * 0.38f * zoom / maxAbs
    points.forEach { p ->
        val x1 = p[0] * cy - p[2] * sy
        val z1 = p[0] * sy + p[2] * cy
        val y1 = p[1] * cp - z1 * sp
        val z2 = p[1] * sp + z1 * cp
        val perspective = (1f + z2 / (maxAbs * 3f)).coerceIn(0.35f, 2f)
        val x = size.width / 2f + pan.x + x1 * scale * perspective
        val y = size.height / 2f + pan.y - y1 * scale * perspective
        if (x in 0f..size.width && y in 0f..size.height) {
            drawCircle(color = Color.White, radius = 2.2f * perspective, center = Offset(x, y))
        }
    }
}
