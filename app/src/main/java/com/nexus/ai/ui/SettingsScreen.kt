package com.nexus.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.nexus.ai.device.NexusAccessibilityService
import kotlinx.coroutines.delay

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
    var tick by remember { mutableStateOf(0) }
    val uri = LocalUriHandler.current
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }

    @Suppress("UNUSED_EXPRESSION") tick
    fun ok(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    val micOk = ok(Manifest.permission.RECORD_AUDIO)
    val smsOk = ok(Manifest.permission.SEND_SMS)
    val contactsOk = ok(Manifest.permission.READ_CONTACTS)
    val accOn = NexusAccessibilityService.instance != null
    val good = Color(0xFF2FBF85)

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
                style = MaterialTheme.typography.bodySmall, color = if (s.hasKey) good else c.error)
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
            Title("⚡  Automation (Accessibility)")
            Text(if (accOn) "ON ✓ Nexus apps chala sakti hai" else "OFF — Nexus abhi apps control nahi kar sakti",
                color = if (accOn) good else c.error)
            Text("Kaise: Accessibility settings kholo → NEXUS AI → ON. Agar 'Restricted setting' dikhe: " +
                "App info → ⋮ → Allow restricted settings, phir dobara ON karo. Ye sirf tab kaam karta hai jab Nexus ko task diya ho.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurface.copy(alpha = 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Accessibility kholo") }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("App info") }
            }
        }

        Glass(Modifier.fillMaxWidth()) {
            Title("🛡  Permissions")
            Text(if (micOk) "Microphone ✓" else "Microphone ✗", color = if (micOk) good else c.error)
            Text(if (smsOk) "SMS bhejna ✓" else "SMS bhejna ✗", color = if (smsOk) good else c.error)
            Text(if (contactsOk) "Contacts padhna ✓" else "Contacts padhna ✗", color = if (contactsOk) good else c.error)
            Text("SMS hamesha tumhari 'haan' ke baad hi jaata hai. Banking/payment apps blocked hain.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurface.copy(alpha = 0.7f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS))
                }) { Text("Allow sab") }
                OutlinedButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Revoke / manage") }
            }
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
                            color = if (sel) c.primary else c.onSurface,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        Glass(Modifier.fillMaxWidth()) {
            Title("😇😈  Personality")
            Text("Angel ${angelUi.toInt()}%  •  Devil ${100 - angelUi.toInt()}%", style = MaterialTheme.typography.bodyMedium)
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
            Title("ℹ️  About")
            Text("NEXUS AI v0.4.0", fontWeight = FontWeight.Bold)
            Text("Privacy: session chalu hone par tumhari awaaz aur Nexus ke padhe hue screen text Google Gemini ko jaate hain. STOP dabate hi band.",
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
