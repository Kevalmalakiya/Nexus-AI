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
import com.nexus.ai.ui.MemoryScreen
import com.nexus.ai.ui.NexusTheme
import com.nexus.ai.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexusTheme {
                val vm: ChatViewModel = viewModel()
                val s by vm.state.collectAsState()
                var screen by remember { mutableStateOf("home") }
                val cur = if (!s.hasKey) "settings" else screen
                BackHandler(enabled = s.hasKey && cur != "home") { screen = "home" }
                when (cur) {
                    "settings" -> SettingsScreen(vm) { screen = "home" }
                    "memory" -> MemoryScreen(vm) { screen = "home" }
                    else -> HomeScreen(vm, onSettings = { screen = "settings" }, onMemory = { screen = "memory" })
                }
            }
        }
    }
}
