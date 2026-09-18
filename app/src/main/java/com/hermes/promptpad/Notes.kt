package com.hermes.promptpad

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun NotesScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var notes by remember { mutableStateOf(store.notes()) }
    var folder by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<Long?>(null) }

    fun persist(list: List<Note>) { notes = list; store.saveNotes(list) }

    val current = notes.firstOrNull { it.id == open }
    if (current != null) {
        BackHandler { open = null }
        var confirmDelete by remember { mutableStateOf(false) }
        NoteEditor(current, onChange = { store.saveNotes(notes) }, onDelete = { confirmDelete = true })
        if (confirmDelete) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete note?") },
            text = { Text("This cannot be undone.") },
            confirmButton = { TextButton(onClick = { persist(notes.filterNot { it.id == current.id }); open = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }

    var reordering by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val visible = notes.filter { it.folder == Store.FOLDERS[folder] }
    EdgeScreen("notes") {
        LazyColumn(Modifier.weight(1f).orangeScrollbar(listState).padding(end = 6.dp),
            state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visible, key = { it.id }) { n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                Card(Modifier.weight(1f), onClick = { open = n.id }) {
                    Text(n.title.ifBlank { "(untitled)" }, style = MaterialTheme.typography.titleMedium)
                    if (n.body.isNotBlank()) Text(n.body.lineSequence().first().take(60),
                        style = MaterialTheme.typography.bodySmall, color = Dim)
                }
                if (reordering) {
                    val index = visible.indexOf(n)
                    fun move(to: Int) = persist(moveItem(notes, notes.indexOf(n), notes.indexOf(visible[to])))
                    ReorderButtons(
                        up = if (index > 0) ({ move(index - 1) }) else null,
                        down = if (index < visible.lastIndex) ({ move(index + 1) }) else null,
                    )
                }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("+ new note", Modifier.weight(1f).heightIn(min = Dim2.touch).clickable {
                val n = Note(System.currentTimeMillis(), Store.FOLDERS[folder], "", "")
                persist(notes + n); open = n.id
            }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = Accent)
            Text(if (reordering) "done" else "reorder", Modifier.heightIn(min = Dim2.touch)
                .clickable { reordering = !reordering }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyMedium, color = Accent)
        }
        Spacer(Modifier.height(Dim2.gap))
        Tabs(Store.FOLDERS, folder) { folder = it }
    }
}

/**
 * Rich text is stored as lightweight markdown markers (**b**, _i_, __u__, # h, - bullet, [] checkbox)
 * ponytail: markers over a span model — round-trips through plain-text storage for free.
 */
@Composable
fun NoteEditor(note: Note, onChange: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(initialNoteBodyValue(note.body)) }
    var preview by remember(note.id) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var editorTops by remember { mutableStateOf(listOf(0)) }
    val previewTops = remember { mutableStateMapOf<Int, Int>() }
    var restore by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(note.id, preview) {
        if (!preview) {
            (if (note.title.isEmpty() && note.body.isEmpty()) titleFocus else bodyFocus).requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(preview, editorTops, previewTops.toMap()) {
        val tops = if (preview) previewTops.toSortedMap().values.toList() else editorTops
        val anchor = restore
        if (anchor != null && tops.size == body.text.lines().size) {
            withFrameNanos { }
            scroll.scrollTo(noteScrollOffset(tops, anchor))
            restore = null
        }
    }

    fun update(value: TextFieldValue) { body = value; note.body = value.text; onChange() }
    fun share() = ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, noteShareText(note))
    }, "Sharing text"))

    EdgeScreen(title, heading = {
        BasicTextField(title, { title = it; note.title = it; onChange() },
            Modifier.fillMaxWidth().heightIn(min = Dim2.touch).focusRequester(titleFocus)
                .semantics { contentDescription = "Note title" },
            singleLine = true, cursorBrush = SolidColor(Accent),
            textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
            decorationBox = { field ->
                if (title.isEmpty()) Text("title", Modifier.fillMaxWidth(), color = DotIdle,
                    style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                field()
            })
    }) {
        Box(Modifier.fillMaxWidth().weight(1f).orangeScrollbar(scroll)) {
            if (preview) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 6.dp)) {
                    body.text.lines().forEachIndexed { index, line ->
                        RichLine(line, Modifier.onGloballyPositioned {
                            previewTops[index] = it.positionInParent().y.roundToInt()
                        }) { done ->
                            val lines = body.text.lines().toMutableList()
                            lines[index] = toggleCheck(line, done)
                            update(body.copy(text = lines.joinToString("\n")))
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 6.dp)) {
                    BasicTextField(body, ::update,
                        Modifier.fillMaxWidth().focusRequester(bodyFocus).semantics { contentDescription = "Note body" },
                        textStyle = MaterialTheme.typography.bodyMedium, cursorBrush = SolidColor(Accent),
                        onTextLayout = { layout ->
                            var offset = 0
                            editorTops = body.text.lines().map { line ->
                                layout.getLineTop(layout.getLineForOffset(offset.coerceAtMost(body.text.length)))
                                    .roundToInt().also { offset += line.length + 1 }
                            }
                        },
                        decorationBox = { field ->
                            if (body.text.isEmpty()) Text("write…", color = DotIdle, style = MaterialTheme.typography.bodyMedium)
                            field()
                        })
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = Dim2.touch), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("H" to ("\n# " to 3), "B" to ("****" to 2)).forEach { (l, spec) ->
                Text(l, Modifier.weight(1f).clickable { update(insertAtCursor(body, spec.first, spec.second)) }.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = if (preview) DotIdle else White,
                    textAlign = TextAlign.Center)
            }
            Text("<>", Modifier.weight(1f).clickable { update(insertCodeTicks(body)) }.padding(8.dp),
                style = MaterialTheme.typography.bodyMedium, color = if (preview) DotIdle else White,
                textAlign = TextAlign.Center)
            listOf("•" to "-", "☐" to "[]").forEach { (label, marker) ->
                Text(label, Modifier.weight(1f).clickable { update(insertListMarker(body, marker)) }.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium, color = if (preview) DotIdle else White,
                    textAlign = TextAlign.Center)
            }
            Text(if (preview) "edit" else "view", Modifier.weight(1f).clickable {
                restore = noteScrollAnchor(if (preview) previewTops.toSortedMap().values.toList() else editorTops, scroll.value)
                previewTops.clear()
                preview = !preview
            }.padding(8.dp),
                style = MaterialTheme.typography.bodySmall, color = Accent, textAlign = TextAlign.Center)
            IconButton(::share, Modifier.weight(1f)) { Icon(Icons.Outlined.Share, "Share note", tint = Accent) }
            IconButton(onDelete, Modifier.weight(1f)) { Icon(Icons.Outlined.Delete, "Delete note", tint = Accent) }
        }
    }
}

