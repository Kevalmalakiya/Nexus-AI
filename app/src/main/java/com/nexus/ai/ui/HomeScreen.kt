package com.nexus.ai.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexus.ai.voice.VoicePhase

private fun isOnline(ctx: Context): Boolean {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun HomeScreen(vm: ChatViewModel, onSettings: () -> Unit) {
    val phase by vm.phase.collectAsState()
    val level by vm.voice.level.collectAsState()
    val heard by vm.heard.collectAsState()
    val reply by vm.reply.collectAsState()
    val s by vm.state.collectAsState()
    val name by vm.userName.collectAsState()
    val ctx = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    val online = remember(phase, s.error) { runCatching { isOnline(ctx) }.getOrDefault(true) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.onOrbTap() else vm.setError("Voice ke liye mic permission chahiye")
    }
    fun tap() {
        note = null
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.onOrbTap() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val status = when (phase) {
        VoicePhase.IDLE -> "Orb ko tap karke Nexus se baat karo"
        VoicePhase.LISTENING -> "Listening… bolo"
        VoicePhase.THINKING -> "Thinking…"
        VoicePhase.SPEAKING -> "Speaking… (tap karke rok sakte ho)"
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Surface(onClick = onSettings, shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("☰") }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                Text(
                    if (online) "● ONLINE" else "● OFFLINE",
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (online) Color(0xFF2FBF85) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            Spacer(Modifier.size(44.dp))
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth()) {
            Text("Namaste", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
            Text(name, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            NexusOrb(
                phase, level,
                Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) { tap() }
            )
        }

        Text(status, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f), textAlign = TextAlign.Center)
        if (phase != VoicePhase.IDLE) {
            Spacer(Modifier.height(8.dp))
            Surface(
                onClick = { vm.stopAll() }, shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            ) {
                Text("STOP", Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (heard.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("Tum: $heard", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                maxLines = 2, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }
        if (reply.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(reply, textAlign = TextAlign.Center, maxLines = 3)
        }
        s.error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall)
        }
        note?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureCard("⛶  Screen", "Coming soon", Modifier.weight(1f)) {
                note = "Screen share Phase 4 me aayega (abhi available nahi)"
            }
            FeatureCard("⚡  Automation", "Coming soon", Modifier.weight(1f)) {
                note = "Device automation Phase 3 me aayega (abhi available nahi)"
            }
        }
    }
}

@Composable
private fun FeatureCard(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f))
        }
    }
}
