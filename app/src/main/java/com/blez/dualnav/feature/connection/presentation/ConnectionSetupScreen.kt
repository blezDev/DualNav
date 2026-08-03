package com.blez.dualnav.feature.connection.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.domain.model.ConnectionType
import com.blez.dualnav.core.presentation.util.ObserveAsEvents
import com.blez.dualnav.ui.theme.DualNavTheme
import com.blez.dualnav.ui.theme.LocalIsBleachTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ConnectionSetupRoot(
    onNavigateNext: (AppRole) -> Unit,
    onNavigateBackToRoleSelection: () -> Unit,
    viewModel: ConnectionSetupViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ConnectionSetupEvent.NavigateNext -> onNavigateNext(event.role)
            is ConnectionSetupEvent.ShowMessage -> Unit
        }
    }

    ConnectionSetupScreen(
        state = state,
        onAction = viewModel::onAction,
        onNavigateBackToRoleSelection = onNavigateBackToRoleSelection
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSetupScreen(
    state: ConnectionSetupState,
    onAction: (ConnectionSetupAction) -> Unit,
    onNavigateBackToRoleSelection: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connection_setup_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBackToRoleSelection) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_change_role))
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val options = listOf(
                    ConnectionType.BLUETOOTH to R.string.connection_type_bluetooth,
                    ConnectionType.WIFI to R.string.connection_type_wifi,
                    ConnectionType.FIREBASE to R.string.connection_type_firebase
                )
                options.forEachIndexed { index, (type, labelRes) ->
                    SegmentedButton(
                        selected = state.selectedType == type,
                        onClick = { onAction(ConnectionSetupAction.OnConnectionTypeSelected(type)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(stringResource(labelRes))
                    }
                }
            }

            ConnectionStatusCard(status = state.status, isConnecting = state.isConnecting)

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
                            ListItem(
                                headlineContent = { Text(device.name) },
                                trailingContent = {
                                    if (device.isConnected) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = stringResource(R.string.cd_connection_status_connected)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { onAction(ConnectionSetupAction.OnContinueClick) },
                enabled = state.status is ConnectionStatus.Connected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.connection_setup_continue))
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
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
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
