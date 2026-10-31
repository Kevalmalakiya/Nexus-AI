package com.nexus.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.ai.ui.ChatViewModel
import com.nexus.ai.ui.HomeScreen
import com.nexus.ai.ui.NexusTheme
import com.nexus.ai.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexusTheme {
                val vm: ChatViewModel = viewModel()
                val s by vm.state.collectAsState()
                var settings by remember { mutableStateOf(false) }
                val showSettings = !s.hasKey || settings
                BackHandler(enabled = s.hasKey && showSettings) { settings = false }
                if (showSettings) SettingsScreen(vm, onBack = { settings = false })
                else HomeScreen(vm, onSettings = { settings = true })
            }
        }
    }
}
