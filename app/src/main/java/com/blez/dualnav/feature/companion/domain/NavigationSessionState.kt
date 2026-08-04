package com.blez.dualnav.feature.companion.domain

import com.blez.dualnav.core.domain.model.Destination
import com.blez.dualnav.core.domain.model.TravelMode

/** In-memory record of the current navigation session on the Companion side — the active
 * destination, its travel mode, and any stops added along the way — so Resume/AddStop can
 * relaunch the full route. */
interface NavigationSessionState {
    fun setDestination(destination: Destination)
    fun getDestination(): Destination?
    fun setTravelMode(travelMode: TravelMode)
    fun getTravelMode(): TravelMode
    fun addStop(stop: Destination)
    fun getStops(): List<Destination>
    fun clear()
}
