package com.blez.dualnav.feature.control.data

import com.blez.dualnav.core.domain.util.Logger
import com.blez.dualnav.feature.control.domain.MapsLinkResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class HttpMapsLinkResolver(private val logger: Logger) : MapsLinkResolver {

    override suspend fun resolveShortLink(url: String): String? = withContext(Dispatchers.IO) {
        var current = url
        repeat(MAX_REDIRECTS) {
            when (val outcome = followOneRedirect(current)) {
                is RedirectOutcome.Redirected -> current = outcome.location
                RedirectOutcome.Terminal -> return@withContext current
                RedirectOutcome.Failed -> return@withContext null
            }
        }
        current
    }

    private fun followOneRedirect(url: String): RedirectOutcome {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "HEAD"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
        } catch (e: Exception) {
            logger.warn("Could not open connection to $url", e)
            return RedirectOutcome.Failed
        }

        return try {
            connection.connect()
            val code = connection.responseCode
            val location = if (code in 300..399) connection.getHeaderField("Location") else null
            if (location != null) RedirectOutcome.Redirected(location) else RedirectOutcome.Terminal
        } catch (e: Exception) {
            logger.warn("Redirect resolution failed for $url", e)
            RedirectOutcome.Failed
        } finally {
            connection.disconnect()
        }
    }

    /** Distinguishes "no more redirects, this is the final URL" from an actual connection failure —
     * both used to collapse to null, which made a real network error look like a resolved (but
     * unparsable) short link instead of the "couldn't reach it" error it should have been. */
    private sealed interface RedirectOutcome {
        data class Redirected(val location: String) : RedirectOutcome
        data object Terminal : RedirectOutcome
        data object Failed : RedirectOutcome
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val TIMEOUT_MS = 5000
    }
}
