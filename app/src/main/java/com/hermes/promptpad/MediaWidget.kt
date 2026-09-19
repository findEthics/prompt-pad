package com.hermes.promptpad

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.mutableStateOf

// ponytail: trimmed from katapult's AudioWidgetHelper — one active session, no dismissal memory
// (the notifier already hides the media notification, so there's nothing to re-summon).
@SuppressLint("StaticFieldLeak")
object MediaWidget {
    data class Info(val pkg: String, val playing: Boolean, val title: String?, val artist: String?, val controller: MediaController)

    val state = mutableStateOf<Info?>(null)

    private var manager: MediaSessionManager? = null
    private var listener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var controller: MediaController? = null
    private var callback: MediaController.Callback? = null

    fun start(ctx: Context, component: ComponentName) {
        if (manager != null) return  // already running (guard against double start from service + UI)
        val m = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
        manager = m
        val l = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        listener = l
        runCatching {
            m.addOnActiveSessionsChangedListener(l, component)
            pick(m.getActiveSessions(component))
        }
    }

    fun stop() {
        listener?.let { manager?.removeOnActiveSessionsChangedListener(it) }
        listener = null
        unwatch()
        manager = null
        state.value = null
    }

    private fun pick(controllers: List<MediaController>?) {
        // Only show the widget while something is actually playing (buffering/connecting = mid-play, keep).
        val active = controllers.orEmpty().firstOrNull { isPlaying(it.playbackState?.state) }
        if (active == null) { unwatch(); state.value = null; return }
        watch(active)
        update(active)
    }

    // ponytail: playing (or the brief buffering/connecting during a skip) shows the widget; paused/stopped hides it.
    fun isPlaying(s: Int?) = s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING ||
        s == PlaybackState.STATE_CONNECTING

    private fun watch(c: MediaController) {
        if (controller?.sessionToken == c.sessionToken) return
        unwatch()
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(m: android.media.MediaMetadata?) = update(c)
            override fun onPlaybackStateChanged(s: PlaybackState?) {
                // Pause/stop clears the widget (lets the app's dismissible notification return); resume re-shows it.
                if (isPlaying(s?.state)) update(c) else state.value = null
            }
            override fun onSessionDestroyed() { state.value = null }
        }
        runCatching { c.registerCallback(cb); controller = c; callback = cb }
    }

    private fun unwatch() {
        callback?.let { cb -> runCatching { controller?.unregisterCallback(cb) } }
        callback = null; controller = null
    }

    private fun update(c: MediaController) {
        HubListener.items.removeAll { it.pkg == c.packageName }
        val md = c.metadata
        state.value = Info(
            c.packageName,
            c.playbackState?.state == PlaybackState.STATE_PLAYING,
            md?.description?.title?.toString(),
            md?.description?.subtitle?.toString(),
            c,
        )
    }

    fun playPause() = state.value?.controller?.let {
        if (it.playbackState?.state == PlaybackState.STATE_PLAYING) it.transportControls.pause()
        else it.transportControls.play()
    }
    fun next() = state.value?.controller?.transportControls?.skipToNext()
    fun prev() = state.value?.controller?.transportControls?.skipToPrevious()
    fun open(ctx: Context) = state.value?.pkg?.let { pkg ->
        ctx.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); runCatching { ctx.startActivity(it) }
        }
    }
}
