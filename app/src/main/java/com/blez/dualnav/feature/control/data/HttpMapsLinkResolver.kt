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
            val location = followOneRedirect(current) ?: return@withContext current
            current = location
        }
        current
    }

    private fun followOneRedirect(url: String): String? {
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "HEAD"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
        } catch (e: Exception) {
            logger.warn("Could not open connection to $url", e)
            return null
        }

        return try {
            connection.connect()
            val code = connection.responseCode
            if (code in 300..399) connection.getHeaderField("Location") else null
        } catch (e: Exception) {
            logger.warn("Redirect resolution failed for $url", e)
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MAX_REDIRECTS = 5
        const val TIMEOUT_MS = 5000
    }
}
