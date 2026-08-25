package com.realitycompiler

import kotlin.math.sqrt

object PoseMath {
    const val MAX_SYNC_DELTA_NS = 20_000_000L

    fun isSynchronized(deltaNs: Long): Boolean = deltaNs in 0..MAX_SYNC_DELTA_NS

    fun quaternionNorm(x: Float, y: Float, z: Float, w: Float): Float = sqrt(x * x + y * y + z * z + w * w)

    fun translationDistance(a: FloatArray, b: FloatArray): Float {
        require(a.size >= 3 && b.size >= 3)
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
