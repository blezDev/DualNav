package com.blez.dualnav.feature.control.presentation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.presentation.util.ObserveAsEvents
import com.blez.dualnav.core.presentation.util.asString
import com.blez.dualnav.feature.overlay.presentation.FloatingOverlayService
import com.blez.dualnav.feature.overlay.presentation.OverlayPermission
import com.blez.dualnav.ui.theme.DualNavTheme
import com.blez.dualnav.ui.theme.LocalIsBleachTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ControlHomeRoot(
    onNavigateToSettings: () -> Unit,
    viewModel: ControlHomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ControlHomeEvent.ShowSnackbar -> {
                val message = event.message.asString(context)
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    ControlHomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onNavigateToSettings = onNavigateToSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlHomeScreen(
    state: ControlHomeState,
    snackbarHostState: SnackbarHostState,
    onAction: (ControlHomeAction) -> Unit,
    onNavigateToSettings: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.control_home_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionStatusRow(state.connectionStatus)

            OutlinedTextField(
                value = state.mapsLink,
                onValueChange = { onAction(ControlHomeAction.OnMapsLinkChange(it)) },
                label = { Text(stringResource(R.string.control_home_maps_link_label)) },
                placeholder = { Text(stringResource(R.string.control_home_maps_link_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Button(
                onClick = { onAction(ControlHomeAction.OnSendMapsLinkClick) },
                enabled = state.mapsLink.isNotBlank() && !state.isSendingLink,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (LocalIsBleachTheme.current) {
                    Icon(
                        painter = painterResource(R.drawable.ic_katana),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.control_home_send_destination))
            }

            OutlinedButton(
                onClick = { onAction(ControlHomeAction.OnOpenManualEntryClick) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.control_home_manual_entry))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { onAction(ControlHomeAction.OnStopClick) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.control_home_stop))
                }
                OutlinedButton(
                    onClick = { onAction(ControlHomeAction.OnResumeClick) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.control_home_resume))
                }
                OutlinedButton(
                    onClick = { onAction(ControlHomeAction.OnAddStopClick) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.control_home_add_stop))
                }
            }

            FloatingWidgetToggleButton(modifier = Modifier.fillMaxWidth())
        }
    }

    if (state.showManualDialog) {
        ManualCoordinateDialog(state = state, onAction = onAction)
    }
}

@Composable
private fun ConnectionStatusRow(status: ConnectionStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status is ConnectionStatus.Connected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (LocalIsBleachTheme.current) {
                Icon(
                    painter = painterResource(
                        if (status is ConnectionStatus.Connected) R.drawable.ic_soul_flame else R.drawable.ic_hollow_skull
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (status is ConnectionStatus.Connected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.control_home_status_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = when (status) {
                        is ConnectionStatus.Connected -> stringResource(R.string.connection_setup_status_connected)
                        is ConnectionStatus.Reconnecting -> stringResource(R.string.connection_setup_status_reconnecting)
                        is ConnectionStatus.Error -> status.message
                        is ConnectionStatus.Disconnected -> stringResource(R.string.connection_setup_status_disconnected)
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
private fun FloatingWidgetToggleButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isOverlayActive by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (OverlayPermission.isGranted(context)) {
            context.startForegroundService(Intent(context, FloatingOverlayService::class.java))
            isOverlayActive = true
        }
    }

    OutlinedButton(
        onClick = {
            when {
                isOverlayActive -> {
                    context.stopService(Intent(context, FloatingOverlayService::class.java))
                    isOverlayActive = false
                }
                OverlayPermission.isGranted(context) -> {
                    context.startForegroundService(Intent(context, FloatingOverlayService::class.java))
                    isOverlayActive = true
                }
                else -> permissionLauncher.launch(OverlayPermission.createRequestIntent(context))
            }
        },
        modifier = modifier
    ) {
        Text(
            stringResource(
                if (isOverlayActive) R.string.control_home_disable_floating_widget
                else R.string.control_home_enable_floating_widget
            )
        )
    }
}

@Composable
private fun ManualCoordinateDialog(
    state: ControlHomeState,
    onAction: (ControlHomeAction) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onAction(ControlHomeAction.OnDismissManualDialog) },
        title = { Text(stringResource(R.string.manual_coordinate_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualLatitude,
                    onValueChange = { onAction(ControlHomeAction.OnManualLatitudeChange(it)) },
                    label = { Text(stringResource(R.string.manual_coordinate_latitude_label)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.manualLongitude,
                    onValueChange = { onAction(ControlHomeAction.OnManualLongitudeChange(it)) },
                    label = { Text(stringResource(R.string.manual_coordinate_longitude_label)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.manualAddress,
                    onValueChange = { onAction(ControlHomeAction.OnManualAddressChange(it)) },
                    label = { Text(stringResource(R.string.manual_coordinate_address_label)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(ControlHomeAction.OnManualConfirmClick) }) {
                Text(stringResource(R.string.manual_coordinate_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ControlHomeAction.OnDismissManualDialog) }) {
                Text(stringResource(R.string.manual_coordinate_cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun ControlHomeScreenPreview() {
    DualNavTheme {
        ControlHomeScreen(
            state = ControlHomeState(
                mapsLink = "https://maps.app.goo.gl/example",
                connectionStatus = ConnectionStatus.Connected
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ControlHomeScreenDarkPreview() {
    DualNavTheme {
        ControlHomeScreen(
            state = ControlHomeState(
                connectionStatus = ConnectionStatus.Disconnected
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ControlHomeScreenBleachPreview() {
    DualNavTheme(themeMode = com.blez.dualnav.core.domain.model.AppThemeMode.BLEACH) {
        ControlHomeScreen(
            state = ControlHomeState(
                mapsLink = "https://maps.app.goo.gl/example",
                connectionStatus = ConnectionStatus.Connected
            ),
            snackbarHostState = SnackbarHostState(),
            onAction = {}
        )
    }
}
