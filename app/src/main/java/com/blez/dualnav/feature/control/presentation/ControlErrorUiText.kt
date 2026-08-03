package com.blez.dualnav.feature.control.presentation

import com.blez.dualnav.R
import com.blez.dualnav.core.presentation.util.UiText
import com.blez.dualnav.feature.control.domain.CoordinateValidationError
import com.blez.dualnav.feature.control.domain.MapsLinkError

fun MapsLinkError.toUiText(): UiText = when (this) {
    MapsLinkError.INVALID_LINK -> UiText.StringResource(R.string.error_invalid_maps_link)
    MapsLinkError.UNREACHABLE -> UiText.StringResource(R.string.error_maps_link_unreachable)
}

fun CoordinateValidationError.toUiText(): UiText = when (this) {
    CoordinateValidationError.NOT_A_NUMBER -> UiText.StringResource(R.string.error_invalid_number)
    CoordinateValidationError.INVALID_LATITUDE -> UiText.StringResource(R.string.error_invalid_latitude)
    CoordinateValidationError.INVALID_LONGITUDE -> UiText.StringResource(R.string.error_invalid_longitude)
}