fun noteScrollAnchor(tops: List<Int>, scroll: Int): Pair<Int, Int> {
    val line = tops.indexOfLast { it <= scroll }.coerceAtLeast(0)
    return line to (scroll - tops.getOrElse(line) { 0 }).coerceAtLeast(0)
}

fun noteScrollOffset(tops: List<Int>, anchor: Pair<Int, Int>): Int {
    if (tops.isEmpty()) return 0
    val line = anchor.first.coerceIn(tops.indices)
    val extra = tops.getOrNull(line + 1)?.let { (it - tops[line] - 1).coerceAtLeast(0) } ?: Int.MAX_VALUE
    return tops[line] + anchor.second.coerceIn(0, extra)
}

fun initialNoteBodyValue(text: String) = TextFieldValue(text, TextRange.Zero)

fun insertListMarker(value: TextFieldValue, marker: String): TextFieldValue {
    val inserted = "\n$marker "
    val text = value.text.replaceRange(value.selection.min, value.selection.max, inserted)
    return TextFieldValue(text, TextRange(value.selection.min + inserted.length))
}

fun insertCodeTicks(value: TextFieldValue): TextFieldValue = insertAtCursor(value, "``", 1)

/** Inserts [insert] at the cursor (replacing any selection), placing the cursor [cursorOffset] chars in. */
fun insertAtCursor(value: TextFieldValue, insert: String, cursorOffset: Int): TextFieldValue {
    val at = value.selection.min
    return TextFieldValue(value.text.replaceRange(at, value.selection.max, insert), TextRange(at + cursorOffset))
}

fun noteShareText(note: Note) = listOf(note.title, note.body).filter(String::isNotBlank).joinToString("\n\n")

private fun toggleCheck(line: String, done: Boolean) = when {
    done -> line.replaceFirst("[] ", "[x] ")
    else -> line.replaceFirst("[x] ", "[] ")
}

@Composable
private fun RichLine(line: String, modifier: Modifier = Modifier, onCheck: (Boolean) -> Unit) {
    val checked = line.startsWith("[x] ")
    val isCheck = checked || line.startsWith("[] ")
    val text = line.removePrefix("[x] ").removePrefix("[] ").removePrefix("- ").removePrefix("# ")
    Row(modifier.fillMaxWidth().heightIn(min = if (isCheck) Dim2.touch else 0.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (isCheck) Text(if (checked) "☑" else "☐", Modifier.clickable { onCheck(!checked) }.padding(end = 8.dp),
            style = MaterialTheme.typography.bodyMedium, color = Accent)
        else if (line.startsWith("- ")) Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(inline(text, strike = checked),
            style = if (line.startsWith("# ")) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyMedium)
    }
}

/** Renders **bold**, _italic_, __underline__ inside one line. */
fun inline(s: String, strike: Boolean = false) = buildAnnotatedString {
    val re = Regex("\\*\\*(.+?)\\*\\*|__(.+?)__|_(.+?)_")
    var i = 0
    val base = if (strike) SpanStyle(textDecoration = TextDecoration.LineThrough) else SpanStyle()
    re.findAll(s).forEach { m ->
        withStyle(base) { append(s.substring(i, m.range.first)) }
        val style = when {
            m.groupValues[1].isNotEmpty() -> base.copy(fontWeight = FontWeight.Bold)
            m.groupValues[2].isNotEmpty() -> base.copy(textDecoration = TextDecoration.Underline)
            else -> base.copy(fontStyle = FontStyle.Italic)
        }
        withStyle(style) { append(m.groupValues.drop(1).first { it.isNotEmpty() }) }
        i = m.range.last + 1
    }
    withStyle(base) { append(s.substring(i)) }
}
