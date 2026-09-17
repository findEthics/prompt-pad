package com.hermes.promptpad

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class ListNoteTest {
    @Test fun reorderPreservesRowsAndRespectsBounds() {
        val rows = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), moveItem(rows, 0, 2))
        assertEquals(rows, moveItem(rows, -1, 2))
        assertEquals(rows, moveItem(rows, 0, 3))
        assertEquals(rows, moveItem(rows, 1, 1))
    }

    @Test fun viewportTracksParagraphAcrossDifferentLineHeights() {
        val edit = listOf(0, 20, 40, 100)
        val preview = listOf(0, 30, 78, 138)
        assertEquals(2 to 12, noteScrollAnchor(edit, 52))
        assertEquals(90, noteScrollOffset(preview, 2 to 12))
        assertEquals(52, noteScrollOffset(edit, noteScrollAnchor(preview, 90)))
        assertEquals(0, noteScrollOffset(emptyList(), 0 to 0))
        assertEquals(137, noteScrollOffset(preview, 2 to 500))
    }

    @Test fun codeCommandPlacesCursorBetweenTicks() {
        assertEquals(TextFieldValue("a``b", TextRange(2)),
            insertCodeTicks(TextFieldValue("ab", TextRange(1))))
    }

    @Test fun boldCommandInsertsMarkersWithCursorInside() {
        assertEquals(TextFieldValue("a****b", TextRange(3)),
            insertAtCursor(TextFieldValue("ab", TextRange(1)), "****", 2))
    }

    @Test fun headingCommandInsertsMarkerWithCursorAfterHash() {
        assertEquals(TextFieldValue("a\n# b", TextRange(4)),
            insertAtCursor(TextFieldValue("ab", TextRange(1)), "\n# ", 3))
    }
}
