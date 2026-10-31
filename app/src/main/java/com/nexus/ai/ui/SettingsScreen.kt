package com.nexus.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

private val VOICES = listOf("Aoede", "Kore", "Leda", "Zephyr", "Puck", "Charon")

@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsState()
    val name by vm.userName.collectAsState()
    val model by vm.model.collectAsState()
    val voice by vm.voiceName.collectAsState()
    val angel by vm.angel.collectAsState()
    var keyIn by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var nameIn by remember(name) { mutableStateOf(name) }
    var modelIn by remember(model) { mutableStateOf(model) }
    var angelUi by remember(angel) { mutableStateOf(angel.toFloat()) }
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val micOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s.hasKey) RoundIconButton("←", onBack)
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        Glass(Modifier.fillMaxWidth()) {
            Title("👤  Profile")
            OutlinedTextField(nameIn, { nameIn = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Tumhara naam") })
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.saveName(nameIn) }) { Text("Save") }
        }

        Glass(Modifier.fillMaxWidth()) {
            Title("🔑  Gemini API key")
            Text(if (s.hasKey) "Key saved ✓ (encrypted, sirf is phone me)" else "Abhi key nahi hai",
                style = MaterialTheme.typography.bodySmall, color = if (s.hasKey) Color(0xFF2FBF85) else c.error)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(keyIn, { keyIn = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Gemini API key") },
                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveKey(keyIn); keyIn = "" }, enabled = keyIn.isNotBlank()) { Text("Save") }
                OutlinedButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
                OutlinedButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text("Get key") }
            }
            if (s.hasKey) TextButton(onClick = { vm.clearKey() }) { Text("Remove API key", color = c.error) }
        }

        Glass(Modifier.fillMaxWidth()) {
            Title("🎙  Voice")
            Text("Nexus ki awaaz (agle session se lagu)", style = MaterialTheme.typography.bodySmall,
                color = c.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VOICES.forEach { v ->
                    val sel = v == voice
                    Surface(
                        onClick = { vm.saveVoice(v) }, shape = CircleShape,
                        color = if (sel) c.primary.copy(alpha = 0.25f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (sel) c.primary else c.outline)
                    ) {
                        Text(v, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = if (sel) c.primary else c.onSurface, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Glass(Modifier.fillMaxWidth()) {
            Title("😇😈  Personality")
            Text("Angel ${angelUi.toInt()}%  •  Devil ${100 - angelUi.toInt()}%",
                style = MaterialTheme.typography.bodyMedium)
            Slider(value = angelUi, onValueChange = { angelUi = it },
                onValueChangeFinished = { vm.saveAngel(angelUi.toInt()) }, valueRange = 0f..100f)
            Text("Agle session se lagu hoga", style = MaterialTheme.typography.bodySmall,
                color = c.onSurface.copy(alpha = 0.7f))
        }

        Glass(Modifier.fillMaxWidth()) {
            Title("🧠  Live model")
            Text("Khali chhodo to default: gemini-3.8-live. Error aaye to yahan doosra Live model naam daalo.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurface.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(modelIn, { modelIn = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Live model name") })
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.saveModel(modelIn) }) { Text("Save") }
        }

        Glass(Modifier.fillMaxWidth()) {
            Title("🛡  Permissions")
            Text(if (micOk) "Microphone: Allowed ✓" else "Microphone: Not allowed",
                color = if (micOk) Color(0xFF2FBF85) else c.error)
            Text("Automation: sirf app kholna, YouTube search aur settings kholna. Kuch bhi chhupkar nahi hota.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurface.copy(alpha = 0.7f))
            TextButton(onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${ctx.packageName}")))
            }) { Text("App permissions kholo") }
        }

        Glass(Modifier.fillMaxWidth()) {
            Title("ℹ️  About")
            Text("NEXUS AI v0.3.0", fontWeight = FontWeight.Bold)
            Text("Privacy: jab session chalu ho, tumhari awaaz Google Gemini ko bheji jaati hai. STOP dabate hi band.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurface.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 6.dp))
}
