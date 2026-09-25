package com.hermes.promptpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Selectable filters. "All" is the default; emails have no category and fall under All.
val HUB_FILTERS = listOf("Calls", "Messages", "All", "Starred")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(prefs: Prefs, tick: Int, back: () -> Unit, nav: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    var filter by remember { mutableIntStateOf(HUB_FILTERS.indexOf("All")) }
    var replyingTo by remember { mutableStateOf<String?>(null) }
    val all = HubListener.items
    // ponytail: starred items leave every other view and live only under Starred.
    val shown = all.filter {
        when (HUB_FILTERS[filter]) {
            "Calls" -> it.kind == HubKind.CALL && !it.starred
            "Messages" -> it.kind == HubKind.MESSAGE && !it.starred
            "Starred" -> it.starred
            else -> !it.starred
        }
    }
    val hasStarred = all.any { it.starred }
    var pickLeftApp by remember { mutableStateOf(false) }
    val leftSwipe = {
        if (prefs.notifierLeftApp.isBlank()) pickLeftApp = true else launchShortcut(ctx, prefs.notifierLeftApp, nav)
    }

    val time = remember(tick) { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) }
    var weather by remember(prefs.notifierAsHome, prefs.showWeather, prefs.weatherLabel) {
        mutableStateOf(if (prefs.notifierAsHome && prefs.showWeather) prefs.weatherCache() else null)
    }
    LaunchedEffect(prefs.notifierAsHome, prefs.showWeather, prefs.weatherLatitude, prefs.weatherLongitude, tick) {
        weather = if (prefs.notifierAsHome && prefs.showWeather) Weather.current(prefs) else null
    }
    // Also start media polling from the UI: onListenerConnected may not re-fire after an app update.
    LaunchedEffect(Unit) { MediaWidget.start(ctx, android.content.ComponentName(ctx, HubListener::class.java)) }
    EdgeScreen("notifier", Modifier.pointerInput(prefs.notifierAsHome) {
        var dx = 0f; var dy = 0f
        detectDragGestures(
            onDragStart = { dx = 0f; dy = 0f },
            onDragEnd = {
                when {
                    // notifier-as-home: it's the launcher home, so mirror Home's own gestures.
                    prefs.notifierAsHome && dy < -120f && kotlin.math.abs(dy) > kotlin.math.abs(dx) -> nav(Screen.Drawer)
                    dx > 80.dp.toPx() -> nav(Screen.Notes)
                    dx < -80.dp.toPx() -> if (prefs.notifierAsHome) leftSwipe() else back()
                }
            },
        ) { _, delta -> dx += delta.x; dy += delta.y }
    }.pointerInput(prefs.tapToSleep) {
        // Notifier-as-home mirrors Home's double-tap-to-sleep. Cards consume their own taps.
        detectTapGestures(onDoubleTap = { if (prefs.tapToSleep) TapToSleepAccessibilityService.lockScreen() })
    },
    heading = if (prefs.notifierAsHome) ({
        Text(time, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, color = Accent)
    }) else null,
    topRight = if (prefs.notifierAsHome && prefs.showWeather) ({
        Text(weather?.let { weatherText(it.apparentTemperatureC, it.weatherCode, it.isDay) } ?: "—",
            Modifier.clickable { uriHandler.openUri("https://overcast-chi.vercel.app") },
            style = MaterialTheme.typography.bodySmall)
    }) else null) {
        if (!HubListener.isEnabled(ctx)) {
            Card(Modifier.fillMaxWidth(), onClick = { HubListener.openSettings(ctx) }) {
                Text("Grant notification access", style = MaterialTheme.typography.bodyMedium, color = Accent)
                Text("Prompt-Pad needs it to collect messages, calls and alerts here.",
                    style = MaterialTheme.typography.bodySmall, color = Dim)
            }
            Spacer(Modifier.height(Dim2.gap))
        }
        // Active and paused sessions both remain controls; only paused cards are dismissible.
        val media = MediaWidget.state.value
        if (media != null) {
            val card = @Composable {
                Card(Modifier.fillMaxWidth(), onClick = { MediaWidget.open(ctx) }) {
                    Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).heightIn(min = Dim2.touch), verticalArrangement = Arrangement.Center) {
                            Text(media.title ?: "Playing", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!media.artist.isNullOrBlank()) Text(media.artist, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(MediaWidget::prev, Modifier.fillMaxHeight()) { Icon(Icons.Filled.SkipPrevious, "Previous track", Modifier.size(18.dp), tint = Accent) }
                        IconButton(MediaWidget::playPause, Modifier.fillMaxHeight()) { Icon(if (media.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (media.playing) "Pause" else "Play", Modifier.size(18.dp), tint = Accent) }
                        IconButton(MediaWidget::next, Modifier.fillMaxHeight()) { Icon(Icons.Filled.SkipNext, "Next track", Modifier.size(18.dp), tint = Accent) }
                    }
                }
            }
            if (media.playing) card() else {
                val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.Settled) true else { MediaWidget.dismiss(); false }
                })
                SwipeToDismissBox(dismissState, {}, enableDismissFromStartToEnd = true, enableDismissFromEndToStart = true) { card() }
            }
            Spacer(Modifier.height(Dim2.gap))
        }
        // Box keeps tabs pinned bottom; a short list wraps at top, leaving empty space to the root's swipe/tap gestures.
        Box(Modifier.weight(1f)) {
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    if (i >= 0) {
                                        val nowStarred = !item.starred
                                        all[i] = item.copy(starred = nowStarred)
                                        // Starring clears Android's tray but retains the card in Starred.
                                        if (nowStarred) HubListener.retain(item.key)
                                    }
                                }.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium, color = Accent)
                        }
                        item.messages.forEach { message ->
                            Text(if (message.isUser) "You: ${message.text}" else message.text,
                                style = MaterialTheme.typography.bodySmall, color = if (message.isUser) Accent else Dim,
                                maxLines = 5, overflow = TextOverflow.Ellipsis)
                        }
                        if (replyingTo == item.key && item.reply != null) {
                            var draft by remember(item.key) { mutableStateOf("") }
                            val focus = remember(item.key) { FocusRequester() }
                            // Tapping reply is one step: focus the field and raise the keyboard.
                            LaunchedEffect(item.key) { focus.requestFocus(); keyboard?.show() }
                            fun send() {
                                if (draft.isNotBlank() && HubListener.sendReply(ctx, item, draft)) replyingTo = null
                            }
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextField(draft, { draft = it }, Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent {
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
        }
        Spacer(Modifier.height(Dim2.gap))
        HubTabs(filter, hasStarred, { filter = it }) { nav(Screen.Todo) }
    }
    if (pickLeftApp) AppPicker(onPick = { prefs.notifierLeftApp = it; pickLeftApp = false; launchShortcut(ctx, it, nav) }, onDismiss = { pickLeftApp = false })
}

