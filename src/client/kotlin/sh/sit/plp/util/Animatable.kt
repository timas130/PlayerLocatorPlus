package sh.sit.plp.util

import kotlin.math.exp

// adapted from The Android Open Source Project
// licensed under Apache-2.0
class Animatable(initialValue: Float) {
    var naturalFreq: Float = 120f

    var targetValue: Float = initialValue
    var currentValue: Float = initialValue
        private set

    private var lastDisplacement = 0f
    private var lastVelocity = 0f

    fun updateValues(timeElapsed: Float) {
        val adjustedDisplacement = lastDisplacement - targetValue
        val deltaT = timeElapsed / 1000.0 // unit: seconds

        val displacement: Double
        val currentVelocity: Double

        // Critically damped
        val coeffA = adjustedDisplacement
        val coeffB = lastVelocity + naturalFreq * adjustedDisplacement
        val nFdT = -naturalFreq * deltaT
        displacement = (coeffA + coeffB * deltaT) * exp(nFdT)
        currentVelocity =
            (((coeffA + coeffB * deltaT) * exp(nFdT) * (-naturalFreq)) + coeffB * exp(nFdT))

        val newValue = (displacement + targetValue).toFloat()
        val newVelocity = currentVelocity.toFloat()

        lastDisplacement = newValue
        lastVelocity = newVelocity

        currentValue = newValue
    }
}
