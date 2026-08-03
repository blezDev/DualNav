package com.blez.dualnav.core.domain.model

data class Destination(
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
