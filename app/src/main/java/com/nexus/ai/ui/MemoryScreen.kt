package com.nexus.ai.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MemoryScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val items by vm.memories.collectAsState()
    val on by vm.memoryOn.collectAsState()
    var q by remember { mutableStateOf("") }
    var add by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val shown = if (q.isBlank()) items else items.filter { it.text.contains(q, true) || it.category.contains(q, true) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIconButton("←", onBack)
            Text("🧠  Memory", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Glass(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (on) "Memory ON" else "Memory OFF", fontWeight = FontWeight.Bold)
                    Text("Nexus sirf kaam ki baatein yaad rakhti hai. Passwords, OTP, card numbers kabhi nahi.",
                        style = MaterialTheme.typography.bodySmall, color = c.onSurface.copy(alpha = 0.7f))
                }
                Switch(checked = on, onCheckedChange = { vm.setMemoryOn(it) })
            }
        }
        Glass(Modifier.fillMaxWidth()) {
            OutlinedTextField(add, { add = it }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Kuch yaad rakhwao (jaise: mujhe chai pasand hai)") })
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { msg = vm.addMemory(add); if (msg == "Remembered") add = "" }, enabled = add.isNotBlank()) { Text("Add") }
                Text(msg, style = MaterialTheme.typography.bodySmall, color = c.secondary)
            }
        }
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search memory") })

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shown.isEmpty()) item { Text("Abhi kuch saved nahi hai.", color = c.onBackground.copy(alpha = 0.6f)) }
            items(shown, key = { it.id }) { m ->
                Glass(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.text)
                            Text(m.category, style = MaterialTheme.typography.labelSmall, color = c.primary)
                        }
                        TextButton(onClick = { vm.deleteMemory(m.id) }) { Text("Delete", color = c.error) }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, vm.exportMemory())
                ctx.startActivity(Intent.createChooser(send, "Export memory").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }, enabled = items.isNotEmpty()) { Text("Export") }
            OutlinedButton(onClick = { confirmClear = true }, enabled = items.isNotEmpty()) {
                Text("Clear all", color = c.error)
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Poori memory delete?") },
        text = { Text("Ye wapas nahi aayegi.") },
        confirmButton = { TextButton(onClick = { vm.clearMemory(); confirmClear = false }) { Text("Delete all") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
    )
}
