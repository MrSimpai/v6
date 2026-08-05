package com.example.medtap.reminder

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import com.example.medtap.data.outstandingToday
import com.example.medtap.data.perfectDayStreak
import com.example.medtap.ui.Dragon
import com.example.medtap.ui.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Deliberately not a foreground service: on Android 14+ FGS notifications became
 * user-dismissable and `specialUse` needs a Play Console justification, so a service is
 * now worse on both counts. An ongoing notification posted from an alarm receiver, with
 * a deleteIntent that re-posts it, is non-swipeable and costs nothing between doses.
 */
object Reminders {

    const val CHANNEL_NAG = "dose_due"
    const val CHANNEL_YAY = "dose_logged"
    const val CHANNEL_RISK = "streak_risk"
    const val ACTION_DUE = "com.example.medtap.DUE"
    const val ACTION_NAG = "com.example.medtap.NAG"
    const val ACTION_REPOST = "com.example.medtap.REPOST"
    const val ACTION_ICON = "com.example.medtap.ICON"
    const val ACTION_LASTCALL = "com.example.medtap.LASTCALL"
    const val EXTRA_TAG_ID = "tagId"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_MOOD = "mood"

    private fun notifId(tagId: String) = tagId.hashCode() and 0x0FFFFFFF

    /** Fixed id: there is only ever one countdown, for the day as a whole. */
    private const val LASTCALL_ID = 0x0FF1CE

