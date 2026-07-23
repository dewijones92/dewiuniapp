package com.dewijones92.uniapp.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dewijones92.uniapp.MainActivity
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.data.content.SourceUpdate

/**
 * Posts the "new content" notification for whatever the background refresh
 * found. One notification per source (so several feeds updating don't collapse
 * into a single opaque line), grouped under a summary. Both pillars flow
 * through here unchanged — a [SourceUpdate] is a [SourceUpdate].
 */
public class NewContentNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    public fun notify(updates: List<SourceUpdate>) {
        if (updates.isEmpty()) return
        ensureChannel()
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        updates.forEach { update ->
            manager.notify(update.source.id.value.hashCode(), sourceNotification(update))
        }
        manager.notify(SUMMARY_ID, summaryNotification(updates))
    }

    private fun sourceNotification(update: SourceUpdate) =
        baseBuilder()
            .setContentTitle(update.source.title)
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.new_content_source_text,
                    update.items.size,
                    update.items.size,
                ),
            )
            .setStyle(
                NotificationCompat.InboxStyle().also { style ->
                    update.items.take(MAX_LINES).forEach { style.addLine(it.title) }
                },
            )
            .setGroup(GROUP_KEY)
            .build()

    private fun summaryNotification(updates: List<SourceUpdate>): android.app.Notification {
        val total = updates.sumOf { it.items.size }
        return baseBuilder()
            .setContentTitle(context.getString(R.string.new_content_title))
            .setContentText(
                context.resources.getQuantityString(R.plurals.new_content_summary_text, total, total),
            )
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .build()
    }

    private fun baseBuilder() =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_new_content)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.new_content_channel_name))
                .setDescription(context.getString(R.string.new_content_channel_description))
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = "new_content"
        const val GROUP_KEY = "com.dewijones92.uniapp.NEW_CONTENT"
        const val SUMMARY_ID = -1
        const val MAX_LINES = 5
    }
}
