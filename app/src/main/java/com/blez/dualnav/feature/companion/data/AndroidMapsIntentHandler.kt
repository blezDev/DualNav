package com.blez.dualnav.feature.companion.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.blez.dualnav.core.domain.model.Destination
import com.blez.dualnav.core.domain.model.TravelMode
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.feature.companion.domain.MapsIntentHandler

class AndroidMapsIntentHandler(
    private val context: Context,
    private val logger: Logger
) : MapsIntentHandler {

    override fun openNavigation(destination: Destination, travelMode: TravelMode) {
        val navigationIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(
                "google.navigation:q=${destination.latitude},${destination.longitude}" +
                        "&mode=${travelMode.toNavigationModeChar()}"
            )
        ).apply {
            setPackage(GOOGLE_MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(navigationIntent)
        } catch (e: ActivityNotFoundException) {
            logger.warn("Google Maps not available, falling back to a generic geo: intent", e)
            openWithAnyMapsApp(destination)
        }
    }

    override fun openNavigationWithStops(
        destination: Destination,
        stops: List<Destination>,
        travelMode: TravelMode
    ) {
        if (stops.isEmpty()) {
            openNavigation(destination, travelMode)
            return
        }

        // The documented "dir/?api=1&...&travelmode=" scheme only supports driving/walking/
        // bicycling/transit — no two-wheeler. Maps' own path-segment URL format does support it,
        // via the (undocumented but verified-working) "data=!3eN" travel-mode suffix: 0=driving,
        // 9=two-wheeler. A blank leading segment ("dir//...") is the same "use current location"
        // convention as google.navigation:q= — Maps fills in the live GPS position at launch.
        val points = (stops + destination).joinToString("/") { "${it.latitude},${it.longitude}" }
        val uri =
            Uri.parse("https://www.google.com/maps/dir//$points/data=!3e${travelMode.toDirectionsDataCode()}")
        val directionsIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(GOOGLE_MAPS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(directionsIntent)
        } catch (e: ActivityNotFoundException) {
            logger.warn(
                "Google Maps not available, falling back to a browser for the multi-stop route",
                e
            )
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        uri
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: ActivityNotFoundException) {
                logger.error("No app on this device can open the multi-stop directions link", e2)
            }
        }
    }

    override fun stopNavigation(): Boolean = MapsAccessibilityBridge.exitNavigation()

    private fun openWithAnyMapsApp(destination: Destination) {
        val label = destination.address.ifBlank { "${destination.latitude},${destination.longitude}" }
        val geoIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:${destination.latitude},${destination.longitude}?q=${destination.latitude},${destination.longitude}($label)")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(geoIntent)
        } catch (e: ActivityNotFoundException) {
            logger.error("No app on this device can handle a geo: intent", e)
        }
    }

    private fun TravelMode.toNavigationModeChar(): String = when (this) {
        TravelMode.CAR -> "d"
        // Google's two-wheeler/motorcycle navigation mode — distinct from "b" (bicycling).
        TravelMode.TWO_WHEELER -> "l"
    }

    private fun TravelMode.toDirectionsDataCode(): Int = when (this) {
        TravelMode.CAR -> 0
        TravelMode.TWO_WHEELER -> 9
    }

    private companion object {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}
