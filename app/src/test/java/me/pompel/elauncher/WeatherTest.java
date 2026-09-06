package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WeatherTest {
    @Test
    public void rendersMappedSymbolsAndRoundedTemperature() {
        assertEquals("☀️ 25°C", Weather.render(24.6, "clearsky_day"));
        assertEquals("🌙 -1°C", Weather.render(-1.4, "fair_night"));
        assertEquals("⛅ 0°C", Weather.render(0.2, "partlycloudy_day"));
        assertEquals("☁️ 3°C", Weather.render(3.0, "cloudy"));
        assertEquals("🌫️ 3°C", Weather.render(3.0, "fog"));
        assertEquals("🌧️ 3°C", Weather.render(3.0, "heavyrain"));
        assertEquals("❄️ 3°C", Weather.render(3.0, "lightsnowshowers_day"));
        assertEquals("⛈️ 3°C", Weather.render(3.0, "thunderstormrain"));
        assertEquals("? 3°C", Weather.render(3.0, "unknown"));
    }

    @Test
    public void parsesManualLocationAndRoundsCoordinatesForRequests() {
        Weather.Location location = Weather.Location.parse("Berlin, 52.520008, 13.404954");

        assertEquals("Berlin", location.label);
        assertEquals("52.52", location.latitudeForRequest());
        assertEquals("13.405", location.longitudeForRequest());
        assertNull(Weather.Location.parse("Berlin, 91, 13"));
        assertNull(Weather.Location.parse("Berlin, 52"));
    }

    @Test
    public void refreshesOnlyForMissingOrExpiredCache() {
        assertTrue(Weather.shouldFetch(false, 0L, 100L));
        assertFalse(Weather.shouldFetch(true, 101L, 100L));
        assertTrue(Weather.shouldFetch(true, 100L, 100L));
    }
}
