package com.parentstracker.app.util

/**
 * Shake-to-SOS: three hard shakes (over [THRESHOLD_G]) within [WINDOW_MS].
 * Feed it accelerometer magnitudes in g (1.0 = resting).
 */
class ShakeDetector {
    private val peaks = ArrayDeque<Long>()
    private var lastPeakAt = Long.MIN_VALUE / 2

    fun onSample(gForce: Float, timeMs: Long): Boolean {
        if (gForce < THRESHOLD_G || timeMs - lastPeakAt < MIN_GAP_MS) return false
        lastPeakAt = timeMs
        peaks.addLast(timeMs)
        while (peaks.isNotEmpty() && timeMs - peaks.first() > WINDOW_MS) peaks.removeFirst()
        if (peaks.size >= SHAKES) {
            peaks.clear()
            return true
        }
        return false
    }

    companion object {
        const val THRESHOLD_G = 2.7f
        const val SHAKES = 3
        const val WINDOW_MS = 1_500L
        const val MIN_GAP_MS = 150L
    }
}

/**
 * Fall detection: a moment of free fall (under [FREE_FALL_G]) followed quickly by a hard impact
 * (over [IMPACT_G]), then the phone lying still for [STILL_MS]. Feed it accelerometer magnitudes in g.
 */
class FallDetector {
    private var freeFallAt = 0L
    private var impactAt = 0L
    private var lastMovementAt = 0L

    fun onSample(gForce: Float, timeMs: Long): Boolean {
        if (gForce < FREE_FALL_G) freeFallAt = timeMs
        if (gForce > IMPACT_G && freeFallAt > 0 && timeMs - freeFallAt < IMPACT_WITHIN_MS) {
            impactAt = timeMs
            lastMovementAt = timeMs
            return false
        }
        if (impactAt == 0L) return false
        if (kotlin.math.abs(gForce - 1f) > STILL_TOLERANCE_G) lastMovementAt = timeMs
        if (timeMs - impactAt > GIVE_UP_MS) {
            reset()
            return false
        }
        if (timeMs - lastMovementAt >= STILL_MS) {
            reset()
            return true
        }
        return false
    }

    private fun reset() {
        freeFallAt = 0L
        impactAt = 0L
        lastMovementAt = 0L
    }

    companion object {
        const val FREE_FALL_G = 0.4f
        const val IMPACT_G = 2.5f
        const val IMPACT_WITHIN_MS = 1_000L
        const val STILL_TOLERANCE_G = 0.3f
        const val STILL_MS = 4_000L
        const val GIVE_UP_MS = 15_000L
    }
}
