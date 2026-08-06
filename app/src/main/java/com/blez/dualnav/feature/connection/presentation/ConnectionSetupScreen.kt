package com.blez.dualnav.feature.connection.presentation

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.domain.model.PairingState
import com.blez.dualnav.core.presentation.components.ConfirmationDialog
import com.blez.dualnav.core.presentation.util.BluetoothAvailability
import com.blez.dualnav.core.presentation.util.CompanionWakeLock
import com.blez.dualnav.core.presentation.util.ObserveAsEvents
import com.blez.dualnav.core.presentation.util.WifiAvailability
import com.blez.dualnav.core.presentation.util.asString
import com.blez.dualnav.ui.theme.DualNavTheme
import com.blez.dualnav.ui.theme.LocalIsBleachTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val BLUETOOTH_DISCOVERABLE_DURATION_SECONDS = 300

@Composable
fun ConnectionSetupRoot(
    onNavigateNext: (AppRole) -> Unit,
    onNavigateBackToRoleSelection: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ConnectionSetupViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Companion needs to stay responsive to incoming commands from here on; control never does.
    DisposableEffect(state.role) {
        val role = state.role
        if (role == AppRole.COMPANION) {
            CompanionWakeLock.acquire(context)
        }
        onDispose {
            if (role == AppRole.COMPANION) {
                CompanionWakeLock.release()
            }
        }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ConnectionSetupEvent.NavigateNext -> onNavigateNext(event.role)
            ConnectionSetupEvent.NavigateBackToRoleSelection -> onNavigateBackToRoleSelection()
            is ConnectionSetupEvent.ShowMessage -> {
                val message = event.message.asString(context)
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    ConnectionSetupScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onNavigateToSettings = onNavigateToSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSetupScreen(
    state: ConnectionSetupState,
    onAction: (ConnectionSetupAction) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onNavigateToSettings: () -> Unit = {}
) {
    BackHandler { onAction(ConnectionSetupAction.OnBackPress) }

    val context = LocalContext.current
    var showWifiDisabledDialog by remember { mutableStateOf(false) }
    var showBluetoothDisabledDialog by remember { mutableStateOf(false) }

    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Whether the user approved or dismissed the discoverable prompt, Companion can still
        // proceed - it'll just have to be paired via already-bonded devices if they said no.
        onAction(ConnectionSetupAction.OnConnectionTypeSelected(ConnectionType.BLUETOOTH))
    }

    fun requestBluetoothType() {
        if (state.role == AppRole.COMPANION) {
            // A running RFCOMM server socket alone doesn't make this phone visible to Control's
            // scan - classic Bluetooth discovery only finds devices that are either already
            // OS-bonded or explicitly put into discoverable mode, which requires this prompt.
            discoverableLauncher.launch(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(
                        BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                        BLUETOOTH_DISCOVERABLE_DURATION_SECONDS
                    )
            )
        } else {
            onAction(ConnectionSetupAction.OnConnectionTypeSelected(ConnectionType.BLUETOOTH))
        }
    }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (BluetoothAvailability.isEnabled(context)) {
            requestBluetoothType()
        }
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            if (BluetoothAvailability.isEnabled(context)) {
                requestBluetoothType()
            } else {
                showBluetoothDisabledDialog = true
            }
        }
    }

    fun onTypeSelected(type: ConnectionType) {
        when (type) {
            ConnectionType.WIFI -> {
                if (WifiAvailability.isEnabled(context)) {
                    onAction(ConnectionSetupAction.OnConnectionTypeSelected(type))
                } else {
                    showWifiDisabledDialog = true
                }
            }

            ConnectionType.BLUETOOTH -> {
                if (!BluetoothAvailability.hasPermissions(context)) {
                    bluetoothPermissionLauncher.launch(BluetoothAvailability.requiredPermissions())
                } else if (!BluetoothAvailability.isEnabled(context)) {
                    showBluetoothDisabledDialog = true
                } else {
                    requestBluetoothType()
                }
            }

            ConnectionType.FIREBASE -> onAction(ConnectionSetupAction.OnConnectionTypeSelected(type))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connection_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(ConnectionSetupAction.OnBackPress) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_change_role))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    ConnectionType.BLUETOOTH to R.string.connection_type_bluetooth,
                    ConnectionType.WIFI to R.string.connection_type_wifi,
                    ConnectionType.FIREBASE to R.string.connection_type_firebase
                )
                options.forEachIndexed { index, (type, labelRes) ->
                    SegmentedButton(
                        selected = state.selectedType == type,
                        onClick = { onTypeSelected(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }

            ConnectionStatusCard(status = state.status, isConnecting = state.isConnecting)

            if (state.role == AppRole.CONTROL) {
                // Only Control discovers and initiates pairing; Companion just waits to be paired with.
                if (state.selectedType != null && state.selectedType != ConnectionType.FIREBASE) {
                    OutlinedButton(
                        onClick = { onAction(ConnectionSetupAction.OnDiscoverClick) },
                        enabled = !state.isDiscovering,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                if (state.isDiscovering) R.string.connection_setup_discovering
                                else R.string.connection_setup_discover
                            )
                        )
                    }

                    if (state.devices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.connection_setup_no_devices),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(items = state.devices, key = { it.id }) { device ->
                                val isSelected = device.id == state.selectedDeviceId
                                val selectable = !device.isConnected && !state.isPairing
                                ListItem(
                                    leadingContent = {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = null,
                                            enabled = selectable
                                        )
                                    },
                                    headlineContent = { Text(device.name) },
                                    trailingContent = {
                                        if (device.isConnected) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = stringResource(R.string.cd_connection_status_connected)
                                            )
                                        }
                                    },
                                    colors = if (isSelected) {
                                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                    } else {
                                        ListItemDefaults.colors()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = selectable) {
                                            onAction(ConnectionSetupAction.OnDeviceClick(device.id))
                                        }
                                )
                            }
                        }
                    }
                }
            } else if (state.role == AppRole.COMPANION && state.selectedType != null) {
                Text(
                    text = stringResource(R.string.connection_setup_companion_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { onAction(ConnectionSetupAction.OnContinueClick) },
                enabled = !state.isPairing &&
                        (state.status is ConnectionStatus.Connected || state.selectedDeviceId != null),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isPairing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.connection_setup_continue))
                }
            }
        }
    }

    if (state.showBackConfirmation) {
        ConfirmationDialog(
            title = stringResource(R.string.connection_setup_back_confirm_title),
            message = stringResource(R.string.connection_setup_back_confirm_message),
            confirmText = stringResource(R.string.connection_setup_back_confirm_yes),
            dismissText = stringResource(R.string.connection_setup_back_confirm_no),
            onConfirm = { onAction(ConnectionSetupAction.OnBackConfirmed) },
            onDismiss = { onAction(ConnectionSetupAction.OnBackCancelled) }
        )
    }

    if (showWifiDisabledDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.wifi_disabled_title),
            message = stringResource(R.string.wifi_disabled_message),
            confirmText = stringResource(R.string.wifi_disabled_enable),
            dismissText = stringResource(R.string.wifi_disabled_cancel),
            onConfirm = {
                showWifiDisabledDialog = false
                context.startActivity(WifiAvailability.createEnableIntent())
            },
            onDismiss = { showWifiDisabledDialog = false }
        )
    }

    if (showBluetoothDisabledDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.bluetooth_disabled_title),
            message = stringResource(R.string.bluetooth_disabled_message),
            confirmText = stringResource(R.string.bluetooth_disabled_enable),
            dismissText = stringResource(R.string.bluetooth_disabled_cancel),
            onConfirm = {
                showBluetoothDisabledDialog = false
                bluetoothEnableLauncher.launch(BluetoothAvailability.createEnableIntent())
            },
            onDismiss = { showBluetoothDisabledDialog = false }
        )
    }

    val pairingConfirmation = state.pairingState as? PairingState.AwaitingConfirmation
    if (pairingConfirmation != null) {
        when (state.role) {
            AppRole.CONTROL -> PairingWaitingDialog(
                pairing = pairingConfirmation,
                onCancel = { onAction(ConnectionSetupAction.OnCancelPairingClick) }
            )

            AppRole.COMPANION -> PairingRequestBottomSheet(
                pairing = pairingConfirmation,
                onAccept = { onAction(ConnectionSetupAction.OnAcceptPairingClick) },
                onReject = { onAction(ConnectionSetupAction.OnRejectPairingClick) }
            )

            null -> Unit
        }
    }
}

