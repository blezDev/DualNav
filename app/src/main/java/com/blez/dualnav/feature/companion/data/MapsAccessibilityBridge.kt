package com.blez.dualnav.feature.companion.data

/**
 * Lets the data layer trigger the accessibility service without depending on it directly (that
 * class is a presentation-layer Android component, bound/unbound by the OS on its own schedule).
 * The service registers itself here while connected and unregisters on unbind/destroy.
 */
object MapsAccessibilityBridge {

    interface Actions {
        fun exitNavigation(): Boolean
    }

    @Volatile
    private var actions: Actions? = null

    fun register(actions: Actions) {
        this.actions = actions
    }

    fun unregister(actions: Actions) {
        if (this.actions === actions) {
            this.actions = null
        }
    }

    fun exitNavigation(): Boolean = actions?.exitNavigation() ?: false

    fun isConnected(): Boolean = actions != null
}
