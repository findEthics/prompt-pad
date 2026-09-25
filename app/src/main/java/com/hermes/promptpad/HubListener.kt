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

data class HubMessage(val text: String, val isUser: Boolean = false)

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
    val messages: List<HubMessage> = emptyList(),
    val latestReply: String? = null,
)

/** ponytail: one in-memory list owned by the service; the Hub UI is only alive while the app is. */
class HubListener : NotificationListenerService() {

    override fun onListenerConnected() {
        listener = this
        MediaWidget.start(this, ComponentName(this, HubListener::class.java))
        replySenders.clear()
        items.clear()
        activeNotifications?.forEach { add(it) }
    }

    override fun onListenerDisconnected() {
        if (listener === this) listener = null
        MediaWidget.stop()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = add(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        if (retained.remove(sbn.key)) return
        if (reason != REASON_APP_CANCEL && reason != REASON_APP_CANCEL_ALL) {
            replySenders.remove(sbn.key)
            items.removeAll { it.key == sbn.key }
            return
        }
        // Apps commonly cancel-then-repost the same key (e.g. to relabel an outgoing reply as
        // "You"); wait briefly for that repost instead of dropping the card on every app-cancel.
        val key = sbn.key
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val stillGone = runCatching { listener?.activeNotifications?.none { it.key == key } }.getOrNull() ?: true
            if (stillGone) {
                replySenders.remove(key)
                items.removeAll { it.key == key }
            }
        }, CANCEL_GRACE_MS)
    }

    private fun add(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        // The media widget owns an active session's notification, including when paused.
        if (sbn.packageName == MediaWidget.state.value?.pkg) {
            MediaWidget.rememberNotification(sbn.key)
            return
        }
        val n = sbn.notification
        val previousIndex = items.indexOfFirst { it.key == sbn.key }
        val previous = items.getOrNull(previousIndex)
        if (!shouldInclude(n.flags)) {
            if (previousIndex >= 0) items.removeAt(previousIndex)
            return
        }
        val x = n.extras
        // Outgoing reposts may use "You" or the MessagingStyle account name as the title.
        val title = x.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val style = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val userName = style?.user?.name?.toString()
        val original = replySenders[sbn.key]?.sender
            ?: previous?.title?.takeUnless { it == "You" || it == userName }
        val senderTitle = senderTitle(original, title, userName)
        val history = style?.messages?.sortedBy { it.timestamp }?.takeLast(8)
            ?.mapNotNull { message -> message.text?.toString()?.takeIf(String::isNotBlank)?.let {
                HubMessage(it, message.person == null || message.person?.name == style.user?.name)
            } }
        val current = if (!history.isNullOrEmpty()) history else {
            val text = x.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: x.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            listOfNotNull(text.takeIf { it.isNotBlank() }?.let {
                HubMessage(it, title == "You" && it == previous?.latestReply)
            })
        }
        val messages = latestMessages(current, previous?.latestReply, previous?.messages.orEmpty())
        val updated = HubItem(
            sbn.key, sbn.packageName, senderTitle, messages.joinToString("\n") { it.text }, sbn.postTime,
            kindOf(sbn.packageName, n), replyOf(n), n.contentIntent, previous?.starred ?: false,
            messages, previous?.latestReply,
        )
        if (previousIndex >= 0) items[previousIndex] = updated else items.add(0, updated)
    }

    companion object {
        val items = mutableStateListOf<HubItem>()
        private val retained = mutableSetOf<String>()
        private class PendingReply(val sender: String)
        private val replySenders = mutableMapOf<String, PendingReply>()
        // ponytail: fixed grace window for an app's own cancel-then-repost cycle; lengthen if real
        // reposts take longer, or swap for an active-notification poll if that proves too fragile.
        private const val CANCEL_GRACE_MS = 3_000L
        @Volatile private var listener: HubListener? = null

        internal fun listenerConnected() = listener != null

        fun isEnabled(ctx: Context): Boolean =
            (android.provider.Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: "")
                .contains(ctx.packageName)

        fun shouldInclude(flags: Int): Boolean = flags and Notification.FLAG_GROUP_SUMMARY == 0

        fun openSettings(ctx: Context) {
            ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        fun senderTitle(replySender: String?, current: String, userName: String? = null) =
            replySender?.takeIf { current == "You" || current == userName } ?: current

        // ponytail: only the latest local reply survives a repost; mirror the tray, not a chat archive.
        internal fun latestMessages(current: List<HubMessage>, latestReply: String?, previous: List<HubMessage>): List<HubMessage> {
            if (latestReply == null) return current
            val incoming = current.lastOrNull { !it.isUser }
                ?: previous.lastOrNull { !it.isUser }
            return listOfNotNull(incoming, HubMessage(latestReply, true))
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
            replySenders.remove(key)
            runCatching { listener?.cancelNotification(key) }
        }

        // Notification keys are profile-specific; add only confirmed app packages here.
        internal val individuallyDismissedPackages = setOf("com.google.android.gm", "com.whatsapp", "com.whatsapp.w4b")
        internal fun dismissesIndividually(pkg: String) = pkg in individuallyDismissedPackages

        internal fun shouldDismissKey(sourceKey: String, sourcePkg: String, sourceIsGroup: Boolean,
            sourceGroupKey: String, candidateKey: String, candidateGroupKey: String) =
            candidateKey == sourceKey || !dismissesIndividually(sourcePkg) && sourceIsGroup && candidateGroupKey == sourceGroupKey

        fun dismiss(key: String): Boolean {
            // Other grouped apps can rebuild a child from their summary, so clear their live group.
            retained.remove(key)
            replySenders.remove(key)
            items.removeAll { it.key == key }
            val service = listener ?: return true
            return runCatching {
                val active = service.activeNotifications ?: return false
                val source = active.firstOrNull { it.key == key } ?: return false
                val keys = active.filter {
                    shouldDismissKey(key, source.packageName, source.isGroup, source.groupKey, it.key, it.groupKey)
                }.map { it.key }.toTypedArray()
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
            val pending = PendingReply(item.title)
            val previousSender = replySenders.put(item.key, pending)
            return runCatching {
                pendingIntent.send(ctx, 0, intent)
                val index = items.indexOfFirst { it.key == item.key }
                if (index >= 0) items[index] = items[index].let {
                    it.copy(messages = latestMessages(it.messages, text, it.messages), latestReply = text)
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (replySenders[item.key] === pending) replySenders.remove(item.key)
                }, CANCEL_GRACE_MS)
                true
            }.getOrElse {
                if (previousSender == null) replySenders.remove(item.key) else replySenders[item.key] = previousSender
                false
            }
        }
    }
}
