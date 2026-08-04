package com.blez.dualnav.feature.companion.domain

import com.blez.dualnav.core.domain.model.Destination
import com.blez.dualnav.core.domain.model.TravelMode

interface MapsIntentHandler {
    /** Launches turn-by-turn navigation to [destination] in an external maps app. */
    fun openNavigation(destination: Destination, travelMode: TravelMode)

    /** Launches a multi-stop route: visits [stops] in order, then [destination]. */
    fun openNavigationWithStops(
        destination: Destination,
        stops: List<Destination>,
        travelMode: TravelMode
    )

    /**
     * Exits Google Maps' active turn-by-turn view via the accessibility service, if it's enabled
     * and connected. Returns false (no-op) when the service isn't running — there is no public
     * Android API for a third-party app to stop another app's navigation any other way.
     */
    fun stopNavigation(): Boolean
}
