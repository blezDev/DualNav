package com.blez.dualnav.feature.companion.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blez.dualnav.core.domain.repository.ConnectionRepository
import com.blez.dualnav.core.domain.repository.MessageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CompanionHomeViewModel(
    private val connectionRepository: ConnectionRepository,
    private val messageRepository: MessageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CompanionHomeState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            connectionRepository.getConnectionStatus().collect { status ->
                _state.update { it.copy(connectionStatus = status) }
            }
        }
        viewModelScope.launch {
            messageRepository.receiveCommand().collect { command ->
                _state.update {
                    it.copy(recentActivity = (listOf(command.toActivityUiText()) + it.recentActivity).take(MAX_ACTIVITY_ENTRIES))
                }
            }
        }
    }

    private companion object {
        const val MAX_ACTIVITY_ENTRIES = 20
    }
}
