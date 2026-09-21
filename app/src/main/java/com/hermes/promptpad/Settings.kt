package com.hermes.promptpad

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal fun textScaleIndex(scale: Int): Int = listOf(90, 100, 115, 130).indexOf(scale).let { if (it < 0) 1 else it }

@Composable
fun SettingsScreen(
    prefs: Prefs,
    back: () -> Unit,
    openAccessibility: () -> Unit,
    applyStatusBar: () -> Unit,
    onPreferencesChanged: () -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    var pickingLeftApp by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    fun update(block: () -> Unit) { block(); version++; onPreferencesChanged() }

    Column(
        Modifier.fillMaxSize().background(Black).safeDrawingPadding()
            .padding(horizontal = Dim2.screen).verticalScroll(rememberScrollState()),
    ) {
        Header("settings", back)
        key(version) {
            Section("appearance")
            Toggle("battery in peak widget", prefs.showBattery) { update { prefs.showBattery = it } }
            Toggle("date in peak widget", prefs.showDate) { update { prefs.showDate = it } }
            Toggle("weather in peak widget", prefs.showWeather) { update { prefs.showWeather = it } }
            if (prefs.showWeather) WeatherLocationSetting(prefs) { update {} }
            Toggle("agenda on home", prefs.showAgenda) { update { prefs.showAgenda = it } }
            Toggle("to-do on home", prefs.showTodo) { update { prefs.showTodo = it } }
            Toggle("open notifier as home", prefs.notifierAsHome) { update { prefs.notifierAsHome = it } }
            NotifierLeftAppSetting(prefs) { pickingLeftApp = true }
            Toggle("peak widget right-aligned", prefs.peakRight) { update { prefs.peakRight = it } }
            Choice("peak variant", listOf("time+date", "one line", "stacked"), prefs.peakVariant) { update { prefs.peakVariant = it } }
            Choice(
                "text size",
                listOf("90%", "100%", "115%", "130%"),
                textScaleIndex(prefs.textScale),
            ) { update { prefs.textScale = listOf(90, 100, 115, 130)[it] } }
            Section("gestures and system bars")
            Toggle("notifier", prefs.notifierEnabled) { update { prefs.notifierEnabled = it } }
            Toggle("tap blank area twice to sleep", prefs.tapToSleep) { enabled ->
                update { prefs.tapToSleep = enabled }
                if (enabled) openAccessibility()
            }
            Text(
                "Enable the Prompt-Pad tap-to-sleep service in Accessibility.",
                Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
            Toggle("hide status bar", prefs.hideStatusBar) { hidden ->
                update { prefs.hideStatusBar = hidden }
                applyStatusBar()
            }
            Text(
                "Swipe from the top edge to reveal it temporarily.",
                Modifier.padding(bottom = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Dim,
            )
            Text("Weather data: Open-Meteo",
                Modifier.clickable { uriHandler.openUri("https://open-meteo.com") },
                style = MaterialTheme.typography.labelSmall, color = DotIdle)
            Text("Forecast adapted for compact display; refreshes every 30 minutes.",
                Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.labelSmall, color = DotIdle)
            Text("prompt-pad ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                Modifier.padding(bottom = 12.dp),
                style = MaterialTheme.typography.labelSmall, color = DotIdle)
        }
    }
    if (pickingLeftApp) AppPicker(
        onPick = { prefs.notifierLeftApp = it; pickingLeftApp = false; update {} },
        onDismiss = { pickingLeftApp = false },
    )
}

@Composable
private fun WeatherLocationSetting(prefs: Prefs, onChanged: () -> Unit) {
    var query by remember { mutableStateOf(prefs.weatherLabel) }
    var results by remember { mutableStateOf(emptyList<WeatherLocation>()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    Text("weather location", style = MaterialTheme.typography.bodySmall, color = Dim)
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextField(
            query, { query = it }, Modifier.weight(1f), singleLine = true,
            placeholder = { Text("city", color = DotIdle) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (prefs.hasWeatherLocation && query == prefs.weatherLabel) Accent else White,
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Black, unfocusedContainerColor = Black,
                cursorColor = Accent, focusedIndicatorColor = Accent, unfocusedIndicatorColor = DotIdle,
            ),
        )
        Text(if (searching) "…" else "search", Modifier.padding(start = 10.dp)
            .clickable(enabled = !searching) {
                if (query.isNotBlank()) scope.launch {
                    searching = true
                    results = Weather.searchLocations(query)
                    searching = false
                }
            }, style = MaterialTheme.typography.bodyMedium, color = Accent)
    }
    if (results.isNotEmpty()) Text(
        "Location data: Open-Meteo / GeoNames (CC BY 4.0); labels adapted for display.",
        Modifier.clickable { uriHandler.openUri("https://creativecommons.org/licenses/by/4.0/") },
        style = MaterialTheme.typography.labelSmall,
        color = DotIdle,
    )
    results.forEach { location ->
        Row48({
            prefs.setWeatherLocation(location)
            query = location.label
            results = emptyList()
            onChanged()
            scope.launch { Weather.current(prefs) }
        }) { Text(location.label, style = MaterialTheme.typography.bodySmall, color = Accent) }
    }
}

@Composable
private fun NotifierLeftAppSetting(prefs: Prefs, onPick: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val current = if (prefs.notifierLeftApp.isBlank()) "not set" else shortcutLabel(ctx, prefs.notifierLeftApp)
    Row48(onPick) {
        Text("left-swipe app", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(current, style = MaterialTheme.typography.bodySmall, color = Accent)
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(top = 14.dp, bottom = 4.dp), style = MaterialTheme.typography.labelSmall, color = Accent)
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row48({ onChange(!value) }) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(if (value) "on" else "off", style = MaterialTheme.typography.bodySmall, color = if (value) Accent else DotIdle)
    }
}

@Composable
private fun Choice(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Dim)
        Spacer(Modifier.height(4.dp))
        Tabs(options, selected, onSelect)
    }
}
