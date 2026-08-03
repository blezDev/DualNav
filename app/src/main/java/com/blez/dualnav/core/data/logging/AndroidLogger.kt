package com.blez.dualnav.core.data.logging

import android.util.Log
import com.blez.dualnav.BuildConfig
import com.blez.dualnav.core.domain.util.Logger

class AndroidLogger : Logger {
    override fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    override fun info(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    override fun warn(message: String, throwable: Throwable?) {
        Log.w(TAG, message, throwable)
    }

    override fun error(message: String, throwable: Throwable?) {
        Log.e(TAG, message, throwable)
    }

    private companion object {
        const val TAG = "DualNav"
    }
}
