package com.blez.dualnav.feature.companion.presentation

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.blez.dualnav.feature.companion.data.MapsAccessibilityBridge

/**
 * Exits Google Maps' turn-by-turn view via a simulated back-press when asked to stop navigation.
 * There's no public Android API for a third-party app to pause another app's live navigation —
 * a back-press is used instead of hunting for Maps' own exit button, since it doesn't depend on
 * Maps' current button layout/resource-ids, which change across app versions.
 */
class MapsControlAccessibilityService : AccessibilityService(), MapsAccessibilityBridge.Actions {

    override fun onServiceConnected() {
        super.onServiceConnected()
        MapsAccessibilityBridge.register(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        MapsAccessibilityBridge.unregister(this)
        return super.onUnbind(intent)
    }

    override fun exitNavigation(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
}
