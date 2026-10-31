package com.nexus.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    var keyInput by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding().padding(12.dp)
    ) {
        Text("NEXUS AI", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("More Than AI. Your Digital Companion.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))

        if (!s.hasKey) {
            Text("Enter your own Gemini API key (stored encrypted on this device).")
            OutlinedTextField(keyInput, { keyInput = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("API key") })
            Button(onClick = { vm.saveKey(keyInput); keyInput = "" }) { Text("Save") }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.messages) { m ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                        Surface(shape = RoundedCornerShape(16.dp), color = if (m.fromUser) MaterialTheme.colorScheme.primary.copy(alpha = .3f) else MaterialTheme.colorScheme.surface) {
                            Text(m.text, Modifier.padding(12.dp))
                        }
                    }
                }
                if (s.loading) item { Text("Nexus is thinking…") }
            }
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Message Nexus") })
                Spacer(Modifier.width(8.dp))
                Button(onClick = { vm.send(input); input = "" }, enabled = !s.loading) { Text("Send") }
            }
            TextButton(onClick = { vm.clearKey() }) { Text("Remove API key") }
        }
    }
}

