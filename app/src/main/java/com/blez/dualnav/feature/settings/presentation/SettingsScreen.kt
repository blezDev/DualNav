package com.blez.dualnav.feature.settings.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.AppThemeMode
import com.blez.dualnav.core.domain.model.AppUpdate
import com.blez.dualnav.core.presentation.components.ConfirmationDialog
import com.blez.dualnav.core.presentation.util.ObserveAsEvents
import com.blez.dualnav.core.presentation.util.asString
import com.blez.dualnav.ui.theme.DualNavTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToRoleSelection: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            SettingsEvent.NavigateToRoleSelection -> onNavigateToRoleSelection()
            is SettingsEvent.ShowSnackbar -> {
                val message = event.message.asString(context)
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    SettingsScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_theme_label),
                style = MaterialTheme.typography.titleMedium
            )

            ThemeOptionCard(
                titleRes = R.string.settings_theme_default,
                descriptionRes = R.string.settings_theme_default_description,
                selected = state.themeMode == AppThemeMode.DEFAULT,
                onClick = { onAction(SettingsAction.OnThemeSelected(AppThemeMode.DEFAULT)) }
            )
            ThemeOptionCard(
                titleRes = R.string.settings_theme_bleach,
                descriptionRes = R.string.settings_theme_bleach_description,
                selected = state.themeMode == AppThemeMode.BLEACH,
                onClick = { onAction(SettingsAction.OnThemeSelected(AppThemeMode.BLEACH)) }
            )

            if (state.isConnected) {
                Text(
                    text = stringResource(R.string.settings_connection_label),
                    style = MaterialTheme.typography.titleMedium
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = false,
                            onClick = { onAction(SettingsAction.OnDisconnectClick) })
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_disconnect),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.settings_disconnect_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.settings_updates_label),
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedButton(
                onClick = { onAction(SettingsAction.OnCheckForUpdateClick) },
                enabled = !state.isCheckingForUpdate && !state.isDownloadingUpdate,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isCheckingForUpdate) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.settings_check_for_update))
                }
            }
        }
    }

    if (state.showDisconnectConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.disconnect_confirm_title),
            message = stringResource(R.string.disconnect_confirm_message),
            confirmText = stringResource(R.string.disconnect_confirm_yes),
            dismissText = stringResource(R.string.disconnect_confirm_no),
            onConfirm = { onAction(SettingsAction.OnDisconnectConfirmed) },
            onDismiss = { onAction(SettingsAction.OnDisconnectCancelled) }
        )
    }

    state.availableUpdate?.let { update ->
        UpdateAvailableDialog(
            update = update,
            onDownload = { onAction(SettingsAction.OnDownloadUpdateClick) },
            onDismiss = { onAction(SettingsAction.OnDismissUpdateDialog) }
        )
    }

    if (state.isDownloadingUpdate) {
        DownloadingUpdateDialog()
    }
}

@Composable
private fun UpdateAvailableDialog(
    update: AppUpdate,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.settings_update_available_title,
                    update.versionName
                )
            )
        },
        text = {
            if (update.releaseNotes.isNotBlank()) {
                Text(update.releaseNotes, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(
                    stringResource(
                        if (update.downloadUrl != null) {
                            R.string.settings_update_download
                        } else {
                            R.string.settings_update_open_release_page
                        }
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_update_later))
            }
        }
    )
}

@Composable
private fun DownloadingUpdateDialog() {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(stringResource(R.string.settings_update_downloading_title)) },
        text = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.settings_update_downloading_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )
}

@Composable
private fun ThemeOptionCard(
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
private fun SettingsScreenPreview() {
    DualNavTheme {
        SettingsScreen(
            state = SettingsState(themeMode = AppThemeMode.DEFAULT),
            onAction = {},
            onNavigateBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenBleachPreview() {
    DualNavTheme(themeMode = AppThemeMode.BLEACH) {
        SettingsScreen(
            state = SettingsState(themeMode = AppThemeMode.BLEACH),
            onAction = {},
            onNavigateBack = {}
        )
    }
}
