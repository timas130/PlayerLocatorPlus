package sh.sit.plp.util

import kotlin.math.atan
import kotlin.math.tan

object MathUtils {
    fun calculateHorizontalFov(verticalFov: Int, width: Int, height: Int): Double {
        // ffs
        val fovRad = verticalFov / 2.0 * Math.PI / 180.0
        val d = height / 2.0 / tan(fovRad)
        val t = atan(width / 2.0 / d) * 2.0
        return t / Math.PI * 180.0
    }
}
