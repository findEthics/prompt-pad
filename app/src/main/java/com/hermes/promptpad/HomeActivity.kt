package com.hermes.promptpad

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay

enum class Screen { Home, Drawer, Hub, Settings, Notes, Todo, Agenda, Instructions }

class HomeActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private var pendingKey: String? by mutableStateOf(null)
    private var screen: Screen by mutableStateOf(Screen.Home)
    private val homeScreen: Screen get() = if (prefs.notifierAsHome && prefs.notifierEnabled) Screen.Hub else Screen.Home
    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        if (!prefs.instructionsSeen) screen = Screen.Instructions else screen = homeScreen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screen != homeScreen && screen != Screen.Instructions) { pendingKey = null; screen = homeScreen }
            }
        })
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyStatusBar()

        setContent {
            var preferenceVersion by remember { mutableIntStateOf(0) }
            MinimalTheme(prefs, preferenceVersion) {
                var tick by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) { while (true) { delay(20_000); tick++ } }
                val back = { screen = homeScreen }
                when (screen) {
                    Screen.Home -> HomeScreen(prefs, { screen = it }, tick)
                    Screen.Drawer -> DrawerScreen(prefs, pendingKey.orEmpty(), { pendingKey = null; back() }) {
                        pendingKey = null
                        screen = Screen.Settings
                    }
                    Screen.Hub -> HubScreen(prefs, tick, back) { screen = it }
                    Screen.Settings -> SettingsScreen(
                        prefs,
                        back,
                        ::openAccessibility,
                        ::applyStatusBar,
                    ) { preferenceVersion++ }
                    Screen.Notes -> NotesScreen()
                    Screen.Todo -> TodoScreen { screen = homeScreen }
                    Screen.Agenda -> AgendaScreen { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
                    Screen.Instructions -> InstructionsScreen {
                        prefs.instructionsSeen = true
                        screen = Screen.Home
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingKey = null
        screen = if (prefs.instructionsSeen) homeScreen else Screen.Instructions
    }

    override fun onResume() {
        super.onResume()
        applyStatusBar()
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (screen != Screen.Home) return super.onKeyLongPress(keyCode, event)
        val key = event.displayLabel.lowercaseChar().toString()
        prefs.keyMap[key]?.let { Apps.launch(this, it); return true }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (screen == Screen.Home && prefs.keyMap.isNotEmpty()) event.startTracking()
        if (screen == Screen.Home) {
            val char = event.unicodeChar.toChar()
            if (char.isLetterOrDigit()) {
                pendingKey = char.toString()
                screen = Screen.Drawer
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun applyStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (prefs.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
    }
}
