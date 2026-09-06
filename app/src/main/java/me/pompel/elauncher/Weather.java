package me.pompel.elauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

final class Weather {
    static final String ENABLED = "weather_in_peak_widget";
    static final String LOCATION_INPUT = "weather_location_input";
    private static final String LABEL = "weather_location_label";
    private static final String LATITUDE = "weather_location_latitude";
    private static final String LONGITUDE = "weather_location_longitude";
    private static final String TEMPERATURE = "weather_temperature_c";
    private static final String SYMBOL = "weather_symbol_code";
    private static final String FETCHED_AT = "weather_fetched_at";
    private static final String EXPIRES_AT = "weather_expires_at";
    private static final String LAST_MODIFIED = "weather_last_modified";

    private Weather() { }

    static void refresh(Context context, TextView view) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        if (!preferences.getBoolean(ENABLED, false)) {
            view.setVisibility(TextView.GONE);
            return;
        }
        view.setVisibility(TextView.VISIBLE);
        boolean hasCache = preferences.contains(TEMPERATURE) && preferences.contains(SYMBOL);
        if (hasCache) {
            view.setText(render(preferences.getFloat(TEMPERATURE, 0f),
                    preferences.getString(SYMBOL, "")));
        } else {
            view.setText("—");
        }
        Location location = location(preferences);
        if (location == null || !shouldFetch(hasCache,
                preferences.getLong(EXPIRES_AT, 0L), System.currentTimeMillis())) {
            return;
        }
        new Thread(() -> fetch(context.getApplicationContext(), preferences, location, view),
                "weather-refresh").start();
    }

    static boolean shouldFetch(boolean hasCache, long expiresAt, long now) {
        return !hasCache || now >= expiresAt;
    }

    static String render(double temperatureC, String symbolCode) {
        return emoji(symbolCode) + " " + Math.round(temperatureC) + "°C";
    }

    static void saveLocation(SharedPreferences preferences, Location location) {
        Location oldLocation = location(preferences);
        SharedPreferences.Editor editor = preferences.edit()
                .putString(LABEL, location.label)
                .putString(LATITUDE, Double.toString(location.latitude))
                .putString(LONGITUDE, Double.toString(location.longitude));
        if (!location.equals(oldLocation)) {
            clearCache(editor);
        }
        editor.apply();
    }

    static void clearLocation(SharedPreferences preferences) {
        SharedPreferences.Editor editor = preferences.edit()
                .remove(LABEL).remove(LATITUDE).remove(LONGITUDE);
        clearCache(editor);
        editor.apply();
    }

    static final class Location {
        final String label;
        final double latitude;
        final double longitude;

        private Location(String label, double latitude, double longitude) {
            this.label = label;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        static Location parse(String input) {
            if (input == null) return null;
            String[] parts = input.split(",", -1);
            if (parts.length != 3) return null;
            String label = parts[0].trim();
            if (label.isEmpty()) return null;
            try {
                double latitude = Double.parseDouble(parts[1].trim());
                double longitude = Double.parseDouble(parts[2].trim());
                if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                        || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                    return null;
                }
                return new Location(label, latitude, longitude);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        String latitudeForRequest() {
            return coordinate(latitude);
        }

        String longitudeForRequest() {
            return coordinate(longitude);
        }

        String normalizedInput() {
            return label + ", " + latitude + ", " + longitude;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Location)) return false;
            Location that = (Location) other;
            return label.equals(that.label) && Double.compare(latitude, that.latitude) == 0
                    && Double.compare(longitude, that.longitude) == 0;
        }
    }

    private static void fetch(Context context, SharedPreferences preferences, Location requested,
                              TextView view) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat="
                    + requested.latitudeForRequest() + "&lon=" + requested.longitudeForRequest());
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("User-Agent", context.getApplicationInfo()
                    .loadLabel(context.getPackageManager()) + "/" + appVersion(context)
                    + " https://github.com/findEthics/cLauncher");
            String lastModified = preferences.getString(LAST_MODIFIED, null);
            if (lastModified != null && !lastModified.isEmpty()) {
                connection.setRequestProperty("If-Modified-Since", lastModified);
            }
            int responseCode = connection.getResponseCode();
            if (!requested.equals(location(preferences))) return;
            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                updateMetadata(preferences, connection, lastModified);
                return;
            }
            if (responseCode != HttpURLConnection.HTTP_OK) return;

            JSONObject json = new JSONObject(read(connection));
            JSONArray timeseries = json.getJSONObject("properties").getJSONArray("timeseries");
            JSONObject data = timeseries.getJSONObject(0).getJSONObject("data");
            double temperature = data.getJSONObject("instant").getJSONObject("details")
                    .getDouble("air_temperature");
            JSONObject period = data.optJSONObject("next_1_hours");
            if (period == null) period = data.optJSONObject("next_6_hours");
            if (period == null) return;
            String symbol = period.getJSONObject("summary").getString("symbol_code");

            SharedPreferences.Editor editor = preferences.edit()
                    .putFloat(TEMPERATURE, (float) temperature)
                    .putString(SYMBOL, symbol)
                    .putLong(FETCHED_AT, System.currentTimeMillis());
            putMetadata(editor, connection, lastModified);
            editor.apply();
            view.post(() -> {
                if (preferences.getBoolean(ENABLED, false) && requested.equals(location(preferences))) {
                    view.setText(render(temperature, symbol));
                }
            });
        } catch (Exception ignored) {
            // ponytail: cached weather is the fallback; no retry worker is needed.
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void updateMetadata(SharedPreferences preferences, HttpURLConnection connection,
                                       String previousLastModified) {
        SharedPreferences.Editor editor = preferences.edit().putLong(FETCHED_AT, System.currentTimeMillis());
        putMetadata(editor, connection, previousLastModified);
        editor.apply();
    }

    private static void putMetadata(SharedPreferences.Editor editor, HttpURLConnection connection,
                                    String previousLastModified) {
        String lastModified = connection.getHeaderField("Last-Modified");
        editor.putLong(EXPIRES_AT, connection.getHeaderFieldDate("Expires", System.currentTimeMillis()));
        if (lastModified == null || lastModified.isEmpty()) {
            if (previousLastModified == null || previousLastModified.isEmpty()) editor.remove(LAST_MODIFIED);
            else editor.putString(LAST_MODIFIED, previousLastModified);
        } else {
            editor.putString(LAST_MODIFIED, lastModified);
        }
    }

    private static Location location(SharedPreferences preferences) {
        return Location.parse(preferences.getString(LABEL, "") + ", "
                + preferences.getString(LATITUDE, "") + ", "
                + preferences.getString(LONGITUDE, ""));
    }

    private static void clearCache(SharedPreferences.Editor editor) {
        editor.remove(TEMPERATURE).remove(SYMBOL).remove(FETCHED_AT).remove(EXPIRES_AT)
                .remove(LAST_MODIFIED);
    }

    private static String read(HttpURLConnection connection) throws Exception {
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
        }
        return body.toString();
    }

    private static String emoji(String symbolCode) {
        String symbol = symbolCode == null ? "" : symbolCode.toLowerCase(Locale.US);
        if (symbol.startsWith("clearsky") || symbol.startsWith("fair")) {
            return symbol.endsWith("_night") ? "🌙" : "☀️";
        }
        if (symbol.startsWith("partlycloudy")) return "⛅";
        if (symbol.equals("cloudy")) return "☁️";
        if (symbol.equals("fog")) return "🌫️";
        if (symbol.startsWith("thunderstorm")) return "⛈️";
        if (symbol.startsWith("snow") || symbol.startsWith("lightsnow")
                || symbol.startsWith("heavysnow")) return "❄️";
        if (symbol.startsWith("rain") || symbol.startsWith("lightrain")
                || symbol.startsWith("heavyrain") || symbol.startsWith("drizzle")
                || symbol.startsWith("sleet")) return "🌧️";
        return "?";
    }

    private static String appVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private static String coordinate(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return rounded.signum() == 0 ? "0" : rounded.toPlainString();
    }
}
