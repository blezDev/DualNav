package com.blez.dualnav.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Destination(
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