@Composable
private fun PairingWaitingDialog(pairing: PairingState.AwaitingConfirmation, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.pairing_waiting_title, pairing.deviceName)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.pairing_waiting_message))
                Text(pairing.pin, style = MaterialTheme.typography.headlineMedium)
                CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.pairing_waiting_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PairingRequestBottomSheet(
    pairing: PairingState.AwaitingConfirmation,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onReject) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.pairing_request_title, pairing.deviceName),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.pairing_request_message),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = pairing.pin,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.pairing_request_accept))
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(status: ConnectionStatus, isConnecting: Boolean) {
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
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (LocalIsBleachTheme.current) {
                    Icon(
                        painter = painterResource(
                            if (status is ConnectionStatus.Connected) R.drawable.ic_soul_flame else R.drawable.ic_hollow_skull
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (status is ConnectionStatus.Connected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Text(
                    text = when (status) {
                        is ConnectionStatus.Connected -> stringResource(R.string.connection_setup_status_connected)
                        is ConnectionStatus.Reconnecting -> stringResource(R.string.connection_setup_status_reconnecting)
                        is ConnectionStatus.Error -> status.message
                        is ConnectionStatus.Disconnected -> stringResource(R.string.connection_setup_status_disconnected)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConnectionSetupScreenPreview() {
    DualNavTheme {
        ConnectionSetupScreen(
            state = ConnectionSetupState(
                selectedType = ConnectionType.BLUETOOTH,
                status = ConnectionStatus.Disconnected,
                devices = listOf(
                    DeviceInfoUi(id = "1", name = "Pixel 7", isConnected = false),
                    DeviceInfoUi(id = "2", name = "Galaxy S21", isConnected = true)
                )
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectionSetupScreenDarkPreview() {
    DualNavTheme {
        ConnectionSetupScreen(
            state = ConnectionSetupState(
                selectedType = ConnectionType.FIREBASE,
                status = ConnectionStatus.Connected
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectionSetupScreenBleachPreview() {
    DualNavTheme(themeMode = com.blez.dualnav.core.domain.model.AppThemeMode.BLEACH) {
        ConnectionSetupScreen(
            state = ConnectionSetupState(
                selectedType = ConnectionType.BLUETOOTH,
                status = ConnectionStatus.Connected,
                devices = listOf(
                    DeviceInfoUi(id = "1", name = "Pixel 7", isConnected = false),
                    DeviceInfoUi(id = "2", name = "Galaxy S21", isConnected = true)
                )
            ),
            onAction = {}
        )
    }
}
