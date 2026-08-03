package com.blez.dualnav.feature.control.domain.util

internal object CoordinateValidator {
    fun isValidLatitude(latitude: Double): Boolean = latitude in -90.0..90.0
    fun isValidLongitude(longitude: Double): Boolean = longitude in -180.0..180.0
}
