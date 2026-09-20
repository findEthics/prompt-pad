package com.hermes.promptpad

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

data class WeatherLocation(val label: String, val latitude: Double, val longitude: Double)
data class WeatherCache(
    val apparentTemperatureC: Double,
    val weatherCode: String,
    val isDay: Boolean,
    val fetchedAt: Long,
    val expiresAt: Long,
)

fun weatherEmoji(weatherCode: String, isDay: Boolean): String = when (weatherCode.toIntOrNull()) {
    0 -> if (isDay) "☀️" else "🌙"
    1, 2 -> if (isDay) "⛅" else "🌙☁️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "❄️"
    95, 96, 99 -> "⛈️"
    else -> "?"
}

fun weatherText(apparentTemperatureC: Double, weatherCode: String, isDay: Boolean) =
    "${weatherEmoji(weatherCode, isDay)} ${apparentTemperatureC.roundToInt()}°C"

fun weatherRefreshRequired(enabled: Boolean, hasLocation: Boolean, hasCache: Boolean, expiresAt: Long, now: Long) =
    enabled && hasLocation && (!hasCache || now >= expiresAt)

private const val WEATHER_CACHE_MS = 30 * 60_000L

fun openMeteoCache(apparentTemperatureC: Double, weatherCode: Int, isDay: Boolean, now: Long) =
    WeatherCache(apparentTemperatureC, weatherCode.toString(), isDay, now, now + WEATHER_CACHE_MS)

object Weather {
    private const val TIMEOUT_MS = 10_000

    private data class Response(val code: Int, val body: String)

    suspend fun searchLocations(query: String): List<WeatherLocation> {
        if (query.isBlank()) return emptyList()
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        return try {
            JSONObject(request("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5").body)
                .optJSONArray("results")?.let { results ->
                    List(results.length()) { index ->
                        val item = results.getJSONObject(index)
                        val label = listOf(
                            item.optString("name"), item.optString("admin1"), item.optString("country"),
                        ).filter { it.isNotBlank() }.distinct().joinToString(", ")
                        WeatherLocation(label, item.getDouble("latitude"), item.getDouble("longitude"))
                    }
                } ?: emptyList()
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            emptyList()
        }
    }

    suspend fun current(prefs: Prefs, now: Long = System.currentTimeMillis()): WeatherCache? {
        val cached = prefs.weatherCache()
        if (!weatherRefreshRequired(prefs.showWeather, prefs.hasWeatherLocation, cached != null, cached?.expiresAt ?: 0, now)) {
            return cached
        }
        val latitude = prefs.weatherLatitude ?: return cached
        val longitude = prefs.weatherLongitude ?: return cached
        val result = try {
            fetch(latitude, longitude, now)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            cached
        }
        currentCoroutineContext().ensureActive()
        if (!prefs.showWeather || prefs.weatherLatitude != latitude || prefs.weatherLongitude != longitude) {
            return prefs.weatherCache()
        }
        result?.let(prefs::saveWeatherCache)
        return result
    }

    private suspend fun fetch(latitude: Double, longitude: Double, now: Long): WeatherCache? {
        val lat = String.format(Locale.US, "%.4f", latitude)
        val lon = String.format(Locale.US, "%.4f", longitude)
        val response = request(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day,precipitation" +
                "&hourly=temperature_2m,apparent_temperature,precipitation_probability&timezone=auto",
        )
        if (response.code != HttpURLConnection.HTTP_OK) return null
        val current = JSONObject(response.body).getJSONObject("current")
        return openMeteoCache(
            current.getDouble("apparent_temperature"), current.getInt("weather_code"), current.getInt("is_day") == 1, now,
        )
    }

    private suspend fun request(url: String): Response = withContext(Dispatchers.IO) {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { connection.disconnect() }
            try {
                val code = connection.responseCode
                val body = if (code == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else ""
                if (continuation.isActive) continuation.resume(Response(code, body))
            } catch (error: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(error)
            } finally {
                connection.disconnect()
            }
        }
    }
}
