package com.hermes.promptpad

import android.app.ActivityOptions
import android.content.ComponentName
import android.os.Build
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.mutableStateListOf


enum class HubKind { MESSAGE, CALL, OTHER }

data class HubItem(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val kind: HubKind,
    val reply: Pair<PendingIntent, RemoteInput>?,
    val content: PendingIntent?,
    val starred: Boolean = false,
    val replies: List<String> = emptyList(),
)

/** ponytail: one in-memory list owned by the service; the Hub UI is only alive while the app is. */
class HubListener : NotificationListenerService() {

    override fun onListenerConnected() {
        listener = this
        MediaWidget.start(this, ComponentName(this, HubListener::class.java))
        items.clear()
        activeNotifications?.forEach { add(it) }
    }

    override fun onListenerDisconnected() {
        if (listener === this) listener = null
        MediaWidget.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = add(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Keep only removals explicitly caused by starring; every clear removes the card.
        if (!retained.remove(sbn.key)) items.removeAll { it.key == sbn.key }
    }

    private fun add(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        // The media widget owns an active session's notification, including when paused.
        if (sbn.packageName == MediaWidget.state.value?.pkg) {
            MediaWidget.rememberNotification(sbn.key)
            return
        }
        val n = sbn.notification
        val previous = items.firstOrNull { it.key == sbn.key }
        items.removeAll { it.key == sbn.key }
        if (!shouldInclude(n.flags)) return
        val x = n.extras
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        // ponytail: MessagingStyle carries the tray's appended conversation; fall back to plain text.
        val messages = androidx.core.app.NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(n)?.messages
            ?.mapNotNull { it.text?.toString() }?.filter { it.isNotBlank() }
        val incoming = if (!messages.isNullOrEmpty()) messages.takeLast(8).joinToString("\n")
            else x.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: x.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val replies = previous?.replies.orEmpty()
        // ponytail: keep the conversation only while this system notification lives.
        val text = if (replies.isEmpty()) incoming else
            (previous!!.text.lines() + incoming.lines().filterNot { it in replies }).distinct().joinToString("\n")
        items.add(0, HubItem(
            sbn.key, sbn.packageName, title, text, sbn.postTime,
            kindOf(sbn.packageName, n), replyOf(n), n.contentIntent, previous?.starred ?: false, replies,
        ))
    }

    companion object {
        val items = mutableStateListOf<HubItem>()
        private val retained = mutableSetOf<String>()
        @Volatile private var listener: HubListener? = null

        fun isEnabled(ctx: Context): Boolean =
            (android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "")
                .contains(ctx.packageName)

        fun shouldInclude(flags: Int): Boolean = flags and Notification.FLAG_GROUP_SUMMARY == 0

        fun openSettings(ctx: Context) {
            ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        fun isMessagingPackage(pkg: String): Boolean = pkg in setOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
        ) || pkg.contains("mms") || pkg.contains("messag")

        fun kindOf(pkg: String, n: Notification): HubKind = when {
            n.category == Notification.CATEGORY_CALL || n.category == Notification.CATEGORY_MISSED_CALL -> HubKind.CALL
            n.category == Notification.CATEGORY_MESSAGE || isMessagingPackage(pkg) -> HubKind.MESSAGE
            else -> HubKind.OTHER
        }

        fun replyOf(n: Notification): Pair<PendingIntent, RemoteInput>? {
            n.actions?.forEach { action ->
                action.remoteInputs?.firstOrNull { it.allowFreeFormInput }?.let { return action.actionIntent to it }
            }
            return null
        }

        fun retain(key: String) {
            retained += key
            runCatching { listener?.cancelNotification(key) }
        }

        fun dismiss(key: String): Boolean {
            // WhatsApp children are rebuilt from their group summary unless the whole live group is cleared.
            retained.remove(key)
            items.removeAll { it.key == key }
            val service = listener ?: return true
            return runCatching {
                val active = service.activeNotifications ?: return false
                val source = active.firstOrNull { it.key == key } ?: return false
                val keys = active.filter { it.key == key || source.isGroup && it.groupKey == source.groupKey }
                    .map { it.key }.toTypedArray()
                service.cancelNotifications(keys)
                true
            }.getOrDefault(false)
        }

        fun open(ctx: Context, item: HubItem): Boolean {
            // Android blocks notification trampolines; non-activity intents use the app fallback.
            item.content?.takeIf { Build.VERSION.SDK_INT < 31 || it.isActivity }?.let { pending ->
                val options = ActivityOptions.makeBasic().apply {
                    // Android 14+ requires sender opt-in for another app's activity.
                    if (Build.VERSION.SDK_INT >= 36)
                        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
                    else if (Build.VERSION.SDK_INT >= 34)
                        setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                if (runCatching { pending.send(ctx, 0, null, null, null, null, options.toBundle()) }.isSuccess) return true
            }
            return runCatching {
                val launch = ctx.packageManager.getLaunchIntentForPackage(item.pkg) ?: return false
                ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            }.getOrDefault(false)
        }

        fun sendReply(ctx: Context, item: HubItem, text: String): Boolean {
            val (pendingIntent, remoteInput) = item.reply ?: return false
            if (text.isBlank()) return false
            val intent = Intent()
            RemoteInput.addResultsToIntent(
                arrayOf(remoteInput), intent,
                android.os.Bundle().apply { putCharSequence(remoteInput.resultKey, text) },
            )
            return runCatching {
                pendingIntent.send(ctx, 0, intent)
                val index = items.indexOfFirst { it.key == item.key }
                if (index >= 0) items[index] = items[index].let { it.copy(replies = it.replies + text) }
                true
            }.getOrDefault(false)
        }
    }
}
