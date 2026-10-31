package com.nexus.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.ai.ui.ChatScreen
import com.nexus.ai.ui.ChatViewModel
import com.nexus.ai.ui.HomeScreen
import com.nexus.ai.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val scheme = if (dark) darkColorScheme(
                primary = Color(0xFF9B8CFF),
                background = Color(0xFF07070F),
                surface = Color(0xFF15152A),
                onBackground = Color(0xFFF1EEFF),
                onSurface = Color(0xFFF1EEFF)
            ) else lightColorScheme(
                primary = Color(0xFF5B4BD6),
                background = Color(0xFFF6F4FF),
                surface = Color(0xFFFFFFFF),
                onBackground = Color(0xFF14122B),
                onSurface = Color(0xFF14122B)
            )
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vm: ChatViewModel = viewModel()
                    val s by vm.state.collectAsState()
                    var screen by remember { mutableStateOf("home") }
                    val current = if (!s.hasKey) "settings" else screen
                    BackHandler(enabled = s.hasKey && current != "home") { screen = "home" }
                    when (current) {
                        "settings" -> SettingsScreen(vm, onBack = { screen = "home" })
                        "chat" -> Box(Modifier.fillMaxSize()) {
                            ChatScreen(vm)
                            TextButton(
                                onClick = { screen = "home" },
                                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                            ) { Text("Home") }
                        }
                        else -> HomeScreen(vm, onSettings = { screen = "settings" }, onChats = { screen = "chat" })
                    }
                }
            }
        }
    }
}
