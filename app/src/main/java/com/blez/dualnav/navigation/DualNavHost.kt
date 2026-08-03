package com.blez.dualnav.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.blez.dualnav.core.domain.model.AppRole
import com.blez.dualnav.feature.companion.presentation.CompanionHomeRoot
import com.blez.dualnav.feature.companion.presentation.CompanionHomeRoute
import com.blez.dualnav.feature.connection.presentation.ConnectionSetupRoot
import com.blez.dualnav.feature.connection.presentation.ConnectionSetupRoute
import com.blez.dualnav.feature.control.presentation.ControlHomeRoot
import com.blez.dualnav.feature.control.presentation.ControlHomeRoute
import com.blez.dualnav.feature.role.presentation.RoleSelectionRoot
import com.blez.dualnav.feature.role.presentation.RoleSelectionRoute
import com.blez.dualnav.feature.settings.presentation.SettingsRoot
import com.blez.dualnav.feature.settings.presentation.SettingsRoute
import org.koin.androidx.compose.koinViewModel

@Composable
fun DualNavHost(
    modifier: Modifier = Modifier,
    entryViewModel: AppEntryViewModel = koinViewModel(),
    navController: NavHostController = rememberNavController()
) {
    val startDestination by entryViewModel.startDestination.collectAsStateWithLifecycle()

    when (val destination = startDestination) {
        AppStartDestination.Loading -> LoadingScreen(modifier)
        else -> {
            NavHost(
                navController = navController,
                startDestination = destination.toRoute(),
                modifier = modifier
            ) {
                composable<RoleSelectionRoute> {
                    // Reachable two ways: as the app's very first screen (nothing to go back to)
                    // or via Settings > Change role (something to cancel back out to).
                    val canNavigateBack = navController.previousBackStackEntry != null
                    RoleSelectionRoot(
                        onNavigateNext = {
                            // Both roles need the transport re-established each session, so both
                            // land on connection setup next, not directly on their home screen.
                            // Clearing the whole back stack (not just this route) means a role
                            // change from Settings can't leave the old role's screens reachable.
                            navController.navigate(ConnectionSetupRoute) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        },
                        onNavigateBack = if (canNavigateBack) {
                            { navController.popBackStack() }
                        } else {
                            null
                        }
                    )
                }
                composable<ConnectionSetupRoute> {
                    ConnectionSetupRoot(
                        onNavigateNext = { role ->
                            val next = if (role == AppRole.CONTROL) ControlHomeRoute else CompanionHomeRoute
                            navController.navigate(next) {
                                popUpTo(ConnectionSetupRoute) { inclusive = true }
                            }
                        },
                        // ConnectionSetupRoute is often the start destination on resume, so there's
                        // nothing to pop back to — navigate to role selection explicitly instead.
                        onNavigateBackToRoleSelection = { navController.navigate(RoleSelectionRoute) }
                    )
                }
                composable<ControlHomeRoute> {
                    ControlHomeRoot(onNavigateToSettings = { navController.navigate(SettingsRoute) })
                }
                composable<CompanionHomeRoute> {
                    CompanionHomeRoot(onNavigateToSettings = { navController.navigate(SettingsRoute) })
                }
                composable<SettingsRoute> {
                    SettingsRoot(
                        onNavigateBack = { navController.popBackStack() },
                        onChangeRoleClick = { navController.navigate(RoleSelectionRoute) }
                    )
                }
            }
        }
    }
}

private fun AppStartDestination.toRoute(): Any = when (this) {
    AppStartDestination.Loading -> RoleSelectionRoute
    AppStartDestination.RoleSelection -> RoleSelectionRoute
    AppStartDestination.ResumeControl -> ConnectionSetupRoute
    AppStartDestination.ResumeCompanion -> ConnectionSetupRoute
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
