package com.blez.dualnav.feature.control.domain

interface MapsLinkResolver {
    /** Follows redirects on a shortened Maps link and returns the final expanded URL, or null if unreachable. */
    suspend fun resolveShortLink(url: String): String?
}
