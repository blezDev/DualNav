package com.blez.dualnav.core.presentation.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds a Maps link shared into the app from outside (e.g. Google Maps' own "Share" action)
 * until whichever screen cares about it (Control home) picks it up. A plain in-memory holder
 * rather than a nav argument since the share can arrive before that screen even exists yet.
 */
object IncomingShareHolder {
    private val _sharedLink = MutableStateFlow<String?>(null)
    val sharedLink: StateFlow<String?> = _sharedLink.asStateFlow()

    private val URL_PATTERN = Regex("""https?://\S+""")

    /** [text] is whatever the share intent carried — usually just the link, sometimes a place
     * name plus the link — so this pulls out the URL rather than assuming the whole string is one. */
    fun publish(text: String) {
        val url = URL_PATTERN.find(text)?.value ?: text.trim()
        if (url.isNotBlank()) {
            _sharedLink.value = url
        }
    }

    fun consume() {
        _sharedLink.value = null
    }
}