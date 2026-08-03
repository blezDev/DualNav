package com.blez.dualnav.feature.companion.presentation

import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.Destination
import com.blez.dualnav.core.domain.model.NavigationCommand
import com.blez.dualnav.core.presentation.util.UiText

fun NavigationCommand.toActivityUiText(): UiText = when (this) {
    is NavigationCommand.Navigate -> UiText.StringResource(R.string.companion_command_navigate, arrayOf(destination.label()))
    is NavigationCommand.AddStop -> UiText.StringResource(R.string.companion_command_add_stop, arrayOf(destination.label()))
    NavigationCommand.Stop -> UiText.StringResource(R.string.companion_command_stop)
    NavigationCommand.Resume -> UiText.StringResource(R.string.companion_command_resume)
    is NavigationCommand.StatusCheck -> UiText.StringResource(R.string.companion_command_status_check)
}

private fun Destination.label(): String = address.ifBlank { "$latitude, $longitude" }