    /** The hour the evening countdown appears. Three hours of runway before midnight. */
    private const val LASTCALL_HOUR = 21

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
        lastCallChannel(nm)
        if (nm.getNotificationChannel(CHANNEL_YAY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_YAY, "Dose enregistrée", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Celle qui félicite." ; enableVibration(true) }
            )
        }
    }

    // ---- scheduling -------------------------------------------------------

    private fun lastCallChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_RISK) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RISK, "Série en jeu", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Le compte à rebours du soir, avant minuit." }
            )
        }
    }

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

    /**
     * Repainting the launcher icon can force-stop the app and makes the launcher redraw,
     * so it never happens anywhere near the user. A minute after a dose is logged she has
     * moved on, and a brief flicker on an idle home screen is nothing like the app
     * disappearing under her thumb. Inexact and RTC, not RTC_WAKEUP: waking the device
     * to repaint an icon would be absurd.
     */
    private fun scheduleIconUpdate(ctx: Context, mood: Mood, delayMs: Long) {
        val pi = PendingIntent.getBroadcast(
            ctx, ACTION_ICON.hashCode(),
            Intent(ctx, ReminderReceiver::class.java).apply {
                action = ACTION_ICON
                putExtra(EXTRA_MOOD, mood.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ctx.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.RTC, System.currentTimeMillis() + delayMs, pi)
    }

    /**
     * Tear everything down for a medication being removed. The PendingIntent request code
     * is keyed on tagId + action and ignores the slot, so one cancel per action clears any
     * pending alarm whatever slot it was armed for.
     */
    /** Arms tonight's countdown, or tomorrow's if 21h has already gone by. */
    fun scheduleLastCall(ctx: Context) {
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, LASTCALL_HOUR)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        val pi = PendingIntent.getBroadcast(
            ctx, ACTION_LASTCALL.hashCode(),
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_LASTCALL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Midnight tonight: the moment the day, and the streak, turns over. */
    private fun midnightTonight(): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * The evening countdown, Duolingo's trick: a live ticking clock rather than a sentence
     * about time. `setChronometerCountDown` hands the ticking to the system, so it stays
     * accurate without the app waking up once, and it reads as pressure in a way that
     * "il reste 2 heures" -- frozen at whatever it said when it was posted -- never does.
     *
     * Called both by the 21h alarm and after any dose is logged, so it appears, updates
     * and disappears on its own. Also re-arms tomorrow's alarm.
     */
    fun refreshLastCall(ctx: Context) = CoroutineScope(Dispatchers.IO).launch {
        ensureChannels(ctx)
        scheduleLastCall(ctx)

        val nm = NotificationManagerCompat.from(ctx)
        val dao = Db.get(ctx).dao()
        val meds = dao.activeMedsOnce()
        val left = dao.outstandingToday(meds)
        val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // Nothing owed, or too early in the evening for a last call.
        if (left.isEmpty() || hour < LASTCALL_HOUR) {
            nm.cancel(LASTCALL_ID)
            return@launch
        }

        val midnight = midnightTonight()
        val streak = dao.perfectDayStreak(meds)
        val line = FloMessages.lastCall(streak, left.map { it.name })

        val open = PendingIntent.getActivity(
            ctx, LASTCALL_ID,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_RISK)
            .setSmallIcon(R.drawable.ic_stat_dragon)
            .setLargeIcon(Dragon.faceBitmap(256, Mood.Sad))
            .setContentTitle(line.title)
            .setContentText(line.body)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(NotifArt.banner(Mood.Sad, line.title, line.body))
                    .bigLargeIcon(null as android.graphics.Bitmap?)
                    .setSummaryText(line.body)
            )
            .setColor(Dragon.Pink)
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(midnight)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)       // le compte à rebours, tenu par le système
            .setTimeoutAfter(midnight - now)     // s'efface pile quand il atteint zéro
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        if (nm.areNotificationsEnabled()) nm.notify(LASTCALL_ID, n)
    }

    fun cancelAll(ctx: Context, med: Medication) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val slot = Slots.todayAt(med)
        am.cancel(alarmPI(ctx, ACTION_DUE, med, slot))
        am.cancel(alarmPI(ctx, ACTION_NAG, med, slot))
        val nm = NotificationManagerCompat.from(ctx)
        nm.cancel(notifId(med.tagId))
        nm.cancel(notifId(med.tagId) + 1)
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
            // Le compte à rebours vers minuit, tenu par le système, directement sur le
            // rappel. Duolingo ne dit pas « il te reste du temps », il le montre fondre.
            .setShowWhen(true)
            .setWhen(midnightTonight())
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(open)
            .setDeleteIntent(repost)
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
    /** Housekeeping after a dose lands: silence the nag, arm tomorrow, calm the icon. */
    fun resolve(ctx: Context, med: Medication, slot: Long) {
        ensureChannels(ctx)
        NotificationManagerCompat.from(ctx).cancel(notifId(med.tagId))
        cancelNag(ctx, med, slot)
        scheduleNext(ctx, med)
        scheduleIconUpdate(ctx, Mood.Sleeping, 60_000L)
        refreshLastCall(ctx)      // may have been the last one owed -- clear the countdown
    }

    /**
     * The congratulation, as a notification.
     *
     * Only posted when she is NOT looking at the app -- a tag tapped against a bottle with
     * the phone asleep, typically. When she logs a dose in the app she gets the cheering
     * dragon, and on the last dose of the day a full-screen celebration, so a notification
     * on top of that would just be the same news twice.
     *
     * [dayStreak] is non-zero only when this was the last dose of the day; it outranks the
     * per-medication streak, because "nothing missed all day" is the better thing to say.
     */
    fun celebrate(ctx: Context, med: Medication, slot: Long, streak: Int, dayStreak: Int) {
        ensureChannels(ctx)
        val line = if (dayStreak > 0)
            FloLine("Journée complète 🐉", FloMessages.dayStreakLine(dayStreak))
        else
            FloMessages.celebration(streak, slot)
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
    }

    fun rescheduleAll(ctx: Context) = CoroutineScope(Dispatchers.IO).launch {
        Db.get(ctx).dao().activeMedsOnce().forEach { scheduleNext(ctx, it) }
        scheduleLastCall(ctx)
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

        if (action == Reminders.ACTION_LASTCALL) {
            Reminders.refreshLastCall(ctx.applicationContext)
            return
        }

        if (action == Reminders.ACTION_ICON) {
            val mood = runCatching { Mood.valueOf(intent.getStringExtra(Reminders.EXTRA_MOOD)!!) }
                .getOrNull() ?: return
            IconSwitcher.apply(ctx.applicationContext, mood)
            return
        }

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
