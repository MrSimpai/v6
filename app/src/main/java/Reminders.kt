package com.example.medtap.reminder

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.medtap.MainActivity
import com.example.medtap.R
import com.example.medtap.data.Db
import com.example.medtap.data.Medication
import com.example.medtap.data.Slots
import com.example.medtap.ui.Dragon
import com.example.medtap.ui.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Deliberately not a foreground service: on Android 14+ FGS notifications became
 * user-dismissable and `specialUse` needs a Play Console justification, so a service is
 * now worse on both counts. An ongoing notification posted from an alarm receiver, with
 * a deleteIntent that re-posts it, is non-swipeable and costs nothing between doses.
 */
object Reminders {

    const val CHANNEL_NAG = "dose_due"
    const val CHANNEL_YAY = "dose_logged"
    const val ACTION_DUE = "com.example.medtap.DUE"
    const val ACTION_NAG = "com.example.medtap.NAG"
    const val ACTION_REPOST = "com.example.medtap.REPOST"
    const val EXTRA_TAG_ID = "tagId"
    const val EXTRA_SLOT = "slot"

    private fun notifId(tagId: String) = tagId.hashCode() and 0x0FFFFFFF

    // ---- channels ---------------------------------------------------------

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)

        if (nm.getNotificationChannel(CHANNEL_NAG) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_NAG, "Dose à prendre", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "Ton dragon, jusqu'à ce que la dose soit enregistrée."
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 220, 140, 220, 140, 340)
                        setBypassDnd(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                        setSound(
                            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                        )
                    }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_YAY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_YAY, "Dose enregistrée", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Celle qui félicite." ; enableVibration(true) }
            )
        }
    }

    // ---- scheduling -------------------------------------------------------

    private fun alarmPI(ctx: Context, action: String, med: Medication, slot: Long): PendingIntent {
        val intent = Intent(ctx, ReminderReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TAG_ID, med.tagId)
            putExtra(EXTRA_SLOT, slot)
        }
        return PendingIntent.getBroadcast(
            ctx, (med.tagId + action).hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleNext(ctx: Context, med: Medication) {
        val slot = Slots.nextAfter(med)
        val show = PendingIntent.getActivity(
            ctx, med.tagId.hashCode(), Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // setAlarmClock is the only scheduling call fully exempt from Doze deferral.
        ctx.getSystemService(AlarmManager::class.java)
            .setAlarmClock(AlarmManager.AlarmClockInfo(slot, show), alarmPI(ctx, ACTION_DUE, med, slot))
    }

    private fun scheduleNag(ctx: Context, med: Medication, slot: Long) {
        val at = System.currentTimeMillis() + med.nagEveryMinutes * 60_000L
        ctx.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, at, alarmPI(ctx, ACTION_NAG, med, slot)
        )
    }

    fun cancelNag(ctx: Context, med: Medication, slot: Long) =
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmPI(ctx, ACTION_NAG, med, slot))

    // ---- the sticky dragon ------------------------------------------------

    fun post(ctx: Context, med: Medication, slot: Long, alertAgain: Boolean) {
        ensureChannels(ctx)
        val lateMin = ((System.currentTimeMillis() - slot) / 60_000L).coerceAtLeast(0)
        val (tier, line) = FloMessages.line(lateMin, slot, med.name)
        val mood = tier.mood

        IconSwitcher.apply(ctx, mood)

        val open = PendingIntent.getActivity(
            ctx, med.tagId.hashCode(),
            Intent(ctx, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse("medtap://dose/${med.tagId}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val repost = PendingIntent.getBroadcast(
            ctx, (med.tagId + ACTION_REPOST).hashCode(),
            Intent(ctx, ReminderReceiver::class.java).apply {
                action = ACTION_REPOST
                putExtra(EXTRA_TAG_ID, med.tagId); putExtra(EXTRA_SLOT, slot)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val face = Dragon.faceBitmap(256, mood)
        val banner = NotifArt.banner(mood, line.title, line.body)

        val n = NotificationCompat.Builder(ctx, CHANNEL_NAG)
            .setSmallIcon(R.drawable.ic_stat_dragon)
            .setLargeIcon(face)
            .setContentTitle(line.title)
            .setContentText(line.body)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(banner)
                    .bigLargeIcon(null as android.graphics.Bitmap?)  // banner takes over when expanded
                    .setSummaryText(line.body)
            )
            .setColor(if (tier == Tier.SERIEUX) Dragon.Plum else Dragon.Pink)
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!alertAgain)
            .setShowWhen(true).setWhen(slot)
            .setContentIntent(open)
            .setDeleteIntent(repost)
            .setFullScreenIntent(open, true)
            .addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_stat_dragon, "Ouvrir", open
                ).build()
            )
            .build()

        if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            NotificationManagerCompat.from(ctx).notify(notifId(med.tagId), n)
        }
    }

    /** Fires the instant a dose is logged -- scanned or ticked off: dragon celebrates, nagging stops. */
    fun resolve(ctx: Context, med: Medication, slot: Long, streak: Int) {
        ensureChannels(ctx)
        NotificationManagerCompat.from(ctx).cancel(notifId(med.tagId))
        cancelNag(ctx, med, slot)
        scheduleNext(ctx, med)

        val line = FloMessages.celebration(streak, slot)
        val yay = NotificationCompat.Builder(ctx, CHANNEL_YAY)
            .setSmallIcon(R.drawable.ic_stat_dragon)
            .setLargeIcon(Dragon.faceBitmap(256, Mood.Cheering))
            .setContentTitle(line.title)
            .setContentText(line.body)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(NotifArt.banner(Mood.Cheering, line.title, line.body))
                    .bigLargeIcon(null as android.graphics.Bitmap?)
            )
            .setColor(0xFF3FA98D.toInt())
            .setColorized(true)
            .setTimeoutAfter(45_000)     // long enough to actually read it
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            NotificationManagerCompat.from(ctx).notify(notifId(med.tagId) + 1, yay)
        }

        // resolve() is only ever reached from the UI, so the launcher icon waits until
        // she closes the app. Swapping it here would kill the app under her.
        IconSwitcher.requestOnLeave(Mood.Sleeping)
    }

    fun rescheduleAll(ctx: Context) = CoroutineScope(Dispatchers.IO).launch {
        Db.get(ctx).dao().activeMedsOnce().forEach { scheduleNext(ctx, it) }
    }

    internal fun onAlarm(ctx: Context, action: String, tagId: String, slot: Long, done: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = Db.get(ctx).dao()
                val med = dao.med(tagId) ?: return@launch
                // Past midnight the slot this alarm belongs to is yesterday's, and
                // yesterday is not recoverable. Clear it and wait for today's.
                if (slot != Slots.todayAt(med) || dao.logForSlot(tagId, slot) != null) {
                    NotificationManagerCompat.from(ctx).cancel(notifId(tagId))
                    cancelNag(ctx, med, slot); scheduleNext(ctx, med)
                    return@launch
                }
                when (action) {
                    ACTION_DUE, ACTION_NAG -> {
                        post(ctx, med, slot, alertAgain = true); scheduleNag(ctx, med, slot)
                    }
                    ACTION_REPOST -> post(ctx, med, slot, alertAgain = false)
                }
            } finally { done() }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        val tagId = intent.getStringExtra(Reminders.EXTRA_TAG_ID) ?: return
        val slot = intent.getLongExtra(Reminders.EXTRA_SLOT, 0L)
        val pending = goAsync()
        Reminders.onAlarm(ctx.applicationContext, action, tagId, slot) { pending.finish() }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminders.ensureChannels(ctx)
        Reminders.rescheduleAll(ctx.applicationContext)
    }
}
