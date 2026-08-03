package com.blez.dualnav.feature.control.domain

import com.blez.dualnav.core.domain.model.Destination
import com.blez.dualnav.core.domain.util.Error
import com.blez.dualnav.core.domain.util.Result
import com.blez.dualnav.feature.control.domain.util.MapsLinkParser

enum class MapsLinkError : Error {
    INVALID_LINK,
    UNREACHABLE
}

class ParseMapsLinkUseCase(
    private val mapsLinkResolver: MapsLinkResolver
) {
    suspend operator fun invoke(mapsLink: String): Result<Destination, MapsLinkError> {
        val trimmed = mapsLink.trim()
        if (trimmed.isEmpty()) return Result.Error(MapsLinkError.INVALID_LINK)

        val resolved = if (MapsLinkParser.isShortLink(trimmed)) {
            mapsLinkResolver.resolveShortLink(trimmed) ?: return Result.Error(MapsLinkError.UNREACHABLE)
        } else {
            trimmed
        }

        val destination = MapsLinkParser.parse(resolved) ?: return Result.Error(MapsLinkError.INVALID_LINK)
        return Result.Success(destination)
    }
}
