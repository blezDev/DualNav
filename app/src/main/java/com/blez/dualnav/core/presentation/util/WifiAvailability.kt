package com.blez.dualnav.core.presentation.util

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.provider.Settings

object WifiAvailability {
    fun isEnabled(context: Context): Boolean {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true
    }

    fun createEnableIntent(): Intent = Intent(Settings.ACTION_WIFI_SETTINGS)
}
