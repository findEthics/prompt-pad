package com.hermes.promptpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun InstructionsScreen(done: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Black).safeDrawingPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth()) {
            Text("prompt-pad", Modifier.align(Alignment.Center), style = MaterialTheme.typography.headlineMedium)
            IconButton(done, Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Outlined.Close, "Dismiss instructions", tint = Accent)
            }
        }
        Spacer(Modifier.height(28.dp))
        Instruction("Home", "Long-press a blank area to edit, then tap a tile to swap its shortcut. Swipe left for Settings, swipe up for apps.")
        Instruction("Notifier", "Swipe right on Home to open it (on by default). Grant notification access when asked. Reply, star, or swipe a notification away; music shows on top while playing.")
        Instruction("Notifier as home", "Optional in Settings: the Notifier becomes Home — swipe up for apps, swipe left to open your chosen app, double-tap to sleep.")
        Instruction("Apps", "Swipe up or type on the keyboard to search.")
        Spacer(Modifier.weight(1f))
        Text(
            "start",
            Modifier.fillMaxWidth().heightIn(min = Dim2.touch).clickable { done() }.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Accent,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Instruction(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Accent)
        Text(text, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Dim)
    }
}
