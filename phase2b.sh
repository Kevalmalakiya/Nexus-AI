#!/data/data/com.termux/files/usr/bin/bash
# NEXUS AI - theme patch: dark + light (follows system), readable text everywhere
set -e
P=app/src/main/java/com/nexus/ai
[ -d $P ] || { echo "Pehle: cd ~/Nexus-AI"; exit 1; }

# keep helper scripts out of the repo
grep -qx 'phase*.sh' .gitignore || echo 'phase*.sh' >> .gitignore
git rm --cached -q phase2.sh 2>/dev/null || true
rm -f phase2.sh

cat > $P/MainActivity.kt <<'EOF'
package com.nexus.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.nexus.ai.ui.VoiceScreen

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
                // Surface sets the default text colour, so text is always readable
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
}
EOF

# VoiceScreen: swap hard-coded colours for theme colours
V=$P/ui/VoiceScreen.kt
sed -i 's|Color(0xFF05050D)|MaterialTheme.colorScheme.background|' $V
sed -i 's|Color(0xFFB9A8FF)|MaterialTheme.colorScheme.primary|' $V
sed -i 's|Color.White.copy(alpha = 0.7f)|MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)|' $V
sed -i 's|Color.White.copy(alpha = 0.6f)|MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f)|' $V
sed -i 's|color = Color.White,|color = MaterialTheme.colorScheme.onBackground,|' $V

# ChatScreen: subtitle was nearly invisible
C=$P/ui/ChatScreen.kt
sed -i 's|style = MaterialTheme.typography.bodySmall)|style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))|' $C

echo "Theme patch applied."

