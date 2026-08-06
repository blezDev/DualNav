package com.blez.dualnav.feature.companion.presentation

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blez.dualnav.R
import com.blez.dualnav.core.domain.model.ConnectionStatus
import com.blez.dualnav.core.presentation.components.ConfirmationDialog
import com.blez.dualnav.core.presentation.util.CompanionWakeLock
import com.blez.dualnav.core.presentation.util.ObserveAsEvents
import com.blez.dualnav.core.presentation.util.UiText
import com.blez.dualnav.core.presentation.util.asString
import com.blez.dualnav.feature.overlay.presentation.FloatingOverlayService
import com.blez.dualnav.feature.overlay.presentation.OverlayPermission
import com.blez.dualnav.ui.theme.DualNavTheme
import com.blez.dualnav.ui.theme.LocalIsBleachTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun CompanionHomeRoot(
    onNavigateToSettings: () -> Unit,
    onNavigateToRoleSelection: () -> Unit,
    viewModel: CompanionHomeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var overlayServiceRunning by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showAccessibilityPermissionDialog by remember { mutableStateOf(false) }

    fun refreshOverlay() {
        if (OverlayPermission.isGranted(context)) {
            if (!overlayServiceRunning) {
                context.startForegroundService(Intent(context, FloatingOverlayService::class.java))
                overlayServiceRunning = true
            }
        } else {
            showOverlayPermissionDialog = true
        }
    }

    fun refreshAccessibility() {
        if (!MapsControlAccessibilityPermission.isEnabled(context)) {
            showAccessibilityPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        context.startForegroundService(Intent(context, CommandReceiverService::class.java))
    }

    // The floating widget needs to be up for as long as this screen represents an active
    // Companion session — including while backgrounded behind Google Maps, not just while
    // visible — so it's tied to this composable's lifetime, not Android's resumed/paused state.
    DisposableEffect(Unit) {
        CompanionWakeLock.acquire(context)
        refreshOverlay()
        refreshAccessibility()
        onDispose {
            CompanionWakeLock.release()
            if (overlayServiceRunning) {
                context.stopService(Intent(context, FloatingOverlayService::class.java))
                overlayServiceRunning = false
            }
        }
    }

    // Permissions can only change in Settings, outside this screen — re-check whenever the
    // user comes back to it, since there's no listener API for either permission.
    ObserveOnResume {
        refreshOverlay()
        refreshAccessibility()
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is CompanionHomeEvent.ShowSnackbar -> {
                val message = event.message.asString(context)
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
            CompanionHomeEvent.NavigateToRoleSelection -> onNavigateToRoleSelection()
        }
    }

    CompanionHomeScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onAction = viewModel::onAction,
        onNavigateToSettings = onNavigateToSettings
    )

    if (showOverlayPermissionDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.overlay_permission_title),
            message = stringResource(R.string.overlay_permission_message),
            confirmText = stringResource(R.string.overlay_permission_enable),
            dismissText = stringResource(R.string.overlay_permission_cancel),
            onConfirm = {
                showOverlayPermissionDialog = false
                context.startActivity(OverlayPermission.createRequestIntent(context))
            },
            onDismiss = { showOverlayPermissionDialog = false }
        )
    }

    if (showAccessibilityPermissionDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.accessibility_permission_title),
            message = stringResource(R.string.accessibility_permission_message),
            confirmText = stringResource(R.string.accessibility_permission_enable),
            dismissText = stringResource(R.string.accessibility_permission_cancel),
            onConfirm = {
                showAccessibilityPermissionDialog = false
                context.startActivity(MapsControlAccessibilityPermission.createRequestIntent())
            },
            onDismiss = { showAccessibilityPermissionDialog = false }
        )
    }
}

@Composable
private fun ObserveOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionHomeScreen(
    state: CompanionHomeState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onAction: (CompanionHomeAction) -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    BackHandler { onAction(CompanionHomeAction.OnBackPress) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.companion_home_title)) },
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
            Text(
                text = stringResource(R.string.companion_home_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ConnectionStatusCard(state.connectionStatus)

            if (state.connectionStatus !is ConnectionStatus.Connected) {
                OutlinedButton(
                    onClick = { onAction(CompanionHomeAction.OnReconnectClick) },
                    enabled = !state.isReconnecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.companion_home_reconnect))
                }
            }

            Text(
                text = stringResource(R.string.companion_home_activity_label),
                style = MaterialTheme.typography.titleMedium
            )

            if (state.recentActivity.isEmpty()) {
                Text(
                    text = stringResource(R.string.companion_home_no_activity),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(items = state.recentActivity) { entry ->
                        Text(text = entry.asString(), style = MaterialTheme.typography.bodyMedium)
                    }
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
            onConfirm = { onAction(CompanionHomeAction.OnDisconnectConfirmed) },
            onDismiss = { onAction(CompanionHomeAction.OnDisconnectCancelled) }
        )
    }

    if (state.showRemoteDisconnectedDialog) {
        AlertDialog(
            onDismissRequest = { onAction(CompanionHomeAction.OnRemoteDisconnectedAcknowledged) },
            title = { Text(stringResource(R.string.remote_disconnected_control_title)) },
            text = { Text(stringResource(R.string.remote_disconnected_control_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(CompanionHomeAction.OnRemoteDisconnectedAcknowledged) }) {
                    Text(stringResource(R.string.remote_disconnected_ok))
                }
            }
        )
    }
}

@Composable
private fun ConnectionStatusCard(status: ConnectionStatus) {
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
                    text = stringResource(R.string.companion_home_status_label),
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

@Preview(showBackground = true)
@Composable
private fun CompanionHomeScreenPreview() {
    DualNavTheme {
        CompanionHomeScreen(
            state = CompanionHomeState(
                connectionStatus = ConnectionStatus.Connected,
                recentActivity = listOf(
                    UiText.DynamicString("Navigate to 123 Main St"),
                    UiText.DynamicString("Stop requested")
                )
            )
        )
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CompanionHomeScreenDarkPreview() {
    DualNavTheme {
        CompanionHomeScreen(state = CompanionHomeState(connectionStatus = ConnectionStatus.Disconnected))
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CompanionHomeScreenBleachPreview() {
    DualNavTheme(themeMode = com.blez.dualnav.core.domain.model.AppThemeMode.BLEACH) {
        CompanionHomeScreen(
            state = CompanionHomeState(
                connectionStatus = ConnectionStatus.Connected,
                recentActivity = listOf(
                    UiText.DynamicString("Navigate to 123 Main St"),
                    UiText.DynamicString("Stop requested")
                )
            )
        )
    }
}
