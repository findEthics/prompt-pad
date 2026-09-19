package com.hermes.promptpad

import android.content.Context
import android.content.SharedPreferences

// ponytail: SharedPreferences fits this tiny, single-process launcher.
class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("promptpad", Context.MODE_PRIVATE)

    var showWeather: Boolean by BoolPref(p, "showWeather", false)
    var showBattery: Boolean by BoolPref(p, "showBattery", true)
    var showDate: Boolean by BoolPref(p, "showDate", true)
    var showAgenda: Boolean by BoolPref(p, "showAgenda", true)
    var showTodo: Boolean by BoolPref(p, "showTodo", true)
    var notifierAsHome: Boolean by BoolPref(p, "notifierAsHome", false)
    var instructionsSeenVer: Int by IntPref(p, "instructionsSeenVer", 0)
    var peakRight: Boolean by BoolPref(p, "peakRight", false)
    var peakVariant: Int by IntPref(p, "peakVariant", 0)
    var textScale: Int by IntPref(p, "textScale", 100)
    var tapToSleep: Boolean by BoolPref(p, "tapToSleep", false)
    var hideStatusBar: Boolean by BoolPref(p, "hideStatusBar", false)
    var notifierEnabled: Boolean by BoolPref(p, "notifierEnabled", true)
    var peakApp: String
        get() = p.getString("peakApp", "promptpad:calendar")!!
        set(value) = p.edit().putString("peakApp", value).apply()
    var weatherApp: String
        get() = p.getString("weatherApp", "").orEmpty()
        set(value) = p.edit().putString("weatherApp", value).apply()
    var clockApp: String
        get() = p.getString("clockApp", "promptpad:clock")!!
        set(value) = p.edit().putString("clockApp", value).apply()

    var notifierLeftApp: String
        get() = p.getString("notifierLeftApp", "").orEmpty()
        set(value) = p.edit().putString("notifierLeftApp", value).apply()

    val weatherLabel: String get() = p.getString("weatherLabel", "").orEmpty()
    val weatherLatitude: Double? get() = p.getString("weatherLatitude", null)?.toDoubleOrNull()
    val weatherLongitude: Double? get() = p.getString("weatherLongitude", null)?.toDoubleOrNull()
    val hasWeatherLocation: Boolean get() = weatherLatitude != null && weatherLongitude != null

    fun setWeatherLocation(location: WeatherLocation) {
        p.edit()
            .putString("weatherLabel", location.label)
            .putString("weatherLatitude", location.latitude.toString())
            .putString("weatherLongitude", location.longitude.toString())
            .remove("weatherTemperature").remove("weatherSymbol").remove("weatherFetchedAt")
            .remove("weatherExpiresAt").remove("weatherLastModified")
            .apply()
    }

    fun weatherCache(): WeatherCache? {
        val temperature = p.getString("weatherTemperature", null)?.toDoubleOrNull() ?: return null
        val symbol = p.getString("weatherSymbol", null) ?: return null
        return WeatherCache(
            temperature, symbol, p.getLong("weatherFetchedAt", 0), p.getLong("weatherExpiresAt", 0),
            p.getString("weatherLastModified", "").orEmpty(),
        )
    }

    fun saveWeatherCache(cache: WeatherCache) {
        p.edit().putString("weatherTemperature", cache.temperatureC.toString())
            .putString("weatherSymbol", cache.symbolCode)
            .putLong("weatherFetchedAt", cache.fetchedAt).putLong("weatherExpiresAt", cache.expiresAt)
            .putString("weatherLastModified", cache.lastModified).apply()
    }

    var tiles: List<String>
        get() = p.getString("tiles", "")!!.split(",").filter { it.isNotBlank() }.take(4)
        set(value) = p.edit().putString("tiles", value.take(4).joinToString(",")).apply()

    /** key char (lowercase) -> package or profile-aware app spec. */
    var keyMap: Map<String, String>
        get() = p.getString("keymap", "")!!.split(";").filter { it.contains("=") }
            .associate { val (k, v) = it.split("=", limit = 2); k to v }
        set(value) = p.edit().putString("keymap", value.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()

    fun setTile(index: Int, spec: String, defaults: List<String>) {
        val current = tiles.ifEmpty { defaults.take(4) }.toMutableList()
        while (current.size < 4) current += defaults[current.size]
        current[index] = spec
        tiles = current
    }
}

private class BoolPref(val p: SharedPreferences, val key: String, val default: Boolean) {
    operator fun getValue(target: Any?, property: Any?) = p.getBoolean(key, default)
    operator fun setValue(target: Any?, property: Any?, value: Boolean) = p.edit().putBoolean(key, value).apply()
}

private class IntPref(val p: SharedPreferences, val key: String, val default: Int) {
    operator fun getValue(target: Any?, property: Any?) = p.getInt(key, default)
    operator fun setValue(target: Any?, property: Any?, value: Int) = p.edit().putInt(key, value).apply()
}
