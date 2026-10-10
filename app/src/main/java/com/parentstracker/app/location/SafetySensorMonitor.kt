package com.parentstracker.app.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.parentstracker.app.util.FallDetector
import com.parentstracker.app.util.ShakeDetector
import kotlin.math.sqrt

/**
 * Listens to the accelerometer for shake-to-SOS and fall detection while either is switched on.
 * Runs while the app is alive (open, or sharing location in the background).
 */
class SafetySensorMonitor(
    context: Context,
    private val onShake: () -> Unit,
    private val onFall: () -> Unit,
) : SensorEventListener {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val shake = ShakeDetector()
    private val fall = FallDetector()
    private var shakeOn = false
    private var fallOn = false
    private var listening = false

    val available: Boolean get() = accelerometer != null

    fun update(shakeEnabled: Boolean, fallEnabled: Boolean) {
        shakeOn = shakeEnabled
        fallOn = fallEnabled
        val want = (shakeEnabled || fallEnabled) && accelerometer != null
        if (want && !listening) {
            listening = sensors?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME) == true
        } else if (!want && listening) {
            sensors?.unregisterListener(this)
            listening = false
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val now = SystemClock.elapsedRealtime()
        if (shakeOn && shake.onSample(g, now)) onShake()
        if (fallOn && fall.onSample(g, now)) onFall()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
