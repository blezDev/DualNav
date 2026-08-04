package com.blez.dualnav.core.presentation.util

import android.content.Context
import android.os.PowerManager

/**
 * Reference-counted partial wake lock that keeps the CPU running on the companion phone from
 * connection setup onwards, so incoming commands are processed promptly even with the screen off.
 * Each caller must pair every [acquire] with exactly one [release].
 */
object CompanionWakeLock {
    private const val TAG = "DualNav:CompanionWakeLock"

    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(context: Context) {
        val lock = wakeLock ?: createWakeLock(context).also { wakeLock = it }
        lock.acquire()
    }

    @Synchronized
    fun release() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun createWakeLock(context: Context): PowerManager.WakeLock {
        val powerManager =
            context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply {
            setReferenceCounted(true)
        }
    }
}
