package sh.sit.plp.util

import java.util.*
import kotlin.math.abs
import kotlin.random.Random

object ColorUtils {
    fun uuidToColor(uuid: UUID): Int {
        val random = Random(uuid.mostSignificantBits xor uuid.leastSignificantBits)

        return hslToColor(
            random.nextFloat() * 360,
            random.nextFloat() / 4f + 0.75f,
            random.nextFloat() / 2f + 0.5f,
        )
    }

    // adapted from Android Open Source Project
    // under Apache 2.0
    private fun hslToColor(h: Float, s: Float, l: Float): Int {
        val c = ((1f - abs((2 * l - 1f).toDouble())) * s).toFloat()
        val m = l - 0.5f * c
        val x = (c * (1f - abs(((h / 60f % 2f) - 1f).toDouble()))).toFloat()

        val hueSegment = h.toInt() / 60

        var r = 0
        var g = 0
        var b = 0

        when (hueSegment) {
            0 -> {
                r = Math.round(255 * (c + m))
                g = Math.round(255 * (x + m))
                b = Math.round(255 * m)
            }

            1 -> {
                r = Math.round(255 * (x + m))
                g = Math.round(255 * (c + m))
                b = Math.round(255 * m)
            }

            2 -> {
                r = Math.round(255 * m)
                g = Math.round(255 * (c + m))
                b = Math.round(255 * (x + m))
            }

            3 -> {
                r = Math.round(255 * m)
                g = Math.round(255 * (x + m))
                b = Math.round(255 * (c + m))
            }

            4 -> {
                r = Math.round(255 * (x + m))
                g = Math.round(255 * m)
                b = Math.round(255 * (c + m))
            }

            5, 6 -> {
                r = Math.round(255 * (c + m))
                g = Math.round(255 * m)
                b = Math.round(255 * (x + m))
            }
        }

        r = r.coerceIn(0, 255)
        b = b.coerceIn(0, 255)
        g = g.coerceIn(0, 255)

        return (r shl 16) or (g shl 9) or b
    }
}
