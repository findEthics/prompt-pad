package me.pompel.elauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class LlmOutputMapperTest {
    @Test
    public void mapsEveryCommandToCanonicalSyntax() {
        assertEquals("!call mom", map("{\"command\":\"call\",\"contact\":\"mom\"}"));
        assertEquals("!text mom I'll be late", map("{\"command\":\"text\",\"contact\":\"mom\",\"body\":\"I'll be late\"}"));
        assertEquals("!hermes on my way", map("{\"command\":\"hermes\",\"body\":\"on my way\"}"));
        assertEquals("!timer 5m", map("{\"command\":\"timer\",\"duration\":\"5m\"}"));
        assertEquals("!alarm 22:00", map("{\"command\":\"alarm\",\"time\":\"22:00\"}"));
        assertEquals("!todo call the plumber", map("{\"command\":\"todo\",\"text\":\"call the plumber\"}"));
        assertEquals("!todos", map("{\"command\":\"todos\"}"));
        assertEquals("!note buy milk", map("{\"command\":\"note\",\"text\":\"buy milk\"}"));
        assertEquals("!notes", map("{\"command\":\"notes\"}"));
        assertEquals("!grocery milk", map("{\"command\":\"grocery\",\"item\":\"milk\"}"));
        assertEquals("!groceries", map("{\"command\":\"groceries\"}"));
        assertEquals("!event tomorrow 15:00 dentist", map(
                "{\"command\":\"event\",\"date\":\"tomorrow\",\"time\":\"15:00\",\"title\":\"dentist\"}"));
        assertEquals("!t", map("{\"command\":\"torch\"}"));
        assertEquals("!camera", map("{\"command\":\"camera\"}"));
    }

    @Test
    public void toleratesProseSingleQuotesAndExtraKeys() {
        assertEquals("!grocery milk", map("Here: {'command':'grocery','item':'milk','extra':'ignored'} done"));
        assertEquals("!text mom I'll be late", map(
                "prefix {\"command\":\"text\",\"contact\":\"mom\",\"body\":\"I'll be late\"} suffix"));
    }

    @Test
    public void rejectsUnsafeOrIncompleteOutput() {
        assertNull(map(null));
        assertNull(map("not json"));
        assertNull(map("{\"command\":\"none\"}"));
        assertNull(map("{\"command\":\"weather\"}"));
        assertNull(map("{\"command\":\"grocery\"}"));
        assertNull(map("{\"command\":\"text\",\"contact\":\"mom\"}"));
        assertNull(map("{\"command\":\"grocery\",\"item\":}"));
    }

    private static String map(String raw) {
        return LlmOutputMapper.toCommandString(raw);
    }
}
