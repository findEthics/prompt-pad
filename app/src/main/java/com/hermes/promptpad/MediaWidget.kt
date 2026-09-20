package com.hermes.promptpad

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.compose.runtime.mutableStateOf

// ponytail: trimmed from katapult's AudioWidgetHelper — one active session only.
@SuppressLint("StaticFieldLeak")
object MediaWidget {
    data class Info(val pkg: String, val playing: Boolean, val title: String?, val artist: String?, val controller: MediaController)

    val state = mutableStateOf<Info?>(null)

    private var manager: MediaSessionManager? = null
    private var listener: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private var controller: MediaController? = null
    private var callback: MediaController.Callback? = null
    private var notificationKey: String? = null
    private var dismissed = false

    fun start(ctx: Context, component: ComponentName) {
        if (manager == null) {
            val m = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            manager = m
            val l = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
            listener = l
            runCatching { m.addOnActiveSessionsChangedListener(l, component) }
        }
        // A listener connection can be cached across an app update; refresh every visible Notifier.
        manager?.let { m -> runCatching { pick(m.getActiveSessions(component)) } }
    }

    fun stop() {
        listener?.let { manager?.removeOnActiveSessionsChangedListener(it) }
        listener = null
        unwatch()
        manager = null
        state.value = null
    }

    private fun pick(controllers: List<MediaController>?) {
        // Watch an idle session too: playback-state changes do not change the active-session list.
        val active = controllers.orEmpty().firstOrNull { isPlaying(it.playbackState?.state) }
            ?: controllers.orEmpty().firstOrNull { isActiveState(it.playbackState?.state) }
            ?: controllers.orEmpty().firstOrNull()
        if (active == null) { unwatch(); state.value = null; return }
        watch(active)
        if (isActiveState(active.playbackState?.state)) update(active) else state.value = null
    }

    fun isPlaying(s: Int?) = s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING ||
        s == PlaybackState.STATE_CONNECTING

    fun isActiveState(s: Int?) = s != null && s != PlaybackState.STATE_NONE && s != PlaybackState.STATE_STOPPED

    private fun watch(c: MediaController) {
        if (controller?.sessionToken == c.sessionToken) return
        unwatch()
        notificationKey = null
        dismissed = false
        val cb = object : MediaController.Callback() {
            override fun onMetadataChanged(m: android.media.MediaMetadata?) = update(c)
            override fun onPlaybackStateChanged(s: PlaybackState?) {
                if (isActiveState(s?.state)) update(c) else state.value = null
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
        val playback = c.playbackState?.state
        if (!isActiveState(playback) || dismissed && !isPlaying(playback)) return
        if (isPlaying(playback)) dismissed = false
        HubListener.items.firstOrNull { it.pkg == c.packageName }?.key?.let { notificationKey = it }
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

    fun rememberNotification(key: String) { notificationKey = key }

    fun dismiss() {
        if (state.value?.playing != false) return
        dismissed = true
        notificationKey?.let { HubListener.dismiss(it) }
        notificationKey = null
        state.value = null
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
