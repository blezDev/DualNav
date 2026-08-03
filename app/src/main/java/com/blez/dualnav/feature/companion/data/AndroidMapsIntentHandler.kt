package com.blez.dualnav.feature.companion.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.blez.dualnav.core.domain.model.Destination
import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.feature.companion.domain.MapsIntentHandler

class AndroidMapsIntentHandler(
    private val context: Context,
    private val logger: Logger
) : MapsIntentHandler {

    override fun openNavigation(destination: Destination) {
        val navigationIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=${destination.latitude},${destination.longitude}")
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

    private companion object {
        const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    }
}
