package com.timkrest.framehud.internal

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.annotation.WorkerThread

internal class BatterySample(val powerSaveMode: Boolean, val levelPercent: Int?) {
    companion object {
        val UNKNOWN = BatterySample(powerSaveMode = false, levelPercent = null)
    }
}

@WorkerThread
internal class BatteryMonitor(clock: MetricsClock) {

    private var powerManager: PowerManager? = null
    private var batteryManager: BatteryManager? = null

    private val sampleLimit = RateLimit(clock, MIN_SAMPLE_INTERVAL_MS)

    var sample: BatterySample = BatterySample.UNKNOWN
        private set

    fun bind(context: Context) {
        val appContext = context.applicationContext
        powerManager = appContext.getSystemService(PowerManager::class.java)
        batteryManager = appContext.getSystemService(BatteryManager::class.java)
    }

    fun unbind() {
        powerManager = null
        batteryManager = null
        sampleLimit.clear()
        sample = BatterySample.UNKNOWN
    }

    fun sample() {
        if (!sampleLimit.tryTake()) return
        sample = BatterySample(
            powerSaveMode = powerManager?.isPowerSaveMode ?: false,
            levelPercent = batteryManager
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ?.takeIf { it in 0..100 },
        )
    }

    private companion object {
        const val MIN_SAMPLE_INTERVAL_MS = 1_000L
    }
}
