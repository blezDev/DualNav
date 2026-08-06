package com.blez.dualnav.feature.control.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TwoWheeler
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.TravelMode
import com.blez.dualnav.core.presentation.components.ConfirmationDialog
import com.blez.dualnav.core.presentation.util.ObserveAsEvents
import com.blez.dualnav.core.presentation.util.asString
import com.blez.dualnav.ui.theme.DualNavTheme
import com.blez.dualnav.ui.theme.LocalIsBleachTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun ControlHomeRoot(
    onNavigateToSettings: () -> Unit,
    onNavigateToRoleSelection: () -> Unit,
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
            ControlHomeEvent.NavigateToRoleSelection -> onNavigateToRoleSelection()
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
    BackHandler { onAction(ControlHomeAction.OnBackPress) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun dismissKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    fun openGoogleMaps() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")).apply {
            setPackage(GOOGLE_MAPS_PACKAGE)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")))
            } catch (e2: ActivityNotFoundException) {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$GOOGLE_MAPS_PACKAGE")
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.control_home_title)) },
                actions = {
                    IconButton(onClick = { openGoogleMaps() }) {
                        Icon(
                            Icons.Filled.Map,
                            contentDescription = stringResource(R.string.cd_open_google_maps)
                        )
                    }
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionStatusRow(state.connectionStatus)

            if (state.connectionStatus !is ConnectionStatus.Connected) {
                OutlinedButton(
                    onClick = { onAction(ControlHomeAction.OnReconnectClick) },
                    enabled = !state.isReconnecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.control_home_reconnect))
                }
            }

            OutlinedTextField(
                value = state.mapsLink,
                onValueChange = { onAction(ControlHomeAction.OnMapsLinkChange(it)) },
                label = { Text(stringResource(R.string.control_home_maps_link_label)) },
                placeholder = { Text(stringResource(R.string.control_home_maps_link_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            TravelModeRow(
                selected = state.travelMode,
                onSelected = { onAction(ControlHomeAction.OnTravelModeSelected(it)) }
            )

            Button(
                onClick = {
                    dismissKeyboard()
                    onAction(ControlHomeAction.OnSendMapsLinkClick)
                },
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
                    onClick = {
                        dismissKeyboard()
                        onAction(ControlHomeAction.OnAddStopClick)
                    },
                    enabled = !state.isSendingLink,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.control_home_add_stop))
                }
            }
        }
    }

    if (state.showManualDialog) {
        ManualCoordinateDialog(state = state, onAction = onAction)
    }

    if (state.showDisconnectConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.disconnect_confirm_title),
            message = stringResource(R.string.disconnect_confirm_message),
            confirmText = stringResource(R.string.disconnect_confirm_yes),
            dismissText = stringResource(R.string.disconnect_confirm_no),
            onConfirm = { onAction(ControlHomeAction.OnDisconnectConfirmed) },
            onDismiss = { onAction(ControlHomeAction.OnDisconnectCancelled) }
        )
    }

    if (state.showRemoteDisconnectedDialog) {
        AlertDialog(
            onDismissRequest = { onAction(ControlHomeAction.OnRemoteDisconnectedAcknowledged) },
            title = { Text(stringResource(R.string.remote_disconnected_companion_title)) },
            text = { Text(stringResource(R.string.remote_disconnected_companion_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(ControlHomeAction.OnRemoteDisconnectedAcknowledged) }) {
                    Text(stringResource(R.string.remote_disconnected_ok))
                }
            }
        )
    }
}

private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

private data class TravelModeOption(
    val mode: TravelMode,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelModeRow(selected: TravelMode, onSelected: (TravelMode) -> Unit) {
    val options = listOf(
        TravelModeOption(
            TravelMode.CAR,
            R.string.control_home_travel_mode_car,
            Icons.Filled.DirectionsCar
        ),
        TravelModeOption(
            TravelMode.TWO_WHEELER,
            R.string.control_home_travel_mode_two_wheeler,
            Icons.Filled.TwoWheeler
        )
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option.mode,
                onClick = { onSelected(option.mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            ) {
                Text(stringResource(option.labelRes))
            }
        }
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
private fun ManualCoordinateDialog(
    state: ControlHomeState,
    onAction: (ControlHomeAction) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onAction(ControlHomeAction.OnDismissManualDialog) },
        title = {
            Text(
                stringResource(
                    if (state.manualDialogMode == ManualDialogMode.ADD_STOP) {
                        R.string.manual_coordinate_dialog_title_add_stop
                    } else {
                        R.string.manual_coordinate_dialog_title
                    }
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.manualMapsLink,
                    onValueChange = { onAction(ControlHomeAction.OnManualMapsLinkChange(it)) },
                    label = { Text(stringResource(R.string.manual_coordinate_maps_link_label)) },
                    placeholder = { Text(stringResource(R.string.control_home_maps_link_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
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
            TextButton(
                onClick = { onAction(ControlHomeAction.OnManualConfirmClick) },
                enabled = !state.isSendingLink
            ) {
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
