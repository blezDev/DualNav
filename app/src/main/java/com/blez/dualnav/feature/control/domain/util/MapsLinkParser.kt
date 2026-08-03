package com.blez.dualnav.feature.control.domain.util

import com.blez.dualnav.core.domain.model.Destination
import java.net.URI
import java.net.URLDecoder

/**
 * Pure link parsing — no network. Short links (maps.app.goo.gl etc.) must already be expanded
 * to their full form before calling [parse]; see [com.blez.dualnav.feature.control.domain.MapsLinkResolver].
 */
internal object MapsLinkParser {

    private val COORDINATE_PATTERNS = listOf(
        Regex("""@(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)"""),
        Regex("""[?&]q=(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)"""),
        Regex("""[?&]ll=(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)"""),
        Regex("""[?&]daddr=(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""")
    )

    private val PLACE_NAME_PATTERN = Regex("""/place/([^/@]+)/""")

    private val SHORT_LINK_HOSTS = setOf("maps.app.goo.gl", "goo.gl", "g.co")

    fun isShortLink(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        return SHORT_LINK_HOSTS.any { host.equals(it, ignoreCase = true) }
    }

    fun parse(url: String): Destination? {
        val match = COORDINATE_PATTERNS.firstNotNullOfOrNull { it.find(url) } ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        val address = PLACE_NAME_PATTERN.find(url)
            ?.groupValues
            ?.get(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.replace('+', ' ')
            .orEmpty()

        return Destination(latitude = latitude, longitude = longitude, address = address)
    }
}
