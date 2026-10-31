package com.nexus.ai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    val name by vm.userName.collectAsState()
    val model by vm.model.collectAsState()
    var keyIn by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var nameIn by remember(name) { mutableStateOf(name) }
    var modelIn by remember(model) { mutableStateOf(model) }
    val uri = LocalUriHandler.current

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (s.hasKey) TextButton(onClick = onBack) { Text("← Back") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Section("Profile") {
            OutlinedTextField(nameIn, { nameIn = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Tumhara naam") })
            Button(onClick = { vm.saveName(nameIn) }) { Text("Save") }
        }

        Section("Gemini API key") {
            Text(if (s.hasKey) "Key saved ✓ (encrypted, sirf is phone me)" else "Abhi key nahi hai",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(keyIn, { keyIn = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Gemini API key") },
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveKey(keyIn); keyIn = "" }, enabled = keyIn.isNotBlank()) { Text("Save") }
                OutlinedButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
                OutlinedButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text("Get key") }
            }
            if (s.hasKey) TextButton(onClick = { vm.clearKey() }) { Text("Remove API key") }
        }

        Section("Live model") {
            Text("Khali chhodo to default use hoga: gemini-3.8-live. Error aaye to yahan doosra Live model naam daalo.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(modelIn, { modelIn = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Live model name") })
            Button(onClick = { vm.saveModel(modelIn) }) { Text("Save") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
