package com.blez.dualnav.feature.companion.presentation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object MapsControlAccessibilityPermission {

    fun isEnabled(context: Context): Boolean {
        val expected =
            ComponentName(context, MapsControlAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
            .orEmpty()
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun createRequestIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
}