/** Notifier's own tab row: 4 filter icons plus a to-do icon that opens To Do. */
@Composable
fun HubTabs(selected: Int, starredActive: Boolean, onSelect: (Int) -> Unit, onTodo: () -> Unit) {
    // Material vectors always tint white regardless of device fonts; "All" stays text.
    val icons = mapOf(
        "Calls" to Icons.Outlined.Call,
        "Messages" to Icons.Outlined.ChatBubbleOutline,
        "Starred" to Icons.Outlined.StarOutline,
        "to do" to Icons.Outlined.CheckBox,
    )
    val labels = HUB_FILTERS + "to do"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, l ->
            val isTodo = l == "to do"
            val active = !isTodo && i == selected
            val tint = if (active) Black else White
            val cell = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) Accent else Black)
                .clickable { if (isTodo) onTodo() else onSelect(i) }
                .padding(horizontal = 2.dp, vertical = 8.dp)
            val icon = icons[l]
            if (icon != null) {
                Box(cell, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(icon, l, Modifier.size(22.dp), tint = tint)
                        // Degree marker flags a non-empty Starred tray (mirrors the weather temp cue).
                        if (l == "Starred" && starredActive) Text("°", fontSize = 18.sp, color = tint)
                    }
                }
            } else {
                Text(l, cell, fontSize = 18.sp, color = tint, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}
