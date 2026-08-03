package com.blez.dualnav.feature.companion.domain

import com.blez.dualnav.core.domain.model.Destination

interface MapsIntentHandler {
    /** Launches turn-by-turn navigation to [destination] in an external maps app. */
    fun openNavigation(destination: Destination)
}
