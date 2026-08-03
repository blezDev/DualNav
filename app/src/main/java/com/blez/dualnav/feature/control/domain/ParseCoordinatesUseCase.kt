package com.blez.dualnav.feature.control.domain

import com.blez.dualnav.core.domain.model.Destination
import com.blez.dualnav.core.domain.util.Error
import com.blez.dualnav.core.domain.util.Result
import com.blez.dualnav.feature.control.domain.util.CoordinateValidator

enum class CoordinateValidationError : Error {
    NOT_A_NUMBER,
    INVALID_LATITUDE,
    INVALID_LONGITUDE
}

class ParseCoordinatesUseCase {
    operator fun invoke(
        latitude: String,
        longitude: String,
        address: String = ""
    ): Result<Destination, CoordinateValidationError> {
        val lat = latitude.trim().toDoubleOrNull() ?: return Result.Error(CoordinateValidationError.NOT_A_NUMBER)
        val lng = longitude.trim().toDoubleOrNull() ?: return Result.Error(CoordinateValidationError.NOT_A_NUMBER)
        if (!CoordinateValidator.isValidLatitude(lat)) return Result.Error(CoordinateValidationError.INVALID_LATITUDE)
        if (!CoordinateValidator.isValidLongitude(lng)) return Result.Error(CoordinateValidationError.INVALID_LONGITUDE)
        return Result.Success(Destination(latitude = lat, longitude = lng, address = address))
    }
}
