package com.nexus.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nexus.ai.voice.VoicePhase

@Composable
fun VoiceScreen(vm: ChatViewModel, onKeyboard: () -> Unit) {
    val phase by vm.phase.collectAsState()
    val level by vm.voice.level.collectAsState()
    val heard by vm.heard.collectAsState()
    val reply by vm.reply.collectAsState()
    val s by vm.state.collectAsState()
    val ctx = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.onOrbTap() else vm.setError("Voice ke liye mic permission chahiye")
    }
    fun tap() {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) vm.onOrbTap() else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val status = when (phase) {
        VoicePhase.IDLE -> "Orb ko tap karke bolo"
        VoicePhase.LISTENING -> "Sun rahi hoon…"
        VoicePhase.THINKING -> "Soch rahi hoon…"
        VoicePhase.SPEAKING -> "Bol rahi hoon… (tap karke beech me bol sakte ho)"
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF05050D))
            .statusBarsPadding().navigationBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("NEXUS", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFB9A8FF))
        Spacer(Modifier.height(4.dp))
        Text(status, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)

        Spacer(Modifier.weight(1f))
        NexusOrb(
            phase, level,
            Modifier.fillMaxWidth().aspectRatio(1f).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null
            ) { tap() }
        )
        Spacer(Modifier.weight(1f))

        if (heard.isNotBlank()) Text("Tum: $heard", color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, maxLines = 3)
        if (reply.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(reply, color = Color.White, textAlign = TextAlign.Center, maxLines = 5)
        }
        s.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onKeyboard) { Text("Type") }
            TextButton(onClick = { vm.stopAll() }) { Text("Stop") }
        }
    }
}
