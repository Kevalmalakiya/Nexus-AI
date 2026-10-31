package com.nexus.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexus.ai.ui.ChatScreen
import com.nexus.ai.ui.ChatViewModel
import com.nexus.ai.ui.VoiceScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF8E7CFF),
                    background = Color(0xFF0B0B17),
                    surface = Color(0xFF15152A)
                )
            ) {
                val vm: ChatViewModel = viewModel()
                val s by vm.state.collectAsState()
                var textMode by remember { mutableStateOf(false) }
                if (!s.hasKey || textMode) {
                    Box(Modifier.fillMaxSize()) {
                        ChatScreen(vm)
                        if (s.hasKey) {
                            TextButton(
                                onClick = { textMode = false },
                                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding()
                            ) { Text("Voice") }
                        }
                    }
                } else {
                    VoiceScreen(vm, onKeyboard = { textMode = true })
                }
            }
        }
    }
}
