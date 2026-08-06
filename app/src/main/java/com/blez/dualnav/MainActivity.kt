package com.blez.dualnav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blez.dualnav.core.domain.repository.UpdateInstaller
import com.blez.dualnav.navigation.AppEntryViewModel
import com.blez.dualnav.navigation.DualNavHost
import com.blez.dualnav.ui.theme.DualNavTheme
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    // Requested here, at startup, rather than lazily from Settings — so the "install unknown
    // apps" permission is already granted by the time the user gets to Settings > Check for
    // updates and taps "Download & Install".
    private val updateInstaller: UpdateInstaller by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!updateInstaller.canInstallPackages()) {
            updateInstaller.requestInstallPermission()
        }
        setContent {
            val entryViewModel: AppEntryViewModel = koinViewModel()
            val themeMode by entryViewModel.themeMode.collectAsStateWithLifecycle()

            DualNavTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DualNavHost(entryViewModel = entryViewModel)
                }
            }
        }
    }
}
