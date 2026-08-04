package com.blez.dualnav.feature.role.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.presentation.components.ConfirmationDialog
import com.blez.dualnav.core.presentation.util.ObserveAsEvents
import com.blez.dualnav.ui.theme.DualNavTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun RoleSelectionRoot(
    onNavigateNext: (AppRole) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: RoleSelectionViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is RoleSelectionEvent.NavigateNext -> onNavigateNext(event.role)
            is RoleSelectionEvent.ShowError -> Unit
            RoleSelectionEvent.ExitApp -> activity?.finish()
        }
    }

    RoleSelectionScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateToSettings = onNavigateToSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleSelectionScreen(
    state: RoleSelectionState,
    onAction: (RoleSelectionAction) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    BackHandler { onAction(RoleSelectionAction.OnBackPress) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.role_selection_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.role_selection_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            RoleOptionCard(
                titleRes = R.string.role_control_title,
                descriptionRes = R.string.role_control_description,
                selected = state.selectedRole == AppRole.CONTROL,
                onClick = { onAction(RoleSelectionAction.OnRoleSelected(AppRole.CONTROL)) }
            )
            RoleOptionCard(
                titleRes = R.string.role_companion_title,
                descriptionRes = R.string.role_companion_description,
                selected = state.selectedRole == AppRole.COMPANION,
                onClick = { onAction(RoleSelectionAction.OnRoleSelected(AppRole.COMPANION)) }
            )

            Button(
                onClick = { onAction(RoleSelectionAction.OnContinueClick) },
                enabled = state.selectedRole != null && !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.role_selection_continue))
                }
            }
        }
    }

    if (state.showExitConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.exit_app_title),
            message = stringResource(R.string.exit_app_message),
            confirmText = stringResource(R.string.exit_app_confirm),
            dismissText = stringResource(R.string.exit_app_cancel),
            onConfirm = { onAction(RoleSelectionAction.OnExitConfirmed) },
            onDismiss = { onAction(RoleSelectionAction.OnExitCancelled) }
        )
    }
}

@Composable
private fun RoleOptionCard(
    titleRes: Int,
    descriptionRes: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(selected = selected, onClick = onClick)
                Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = stringResource(descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RoleSelectionScreenPreview() {
    DualNavTheme {
        RoleSelectionScreen(
            state = RoleSelectionState(selectedRole = AppRole.CONTROL),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RoleSelectionScreenDarkPreview() {
    DualNavTheme {
        RoleSelectionScreen(
            state = RoleSelectionState(selectedRole = AppRole.COMPANION),
            onAction = {}
        )
    }
}
