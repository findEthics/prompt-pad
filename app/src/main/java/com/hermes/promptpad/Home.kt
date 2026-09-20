package com.hermes.promptpad

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(prefs: Prefs, nav: (Screen) -> Unit, tick: Int) {
    var editing by remember { mutableStateOf(false) }
    var picking by remember { mutableIntStateOf(-1) }

    Box(
        Modifier.fillMaxSize().background(Black)
            .pointerInput(prefs.notifierEnabled) {
                var dx = 0f; var dy = 0f
                detectDragGestures(
                    onDragStart = { dx = 0f; dy = 0f },
                    onDragEnd = {
                        when {
                            dy < -120f && kotlin.math.abs(dy) > kotlin.math.abs(dx) -> nav(Screen.Drawer)
                            dx > 120f && prefs.notifierEnabled -> nav(Screen.Hub)
                            dx < -120f -> nav(Screen.Settings)
                        }
                    },
                ) { _, delta -> dx += delta.x; dy += delta.y }
            }
            .pointerInput(prefs.tapToSleep) {
                detectTapGestures(
                    onLongPress = { editing = !editing },
                    onDoubleTap = { if (prefs.tapToSleep) TapToSleepAccessibilityService.lockScreen() },
                )
            }
            .then(
                if (prefs.hideStatusBar && prefs.peakRight) {
                    Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    )
                } else {
                    Modifier.safeDrawingPadding()
                }
            ).padding(horizontal = 14.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(if (prefs.peakRight) 8.dp else 14.dp))
            PeakWidget(prefs, tick, nav, editing) { picking = it }
            Spacer(Modifier.height(16.dp))
            if (prefs.showAgenda || prefs.showTodo) {
                GlanceRows(nav, tick, prefs.textScale, prefs.showAgenda, prefs.showTodo)
            }
            Spacer(Modifier.weight(1f))
            AppGrid(prefs, editing, nav) { picking = it }
            if (editing) {
                Text(
                    "edit mode · tap date, clock, weather or icon to replace · long-press to exit",
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    if (picking >= 0) {
        AppPicker(
            onPick = { spec ->
                when (picking) {
                    4 -> prefs.peakApp = spec
                    5 -> prefs.weatherApp = spec
                    6 -> prefs.clockApp = spec
                    else -> prefs.setTile(picking, spec, DEFAULT_TILES)
                }
                picking = -1; editing = false
            },
            onDismiss = { picking = -1 },
        )
    }
}

@Composable
fun PeakWidget(prefs: Prefs, tick: Int, nav: (Screen) -> Unit, editing: Boolean, onEdit: (Int) -> Unit) {
    val ctx = LocalContext.current
    val now = remember(tick) { Date() }
    fun format(pattern: String) = SimpleDateFormat(pattern, Locale.getDefault()).format(now).lowercase()
    fun openShortcut(key: String, index: Int) {
        val native = NATIVE[key]
        val system = SYSTEM[key]
        when {
            editing || key.isEmpty() -> onEdit(index)
            native != null -> if (native.screen != Screen.Hub || prefs.notifierEnabled) nav(native.screen)
            system != null -> Apps.launchAction(ctx, system.third)
            else -> Apps.launch(ctx, key)
        }
    }
    val editModifier = if (editing) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
        .padding(horizontal = 6.dp, vertical = 2.dp) else Modifier
    val dateModifier = editModifier.clickable { openShortcut(prefs.peakApp, 4) }
    val clockModifier = editModifier.clickable { openShortcut(prefs.clockApp, 6) }
    val weatherModifier = editModifier.clickable { openShortcut(prefs.weatherApp, 5) }
    val battery = remember(tick) {
        (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    var weather by remember(prefs.showWeather, prefs.weatherLabel) {
        mutableStateOf(if (prefs.showWeather) prefs.weatherCache() else null)
    }
    LaunchedEffect(prefs.showWeather, prefs.weatherLatitude, prefs.weatherLongitude, tick) {
        weather = if (prefs.showWeather) Weather.current(prefs) else null
    }
    val alignment = if (prefs.peakRight) Alignment.End else Alignment.Start
    Column(
        Modifier.fillMaxWidth().padding(start = if (prefs.peakRight) 0.dp else Dim2.cutoutLeft),
        horizontalAlignment = alignment,
    ) {
        when (prefs.peakVariant) {
            1 -> {
                val date = "${format("EEE")}, ${format("MMM d")}  ·  "
                Row {
                    if (prefs.showDate) Text(date, dateModifier, style = MaterialTheme.typography.headlineSmall)
                    Text(format("HH:mm"), clockModifier, style = MaterialTheme.typography.headlineSmall, color = Accent)
                }
            }
            2 -> {
                Text(format("HH:mm"), clockModifier, style = MaterialTheme.typography.headlineSmall, color = Accent)
                if (prefs.showDate) Text("${format("EEE")}, ${format("MMM d")}", dateModifier, style = MaterialTheme.typography.bodyMedium, color = Dim)
            }
            else -> {
                if (prefs.showDate) Column(dateModifier) {
                    Text("${format("EEE")},", style = MaterialTheme.typography.headlineSmall)
                    Text(format("MMM d"), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (prefs.showWeather) {
                Text(weather?.let { weatherText(it.apparentTemperatureC, it.weatherCode, it.isDay) } ?: "—",
                    weatherModifier,
                    style = MaterialTheme.typography.bodySmall)
                if (prefs.showBattery) Text("  ·  ", style = MaterialTheme.typography.bodySmall, color = DotIdle)
            }
            if (prefs.showBattery) {
                Icon(Icons.Outlined.BatteryStd, null, Modifier.size(13.dp), tint = Accent)
                Spacer(Modifier.width(5.dp))
                Text("$battery%", style = MaterialTheme.typography.bodySmall)
            }
            if (prefs.peakVariant == 0) Text("   ${format("HH:mm")}", clockModifier, style = MaterialTheme.typography.bodySmall, color = Accent)
        }
    }
}

@Composable
fun GlanceRows(nav: (Screen) -> Unit, tick: Int, textScale: Int, showAgenda: Boolean = true, showTodo: Boolean = true) {
    val ctx = LocalContext.current
    val event = remember(tick) { runCatching { Agenda.today(ctx).firstOrNull() }.getOrNull() }
    val open = remember(tick) { Store(ctx).tasks().filter { !it.done } }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (showAgenda) GlanceRow(
            Icons.Outlined.CalendarToday,
            event?.let { "${it.title.lowercase()} · ${Agenda.when_(it).substringAfter("· ")}" } ?: "no events today",
            null,
            textScale = textScale,
        ) { nav(Screen.Agenda) }
        if (showTodo) GlanceRow(
            Icons.AutoMirrored.Outlined.FormatListBulleted,
            open.firstOrNull()?.text ?: "no open tasks",
            if (open.size > 1) open.size else null,
            textScale = textScale,
        ) { nav(Screen.Todo) }
    }
}

@Composable
private fun GlanceRow(icon: ImageVector, text: String, badge: Int?, textScale: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(57.dp).clip(RoundedCornerShape(9.dp))
            .background(Black).border(1.dp, White, RoundedCornerShape(9.dp))
            .clickable { onClick() }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = Accent)
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (badge != null) {
            val badgeSize = (19 * textScale / 100f).dp
            Box(Modifier.size(badgeSize).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
                Text("$badge", style = MaterialTheme.typography.labelSmall, color = Black)
            }
        }
    }
}

private data class Native(val screen: Screen, val label: String, val icon: ImageVector)

/** Route a shortcut spec the same way Home tiles do: native screen, system action, or app launch. */
fun launchShortcut(ctx: Context, spec: String, nav: (Screen) -> Unit) {
    NATIVE[spec]?.let { nav(it.screen); return }
    SYSTEM[spec]?.let { Apps.launchAction(ctx, it.third); return }
    Apps.launch(ctx, spec)
}

/** Human label for a shortcut spec (built-in name or installed app label), for Settings display. */
fun shortcutLabel(ctx: Context, spec: String): String =
    NATIVE[spec]?.label ?: SYSTEM[spec]?.first ?: Apps.fromSpec(ctx, spec)?.label ?: spec

private val NATIVE = mapOf(
    "promptpad:notes" to Native(Screen.Notes, "Note", Icons.Outlined.Description),
    "promptpad:agenda" to Native(Screen.Agenda, "Event", Icons.Outlined.CalendarToday),
    "promptpad:todo" to Native(Screen.Todo, "To Do", Icons.AutoMirrored.Outlined.FormatListBulleted),
    "promptpad:hub" to Native(Screen.Hub, "Notifier", Icons.Outlined.Inbox),
    "promptpad:settings" to Native(Screen.Settings, "Settings", Icons.Outlined.Tune),
)

// Note and To Do deliberately keep their PromptPad icons.
private val NATIVE_KATAPULT_ICONS = mapOf(
    "promptpad:agenda" to R.drawable.calendar,
    "promptpad:hub" to R.drawable.home,
    "promptpad:settings" to R.drawable.settings,
)

val DEFAULT_TILES = listOf("promptpad:notes", "promptpad:agenda", "promptpad:clock", "promptpad:todo")

internal val SYSTEM = mapOf(
    "promptpad:calendar" to Triple("Calendar", Icons.Outlined.CalendarToday, "android.intent.action.MAIN|android.intent.category.APP_CALENDAR"),
    "promptpad:clock" to Triple("Clock", Icons.Outlined.Timer, "android.intent.action.SHOW_ALARMS"),
    "promptpad:phone" to Triple("Call", Icons.Outlined.Call, "android.intent.action.DIAL"),
    "promptpad:sms" to Triple("Message", Icons.Outlined.ChatBubbleOutline, "android.intent.action.MAIN|android.intent.category.APP_MESSAGING"),
    "promptpad:camera" to Triple("Camera", Icons.Outlined.PhotoCamera, "android.media.action.STILL_IMAGE_CAMERA"),
)

private val SYSTEM_KATAPULT_ICONS = mapOf(
    "promptpad:calendar" to R.drawable.calendar,
    "promptpad:clock" to R.drawable.clock,
    "promptpad:phone" to R.drawable.phone,
    "promptpad:sms" to R.drawable.sms,
    "promptpad:camera" to R.drawable.camera,
)

@Composable
fun AppGrid(prefs: Prefs, editing: Boolean, nav: (Screen) -> Unit, onEdit: (Int) -> Unit) {
    val ctx = LocalContext.current
    val tiles = prefs.tiles.ifEmpty { DEFAULT_TILES }.take(4)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        tiles.forEachIndexed { index, key ->
            val native = NATIVE[key]
            val system = SYSTEM[key]
            val app = remember(key) { if (native == null && system == null) Apps.fromSpec(ctx, key) else null }
            Shortcut(
                label = native?.label ?: system?.first ?: app?.label ?: Apps.label(ctx, key),
                icon = native?.icon ?: system?.second,
                iconRes = NATIVE_KATAPULT_ICONS[key] ?: SYSTEM_KATAPULT_ICONS[key],
                app = app,
                editing = editing,
                modifier = Modifier.weight(1f),
            ) {
                when {
                    editing -> onEdit(index)
                    native != null -> if (native.screen != Screen.Hub || prefs.notifierEnabled) nav(native.screen)
                    system != null -> Apps.launchAction(ctx, system.third)
                    app != null -> Apps.launch(ctx, app)
                    else -> Apps.launch(ctx, key)
                }
            }
        }
    }
}

@Composable
private fun Shortcut(
    label: String,
    icon: ImageVector?,
    iconRes: Int?,
    app: AppEntry?,
    editing: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier.clickable { onClick() }.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(19.dp)).background(Black)
                .border(2.dp, if (editing) Accent else White, RoundedCornerShape(19.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (app != null && (app.icon != null || app.bundledIconRes != null)) {
                AppIcon(app.icon, app.bundledIconRes, size = 60)
            } else if (iconRes != null) {
                Icon(painterResource(iconRes), null, Modifier.size(28.dp), tint = White)
            } else if (icon != null) {
                Icon(icon, null, Modifier.size(28.dp), tint = White)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
fun AppPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val apps = remember { Apps.all(ctx) }
    Box(Modifier.fillMaxSize().background(Black.copy(alpha = 0.98f)).clickable { onDismiss() }) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(Dim2.screen)) {
            Header("replace shortcut", back = onDismiss)
            LazyColumn(Modifier.weight(1f)) {
                items((NATIVE.keys + SYSTEM.keys).toList()) { key ->
                    val label = NATIVE[key]?.label ?: SYSTEM[key]!!.first
                    Row48({ onPick(key) }) { Text("$label (built-in)", style = MaterialTheme.typography.bodyMedium, color = Accent) }
                }
                items(apps, key = { it.spec }) { app ->
                    Row48({ onPick(app.spec) }) {
                        AppIcon(app.icon, app.bundledIconRes)
                        Spacer(Modifier.width(12.dp))
                        Text(app.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
