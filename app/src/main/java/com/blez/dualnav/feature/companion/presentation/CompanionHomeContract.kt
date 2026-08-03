package com.blez.dualnav.feature.companion.presentation

import androidx.compose.runtime.Stable
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.presentation.util.UiText

@Stable
data class CompanionHomeState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.Disconnected,
    val recentActivity: List<UiText> = emptyList()
)
