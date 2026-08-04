package com.blez.dualnav.feature.companion.data

import com.blez.dualnav.core.domain.model.Destination
import com.blez.dualnav.core.domain.model.TravelMode
import com.blez.dualnav.feature.companion.domain.NavigationSessionState

class InMemoryNavigationSessionState : NavigationSessionState {

    @Volatile
    private var destination: Destination? = null
    @Volatile
    private var travelMode: TravelMode = TravelMode.CAR
    private val stops = mutableListOf<Destination>()

    @Synchronized
    override fun setDestination(destination: Destination) {
        this.destination = destination
        stops.clear()
    }

    override fun getDestination(): Destination? = destination

    override fun setTravelMode(travelMode: TravelMode) {
        this.travelMode = travelMode
    }

    override fun getTravelMode(): TravelMode = travelMode

    @Synchronized
    override fun addStop(stop: Destination) {
        stops.add(stop)
    }

    @Synchronized
    override fun getStops(): List<Destination> = stops.toList()

    @Synchronized
    override fun clear() {
        destination = null
        travelMode = TravelMode.CAR
        stops.clear()
    }
}
