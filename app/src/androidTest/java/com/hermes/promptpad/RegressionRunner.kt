package com.hermes.promptpad

import android.app.*
import android.content.*
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.graphics.Rect

// ponytail: native Instrumentation + UiAutomation; no test framework dependency.
class RegressionRunner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        try {
            Prefs(targetContext).apply {
                instructionsSeenVer = 2
                notifierEnabled = true
                textScale = 100
                peakRight = true
                peakVariant = 2
                showWeather = true
                hideStatusBar = false
                weatherApp = ""
            }
            val store = Store(targetContext)
            store.saveNotes((0..24).map { Note(it.toLong(), "Personal", "Note $it",
                if (it == 0) (0..69).joinToString("\n") { n -> "Line %03d **bold** and ordinary text".format(n) } else "Body $it") })
            store.saveTasks((0..30).map { Task(it.toLong(), "Task $it", false) })
            targetContext.startActivity(Intent(targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Home visible") { nodes().any { it.text?.toString() == "Note" } }
            context.startActivity(Intent().setClassName(context.packageName, NotificationTarget::class.java.name)
                .putExtra("postFixture", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("notification posted") { HubListener.items.any { it.title == "Regression chat" && it.text == "Original message" } }
            val item = HubListener.items.first { it.title == "Regression chat" }
            runOnMainSync { check(HubListener.sendReply(targetContext, item, "Test reply")) }
            await("publisher reply update") {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand("cmd notification get ${item.key}"))
                    .bufferedReader().use { "Test reply" in it.readText() }
            }
            check(trayContains(item.key)) { "Reply dismissed the system notification" }
            val updated = HubListener.items.firstOrNull { it.key == item.key }
            check(updated != null) { "Reply dismissed the Notifier item" }
            check(updated.title == "Regression chat") { "Reply changed sender to ${updated.title}" }
            check(updated.text.contains("Original message") && updated.replies.contains("Test reply")) { "Original/reply lost: ${updated.text}" }
            runOnMainSync { check(HubListener.open(targetContext, updated)) }
            await("content intent opened") { nodes().any { it.text?.toString() == "Conversation 71" } }
            targetContext.startActivity(Intent(targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Home visible") { nodes().any { it.text?.toString() == "Note" } }
            targetContext.startActivity(Intent(targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Home before dismissal") { nodes().any { it.text?.toString() == "Note" } }
            swipe(true)
            await("reply visible") { nodes().any { it.text?.toString() == "You: Test reply" } }
            val bounds = Rect().also(nodes().first { it.text?.toString() == item.title }::getBoundsInScreen)
            swipe(true, bounds.centerY().toFloat() / targetContext.resources.displayMetrics.heightPixels)
            await("system dismissal") { !trayContains(item.key) && HubListener.items.none { it.key == item.key } }
            check(!trayContains("|72|")) { "Group summary remained in the tray" }
            targetContext.startActivity(Intent(targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Home visible") { nodes().any { it.text?.toString() == "Note" } }
            checkNotesAndTodos()
            checkHome()
            checkPeak()
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "PASS: grouped notification tray dismissal and inline reply sender retention; note title focus/code command/scroll/reorder; todo back/reorder; screen-title and Home status insets; notifier gating/gestures; 90% font; weather configuration\n") })
        } catch (e: Throwable) {
            android.util.Log.e("Regression", nodes().joinToString("\n") { "${it.text?.take(60)} visible=${it.isVisibleToUser} scroll=${it.isScrollable} focus=${it.isFocused} bounds=${Rect().also(it::getBoundsInScreen)}" })
            java.io.FileOutputStream(java.io.File(targetContext.filesDir, "regression-failure.png")).use {
                uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "FAIL: ${e.stackTraceToString()}\n") })
        }
    }
    private fun checkNotesAndTodos() {
        tap("Note")
        checkTitleBelowStatusBar("notes")
        val newNote = bounds("+ new note")
        val noteReorder = bounds("reorder")
        check(kotlin.math.abs(newNote.centerY() - noteReorder.centerY()) < 4 &&
            noteReorder.right > targetContext.resources.displayMetrics.widthPixels * .8f) {
            "Notes reorder is not right-aligned with New Note"
        }
        tap("Note 0")
        await("body focused") { nodes().any { it.isEditable && it.isFocused && it.text?.contains("Line 000") == true } }
        sendKeyDownUpSync(KeyEvent.KEYCODE_X)
        await("typing at first character") { Store(targetContext).notes().first().body.startsWith("xLine 000") }
        sendKeyDownUpSync(KeyEvent.KEYCODE_DEL)
        check(nodes().any { it.isEditable && it.text?.toString() == "Note 0" }) { "Note title is not the heading" }
        tap("view")
        scrollForward()
        val before = topLine()
        check(before.isNotEmpty() && before != "Line 000 bold and ordinary text") { "Note did not scroll" }
        tap("edit")
        await("editing") { nodes().any { it.isEditable && it.text?.contains("Line 000") == true } }
        tap("view")
        await("same note viewport") { topLine() == before }
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        tap("+ new note")
        await("new note title focused") {
            val titleTop = bounds("title").top
            nodes().any { node ->
                val nodeBounds = Rect().also(node::getBoundsInScreen)
                node.isEditable && node.isFocused && nodeBounds.top == titleTop
            }
        }
        tap("Note body")
        tap("<>")
        check(Store(targetContext).notes().last().body == "``") { "Code ticks were not inserted" }
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        waitForIdleSync()
        if (nodes().none { it.text?.toString() == "+ new note" }) sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("notes list") { nodes().any { it.text?.toString() == "+ new note" } }
        tap("reorder")
        tap("Move down")
        check(Store(targetContext).notes().take(2).map { it.id } == listOf(1L, 0L)) { "Note reorder did not persist" }
        tap("done")
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        tap("To Do")
        checkTitleBelowStatusBar("to do")
        await("task input focused") { nodes().any { it.isEditable && it.isFocused } }
        val todoReorder = bounds("reorder")
        val clear = bounds("clear")
        check(kotlin.math.abs(todoReorder.centerY() - clear.centerY()) < 4 && todoReorder.left < clear.left) {
            "To Do reorder is not left-aligned with Clear"
        }
        tap("reorder")
        tap("Move down")
        check(Store(targetContext).tasks().take(2).map { it.id } == listOf(1L, 0L)) { "Task reorder did not persist" }
        scrollForward()
        tap("Add a task...")
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("Back from focused Todo goes Home") { nodes().any { it.text?.toString() == "Note" } }
    }
    private fun checkHome() {
        swipe(true)
        await("Notifier opens") { nodes().any { it.text?.toString() == "notifier" } }
        swipe(false)
        await("Notifier left swipe Home") { nodes().any { it.text?.toString() == "Note" } }
        swipe(false)
        await("Settings") { nodes().any { it.text?.toString() == "settings" } }
        scrollForward()
        tap("90%")
        await("90% saved") { Prefs(targetContext).textScale == 90 }
        scrollForward()
        tap("notifier")
        check(!Prefs(targetContext).notifierEnabled)
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        swipe(true)
        check(nodes().any { it.text?.toString() == "Note" }) { "Disabled Notifier opened" }
        swipe(false)
        scrollForward()
        tap("notifier")
        check(Prefs(targetContext).notifierEnabled)
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    }
    private fun checkPeak() {
        await("Peak visible") { nodes().any { it.text?.matches(Regex("\\d{2}:\\d{2}")) == true } }
        fun clockTop(): Int = Rect().also(nodes().first { it.text?.matches(Regex("\\d{2}:\\d{2}")) == true }::getBoundsInScreen).top
        fun weatherLabel() = nodes().first { it.text?.toString() == "—" || it.text?.contains("°") == true }.text.toString()
        val visibleTop = clockTop()
        uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(targetContext.filesDir, "peak-status-visible.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        swipe(false)
        await("settings") { nodes().any { it.text?.toString() == "settings" } }
        scrollForward()
        tap("hide status bar")
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("hidden bar Home") { nodes().any { it.text?.toString() == "Note" } }
        check(clockTop() < visibleTop) { "Peak did not move up with hidden status bar" }
        val m = targetContext.resources.displayMetrics
        val t = SystemClock.uptimeMillis()
        sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, m.widthPixels * .5f, m.heightPixels * .45f, 0))
        SystemClock.sleep(650)
        sendPointerSync(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, m.widthPixels * .5f, m.heightPixels * .45f, 0))
        await("edit mode") { nodes().any { it.text?.contains("edit mode ·") == true } }
        uiAutomation.takeScreenshot()?.let { bitmap -> java.io.File(targetContext.filesDir, "peak-edit.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        tap(weatherLabel())
        tap("Note (built-in)")
        check(Prefs(targetContext).weatherApp == "promptpad:notes")
        await("picker closed") { nodes().none { it.text?.toString() == "replace shortcut" } }
        tap(weatherLabel())
        await("weather opens configured destination") { nodes().any { it.text?.toString() == "Note 0" } }
    }
    private fun swipe(right: Boolean, heightFraction: Float = .85f) {
        val metrics = targetContext.resources.displayMetrics
        val start = metrics.widthPixels * if (right) .2f else .8f
        val end = metrics.widthPixels - start
        val y = metrics.heightPixels * heightFraction
        val t = SystemClock.uptimeMillis()
        sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, start, y, 0))
        for (i in 1..12) {
            SystemClock.sleep(16)
            sendPointerSync(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, start + (end - start) * i / 12, y, 0))
        }
        sendPointerSync(MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, end, y, 0))
        waitForIdleSync()
        SystemClock.sleep(300)
    }
    private fun topLine(): String = nodes().firstOrNull {
        it.isVisibleToUser && it.text?.toString()?.matches(Regex("Line [0-9]{3}[^\\n]*")) == true
    }?.text?.toString().orEmpty()
    private fun scrollForward() {
        val scroll = nodes().first { it.isScrollable }
        scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        waitForIdleSync()
        SystemClock.sleep(300)
    }
    private fun tap(label: String) {
        await(label) { nodes().any { it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label) } }
        val n = nodes().first { it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label) }
        val r = Rect().also(n::getBoundsInScreen)
        val t = SystemClock.uptimeMillis()
        sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, r.exactCenterX(), r.exactCenterY(), 0))
        sendPointerSync(MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, r.exactCenterX(), r.exactCenterY(), 0))
        waitForIdleSync()
    }
    private fun bounds(label: String) = Rect().also { result ->
        nodes().first {
            it.isVisibleToUser && (it.text?.toString() == label || it.contentDescription?.toString() == label)
        }.getBoundsInScreen(result)
    }
    private fun checkTitleBelowStatusBar(label: String) {
        val inset = targetContext.getSystemService(WindowManager::class.java).currentWindowMetrics
            .windowInsets.getInsets(WindowInsets.Type.statusBars()).top
        val top = bounds(label).top
        check(top >= inset) { "$label overlaps the status bar: titleTop=$top inset=$inset" }
    }
    private fun trayContains(key: String): Boolean = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        uiAutomation.executeShellCommand("cmd notification list")).bufferedReader().use { key in it.readText() }
    private fun await(label: String, predicate: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 8000
        while (SystemClock.uptimeMillis() < end) { if (predicate()) return; SystemClock.sleep(100) }
        error("Timed out: $label")
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        uiAutomation.clearCache()
        fun walk(n: AccessibilityNodeInfo): List<AccessibilityNodeInfo> = listOf(n) + (0 until n.childCount).flatMap { n.getChild(it)?.let(::walk).orEmpty() }
        return uiAutomation.rootInActiveWindow?.let(::walk).orEmpty()
    }
}
