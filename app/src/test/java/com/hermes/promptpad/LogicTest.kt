package com.hermes.promptpad

import android.app.Notification
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun mediaWidgetKeepsPausedSessions() {
        assertTrue(MediaWidget.isPlaying(android.media.session.PlaybackState.STATE_PLAYING))
        // transient states during a skip still count as playing so the widget doesn't flicker away
        assertTrue(MediaWidget.isPlaying(android.media.session.PlaybackState.STATE_BUFFERING))
        assertTrue(MediaWidget.isPlaying(android.media.session.PlaybackState.STATE_CONNECTING))
        assertFalse(MediaWidget.isPlaying(android.media.session.PlaybackState.STATE_PAUSED))
        assertTrue(MediaWidget.isActiveState(android.media.session.PlaybackState.STATE_PAUSED))
        assertFalse(MediaWidget.isActiveState(android.media.session.PlaybackState.STATE_STOPPED))
        assertFalse(MediaWidget.isActiveState(null))
    }

    @Test fun drawerStaysEmptyUntilSearched() {
        val apps = listOf("Telegram", "Settings", "Tetris").map { AppEntry(it, it, it, 0, null) }
        assertEquals(listOf("Telegram", "Tetris"), Apps.search(apps, "te").map { it.label })
        assertTrue(Apps.search(apps, "").isEmpty())
    }

    @Test fun appSpecPreservesProfileIdentity() {
        val app = AppEntry("Mail", "mail.pkg", "mail.Activity", 12, null)
        assertEquals("mail.pkg\tmail.Activity\t12", app.spec)
    }

    @Test fun initialSearchCursorFollowsForwardedKey() {
        val value = initialSearchValue("p")
        assertEquals("p", value.text)
        assertEquals(TextRange(1), value.selection)
    }

    @Test fun notesDoNotOfferJournalsFolder() {
        assertFalse(Store.FOLDERS.contains("Journals"))
        assertEquals("Personal", visibleNoteFolder("Journals"))
    }

    @Test fun katapultIconMappingCoversKnownApps() {
        assertEquals(R.drawable.whatsapp, Apps.bundledIconForPackage("com.whatsapp"))
        assertEquals(R.drawable.mail, Apps.bundledIconForPackage("com.google.android.gm"))
        assertEquals(R.drawable.phone, Apps.bundledIconForPackage("com.android.dialer"))
        assertEquals(R.drawable.google, Apps.bundledIconForPackage("com.android.vending"))
        assertEquals(R.drawable.money, Apps.bundledIconForPackage("com.paypal.android.p2pmobile"))
        assertEquals(R.drawable.keyboard, Apps.bundledIconForPackage("it.palsoftware.pastiera"))
    }

    @Test fun workProfileBundledIconsKeepPersonalIconGeometry() {
        assertEquals(IconSize(28, 21), fitBundledIcon(40, 30, 44))
        assertEquals(IconSize(28, 28), fitBundledIcon(42, 42, 44))
    }

    @Test fun notificationGroupSummariesAreNotAddedToHub() {
        assertFalse(HubListener.shouldInclude(Notification.FLAG_GROUP_SUMMARY))
        assertTrue(HubListener.shouldInclude(0))
    }

    @Test fun gmailAndWhatsappDismissalIsPerNotification() {
        listOf("com.google.android.gm", "com.whatsapp", "com.whatsapp.w4b").forEach { pkg ->
            assertTrue(HubListener.dismissesIndividually(pkg))
            assertFalse(HubListener.shouldDismissKey("key", pkg, true, "group", "other", "group"))
            assertTrue(HubListener.shouldDismissKey("key", pkg, true, "group", "key", "group"))
        }
    }

    @Test fun otherGroupedAppsStillClearTheirLiveGroup() {
        assertTrue(HubListener.shouldDismissKey("key", "org.telegram.messenger", true, "chat", "other", "chat"))
        assertFalse(HubListener.shouldDismissKey("key", "org.telegram.messenger", false, "chat", "other", "chat"))
    }

    @Test fun dismissRemovesAStarredNotificationFromNotifier() {
        HubListener.items.clear()
        HubListener.items += HubItem("starred", "pkg", "title", "", 0, HubKind.OTHER, null, null, starred = true)
        assertTrue(HubListener.dismiss("starred"))
        assertTrue(HubListener.items.isEmpty())
    }

    @Test fun repliedMessageKeepsTheOriginalSenderTitle() {
        assertEquals("Dave", HubListener.senderTitle("Dave", "You"))
        assertEquals("Dave", HubListener.senderTitle("Dave", "Dave"))
        assertEquals("You", HubListener.senderTitle(null, "You"))
    }

    @Test fun hubFiltersInterchangeCallsAndAllAndDropEmail() {
        assertEquals(listOf("Calls", "Messages", "All", "Starred"), HUB_FILTERS)
        assertEquals("All", HUB_FILTERS[2]) // default selection
    }

    @Test fun whatsappAndTelegramAreMessages() {
        assertTrue(HubListener.isMessagingPackage("com.whatsapp"))
        assertTrue(HubListener.isMessagingPackage("org.telegram.messenger"))
        assertFalse(HubListener.isMessagingPackage("com.android.settings"))
    }

    @Test fun completedTodosAreRetainedUntilCleared() {
        val tasks = listOf(Task(1, "one", false), Task(2, "two", true))
        assertEquals(listOf(false, false), toggleTask(tasks, 2).map { it.done })
        assertEquals(listOf(1L), clearDone(tasks).map { it.id })
    }

    @Test fun inlineMarkersRenderAsSpans() {
        val text = inline("plain **bold** and _it_")
        assertEquals("plain bold and it", text.text)
        assertTrue(text.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
        assertTrue(text.spanStyles.any { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic })
    }

    @Test fun listMarkerIsInsertedAtCursorOnANewLine() {
        val result = insertListMarker(androidx.compose.ui.text.input.TextFieldValue("beforeafter", TextRange(6)), "-")
        assertEquals("before\n- after", result.text)
        assertEquals(TextRange(9), result.selection)
    }

    @Test fun listMarkerReplacesSelectionAndLeavesCursorAfterMarker() {
        val result = insertListMarker(androidx.compose.ui.text.input.TextFieldValue("beforeafter", TextRange(6, 11)), "[]")
        assertEquals("before\n[] ", result.text)
        assertEquals(TextRange(10), result.selection)
    }

    @Test fun existingNoteOpensAtTheTop() {
        assertEquals(TextRange.Zero, initialNoteBodyValue("first\nsecond").selection)
    }

    @Test fun openMeteoWeatherCodesAndRefreshRulesAreLocalAndDeterministic() {
        assertEquals("☀️", weatherEmoji("0", true))
        assertEquals("🌙", weatherEmoji("0", false))
        assertEquals("⛅", weatherEmoji("2", true))
        assertEquals("🌙☁️", weatherEmoji("2", false))
        assertEquals("🌧️", weatherEmoji("63", false))
        assertEquals("❄️", weatherEmoji("75", false))
        assertEquals("⛈️", weatherEmoji("95", false))
        assertEquals("🌙☁️ 25°C", weatherText(24.6, "2", false))
        val weather = openMeteoCache(17.6, 63, false, 100)
        assertEquals(17.6, weather.apparentTemperatureC, 0.0)
        assertEquals("63", weather.weatherCode)
        assertFalse(weather.isDay)
        assertEquals(100 + 30 * 60_000L, weather.expiresAt)
        assertFalse(weatherRefreshRequired(false, true, false, 0, 10))
        assertTrue(weatherRefreshRequired(true, true, false, 0, 10))
        assertFalse(weatherRefreshRequired(true, true, true, 20, 10))
        assertTrue(weatherRefreshRequired(true, true, true, 10, 10))
    }

    @Test fun noteShareTextIncludesTitleWhenPresent() {
        assertEquals("Title\n\nBody", noteShareText(Note(1, "Personal", "Title", "Body")))
        assertEquals("Body", noteShareText(Note(1, "Personal", "", "Body")))
    }
}
