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
import com.blez.dualnav.navigation.AppEntryViewModel
import com.blez.dualnav.navigation.DualNavHost
import com.blez.dualnav.ui.theme.DualNavTheme
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
