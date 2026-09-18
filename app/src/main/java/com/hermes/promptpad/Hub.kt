package com.hermes.promptpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

val HUB_FILTERS = listOf("All", "Messages", "Calls", "Emails", "Starred")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(prefs: Prefs, tick: Int, back: () -> Unit) {
    val ctx = LocalContext.current
    var filter by remember { mutableIntStateOf(0) }
    var replyingTo by remember { mutableStateOf<String?>(null) }
    val all = HubListener.items
    val shown = all.filter {
        when (filter) {
            1 -> it.kind == HubKind.MESSAGE
            2 -> it.kind == HubKind.CALL
            3 -> it.kind == HubKind.EMAIL
            4 -> it.starred
            else -> true
        }
    }

    val time = remember(tick) { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) }
    EdgeScreen("notifier", Modifier.pointerInput(Unit) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onHorizontalDrag = { _, dx -> distance += dx },
            onDragEnd = { if (distance < -80.dp.toPx()) back() },
        )
    }, topRight = if (prefs.notifierAsHome) ({
        Text(time, style = MaterialTheme.typography.bodyMedium, color = Accent)
    }) else null) {
        if (!HubListener.isEnabled(ctx)) {
            Card(Modifier.fillMaxWidth(), onClick = { HubListener.openSettings(ctx) }) {
                Text("Grant notification access", style = MaterialTheme.typography.bodyMedium, color = Accent)
                Text("Prompt-Pad needs it to collect messages, calls and alerts here.",
                    style = MaterialTheme.typography.bodySmall, color = Dim)
            }
            Spacer(Modifier.height(Dim2.gap))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(shown, key = { it.key }) { item ->
                val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.Settled) true
                    else { HubListener.dismiss(item.key); false }
                })
                SwipeToDismissBox(
                    state = dismissState,
                    backgroundContent = {},
                    enableDismissFromStartToEnd = true,
                    enableDismissFromEndToStart = true,
                ) {
                    Card(Modifier.fillMaxWidth(), onClick = { HubListener.open(ctx, item) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (item.reply != null) {
                                IconButton({ replyingTo = if (replyingTo == item.key) null else item.key }) {
                                    Icon(Icons.AutoMirrored.Outlined.Reply, "Reply", tint = Accent)
                                }
                            }
                            Text(if (item.starred) "★" else "☆",
                                Modifier.clickable {
                                    val i = all.indexOfFirst { it.key == item.key }
                                    if (i >= 0) all[i] = item.copy(starred = !item.starred)
                                }.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium, color = Accent)
                        }
                        if (item.text.isNotBlank()) Text(item.text, style = MaterialTheme.typography.bodySmall, color = Dim,
                            maxLines = 5, overflow = TextOverflow.Ellipsis)
                        item.replies.forEach { Text("You: $it", style = MaterialTheme.typography.bodySmall, color = Accent) }
                        if (replyingTo == item.key && item.reply != null) {
                            var draft by remember(item.key) { mutableStateOf("") }
                            fun send() {
                                if (draft.isNotBlank() && HubListener.sendReply(ctx, item, draft)) replyingTo = null
                            }
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextField(draft, { draft = it }, Modifier.weight(1f).onPreviewKeyEvent {
                                    if (it.key == Key.Enter && it.type == KeyEventType.KeyDown) { send(); true } else false
                                },
                                    placeholder = { Text("reply", style = MaterialTheme.typography.bodySmall, color = DotIdle) },
                                    singleLine = true, textStyle = MaterialTheme.typography.bodySmall,
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                    keyboardActions = KeyboardActions(onSend = { send() }),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Black, unfocusedContainerColor = Black,
                                        cursorColor = Accent, focusedIndicatorColor = Accent, unfocusedIndicatorColor = DotIdle))
                                Text("send", Modifier.clickable { send() }.padding(start = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium, color = Accent)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Dim2.gap))
        Tabs(HUB_FILTERS, filter) { filter = it }
    }
}
