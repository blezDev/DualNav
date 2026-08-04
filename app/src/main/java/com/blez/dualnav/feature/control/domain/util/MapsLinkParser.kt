package com.blez.dualnav.feature.control.domain.util

import com.blez.dualnav.core.domain.model.Destination
import java.net.URI
import java.net.URLDecoder

/**
 * Pure link parsing — no network. Short links (maps.app.goo.gl etc.) must already be expanded
 * to their full form before calling [parse]; see [com.blez.dualnav.feature.control.domain.MapsLinkResolver].
 */
internal object MapsLinkParser {

    // Ordered most-precise-first. The `@lat,lng` viewport pattern is checked last on purpose:
    // it's the map *camera* position, not the destination — for a place link it's often the
    // surrounding city/area (zoomed out), and for a directions link it's the midpoint of the
    // route, not either endpoint. The `!3d`/`!4d` and `!1d`/`!2d` markers in the URL's data blob
    // are the actual pin/destination coordinates Google Maps uses internally.
    private val COORDINATE_EXTRACTORS: List<(String) -> Pair<Double, Double>?> = listOf(
        regexExtractor(
            """!3d(-?\d{1,3}\.\d+)!4d(-?\d{1,3}\.\d+)""",
            latFirst = true
        ), // place pin: lat,lng
        regexExtractor(
            """!1d(-?\d{1,3}\.\d+)!2d(-?\d{1,3}\.\d+)""",
            latFirst = false
        ), // directions destination: lng,lat
        regexExtractor("""[?&]destination=(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""", latFirst = true),
        regexExtractor("""[?&]daddr=(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""", latFirst = true),
        regexExtractor("""[?&]q=(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""", latFirst = true),
        regexExtractor("""[?&]ll=(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""", latFirst = true),
        regexExtractor("""@(-?\d{1,3}\.\d+),(-?\d{1,3}\.\d+)""", latFirst = true)
    )

    private val PLACE_NAME_PATTERN = Regex("""/place/([^/@]+)/""")
    private val DIR_DESTINATION_NAME_PATTERN = Regex("""/dir/[^/]+/([^/@]+)/""")

    private val SHORT_LINK_HOSTS = setOf("maps.app.goo.gl", "goo.gl", "g.co")

    fun isShortLink(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        return SHORT_LINK_HOSTS.any { host.equals(it, ignoreCase = true) }
    }

    fun parse(url: String): Destination? {
        val (latitude, longitude) = COORDINATE_EXTRACTORS.firstNotNullOfOrNull { it(url) }
            ?: return null

        val address = (PLACE_NAME_PATTERN.find(url) ?: DIR_DESTINATION_NAME_PATTERN.find(url))
            ?.groupValues
            ?.get(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.replace('+', ' ')
            .orEmpty()

        return Destination(latitude = latitude, longitude = longitude, address = address)
    }

    private fun regexExtractor(
        pattern: String,
        latFirst: Boolean
    ): (String) -> Pair<Double, Double>? {
        val regex = Regex(pattern)
        return { url ->
            regex.find(url)?.let { match ->
                val first = match.groupValues[1].toDoubleOrNull()
                val second = match.groupValues[2].toDoubleOrNull()
                if (first == null || second == null) {
                    null
                } else {
                    val (lat, lng) = if (latFirst) first to second else second to first
                    if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
                }
            }
        }
    }
}
