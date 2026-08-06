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
import com.example.medtap.data.DoseLog
import com.example.medtap.data.Medication
import com.example.medtap.data.DayState
import com.example.medtap.data.ReminderPost
import com.example.medtap.data.Slots
import com.example.medtap.data.currentStreak
import com.example.medtap.data.logForSlot
import com.example.medtap.data.outstandingToday
import com.example.medtap.data.perfectDayStreak
import com.example.medtap.data.slotToLog
import com.example.medtap.data.weekStatus
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
    const val CHANNEL_SOON = "dose_soon"
    const val CHANNEL_RECAP = "week_recap"
    const val ACTION_DUE = "com.example.medtap.DUE"
    const val ACTION_NAG = "com.example.medtap.NAG"
    const val ACTION_REPOST = "com.example.medtap.REPOST"
    const val ACTION_ICON = "com.example.medtap.ICON"
    const val ACTION_SOON = "com.example.medtap.SOON"
    const val ACTION_RECAP = "com.example.medtap.RECAP"

    /** Un identifiant fixe : il n'y a qu'un bilan, et celui de dimanche prochain remplace celui-ci. */
    private const val RECAP_ID = 0x0BE11E

    /** Combien de minutes avant l'heure prévue le premier mot arrive. */
    const val HEADSUP_MINUTES = 15
    const val EXTRA_TAG_ID = "tagId"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_VIBE = "vibe"

    /**
     * Un seul identifiant par médicament, pour toute la journée.
     *
     * Le mot d'avance, le rappel et chacune de ses relances se posent tous là-dessus, donc
     * ils se REMPLACENT au lieu de s'empiler. Avant, l'avance avait son propre
     * identifiant : à l'heure pile on se retrouvait avec « c'est bientôt l'heure » et
     * « c'est l'heure » l'un sous l'autre, deux fois la même nouvelle. Une notification
     * par médicament, qui change de ton au fil de la journée, se lit comme quelqu'un qui
     * parle ; deux, comme un logiciel qui insiste.
     */
    private fun notifId(tagId: String) = tagId.hashCode() and 0x0FFFFFFF

    /**
     * Le haut de l'échelle, en millisecondes : au-delà, [Tier.SERIEUX], et il n'y a plus
     * de palier au-dessus. C'est la borne du compte à rebours affiché sur le rappel.
     */
    private const val ESCALATION_MS = 120 * 60_000L

    /**
     * Les heures où une relance ne fait plus de bruit. 22h à 8h.
     *
     * Ça ne touche QUE les relances : la première sonnerie, celle de l'heure prévue,
     * passe toujours. Une pilule de 22h30 doit réveiller quelqu'un ; la douzième relance
     * de la même pilule à 3h du matin ne réveille personne utilement, elle apprend juste
     * à couper les notifications de l'app — ce qui met fin à tout.
     */
    private const val QUIET_FROM_HOUR = 22
    private const val QUIET_TO_HOUR = 8

    private fun quietHours(now: Long): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return hour >= QUIET_FROM_HOUR || hour < QUIET_TO_HOUR
    }

    /**
     * Le droit de faire du bruit, décidé par ce qui a déclenché la pose.
     *
     * L'ancienne version était un booléen `alertAgain`, vrai pour l'heure prévue comme
     * pour toutes les relances : un rappel manqué à 21h sonnait donc au volume d'une
     * alarme toutes les dix minutes jusqu'à minuit, en traversant le mode Ne pas déranger.
     */
    enum class Alert {
        /** L'heure vient de sonner. Ça sonne, quelle que soit l'heure qu'il est. */
        DUE,

        /** Une relance. Elle se tait la nuit, et se tait passé le dernier palier. */
        NAG,

        /** Une remise en place après un balayage. Jamais de bruit : rien de neuf n'est arrivé. */
        SILENT
    }

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
        // Le canal du compte à rebours du soir a disparu avec lui. On le supprime plutôt
        // que de l'oublier : sinon il resterait pour toujours dans les réglages du
        // téléphone, listé sous un nom qui ne correspond plus à rien.
        runCatching { nm.deleteNotificationChannel("streak_risk") }

        if (nm.getNotificationChannel(CHANNEL_SOON) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SOON, "Bientôt l'heure", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply {
                        description = "Un mot quinze minutes avant, sans insister."
                        enableVibration(false)
                    }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_YAY) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_YAY, "Dose enregistrée", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Celle qui félicite." ; enableVibration(true) }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_RECAP) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RECAP, "Bilan du dimanche", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = "Une fois par semaine, ce qui s'est bien passé."
                        enableVibration(false)
                    }
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
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.setAlarmClock(AlarmManager.AlarmClockInfo(slot, show), alarmPI(ctx, ACTION_DUE, med, slot))

        // Le mot d'avance. Inexact et sans réveil : arriver à la minute près n'a aucune
        // importance quinze minutes avant, et ça ne justifie pas de sortir le téléphone
        // de veille.
        val soon = slot - HEADSUP_MINUTES * 60_000L
        if (soon > System.currentTimeMillis()) {
            am.set(AlarmManager.RTC, soon, alarmPI(ctx, ACTION_SOON, med, slot))
        }
    }

    /**
     * Le mot d'avance, un quart d'heure avant l'heure prévue.
     *
     * Tout le reste de l'échelle réagit à un retard, ce qui veut dire que sans lui le
     * premier mot de la journée est toujours un constat d'échec. Celui-ci prévient
     * pendant qu'il est encore possible d'être à l'heure : canal silencieux, aucune
     * vibration, aucun reproche — un coup de coude, pas une alarme.
     *
     * Il se pose sur [notifId], donc à l'heure pile le vrai rappel prend sa place au lieu
     * de s'ajouter en dessous : une seule ligne dans le tiroir, qui change de ton.
     */
    fun soon(ctx: Context, med: Medication, slot: Long) = postSoon(ctx, med, slot)

    private fun postSoon(ctx: Context, med: Medication, slot: Long) =
        CoroutineScope(Dispatchers.IO).launch {
            ensureChannels(ctx)
            val dao = Db.get(ctx).dao()
            if (dao.logForSlot(med.tagId, slot) != null) return@launch      // déjà prise en avance
            if (System.currentTimeMillis() >= slot) return@launch           // l'heure est passée

            val line = FloMessages.early(slot)
            val body = "${med.name} — ${line.body}"
            val open = PendingIntent.getActivity(
                ctx, notifId(med.tagId),
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val vibe =
                if (NotifArt.isNight()) NotifArt.Vibe.REST_NIGHT else NotifArt.Vibe.REST_DAY
            val n = NotificationCompat.Builder(ctx, CHANNEL_SOON)
                .setSmallIcon(R.drawable.ic_stat_dragon)
                .setLargeIcon(Dragon.faceBitmap(256, Mood.Sleeping))
                .setContentTitle(line.title)
                .setContentText(body)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        // Rien n'est en retard : le décor est celui du repos, jour ou nuit
                        // selon la pendule, pas selon l'humeur.
                        .bigPicture(NotifArt.banner(Mood.Sleeping, vibe, line.title, body))
                        .bigLargeIcon(null as android.graphics.Bitmap?)
                        .setBigContentTitle(line.title)
                )
                .setColor(Dragon.Pink)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                // Le quart d'heure fond à l'écran, tenu par le système. « Dans 15 minutes »
                // écrit en toutes lettres serait faux dès la minute suivante.
                .setShowWhen(true)
                .setWhen(slot)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setContentIntent(open)
                .build()
            val nm = NotificationManagerCompat.from(ctx)
            if (nm.areNotificationsEnabled()) nm.notify(notifId(med.tagId), n)
        }

    /**
     * Arme la prochaine relance.
     *
     * Passé le dernier palier, la prochaine n'est pas dans dix minutes mais juste après
     * minuit — et il en faut UNE, on ne peut pas simplement arrêter. C'est cette alarme
     * qui, une fois la journée tournée, retire le rappel périmé et arme celui du
     * lendemain (voir la sortie anticipée d'[onAlarm]). Sans elle, une seule dose oubliée
     * laissait un « tu es en retard » d'hier collé à l'écran et, pire, plus aucun rappel
     * les jours suivants tant que l'app n'était pas rouverte.
     *
     * Entre les deux, on ne réveille plus le téléphone toutes les dix minutes pour
     * reposer un message qui ne peut plus changer.
     */
    // ---- le bilan du dimanche ---------------------------------------------

    /** Dimanche 19h. Ou celui de la semaine prochaine s'il est déjà passé. */
    private const val RECAP_HOUR = 19

    fun scheduleRecap(ctx: Context) {
        val at = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, RECAP_HOUR)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY ||
                timeInMillis <= System.currentTimeMillis()
            ) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        // Inexact et sans réveil : arriver à la minute près pour un bilan hebdomadaire
        // n'a aucun intérêt, et ça ne justifie pas de sortir le téléphone de veille.
        ctx.getSystemService(AlarmManager::class.java).set(
            AlarmManager.RTC, at,
            PendingIntent.getBroadcast(
                ctx, ACTION_RECAP.hashCode(),
                Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_RECAP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    /**
     * Le seul message de toute l'app qui ne demande rien.
     *
     * Tout le reste part d'un problème : c'est l'heure, c'est en retard, c'est très en
     * retard. Une app qui ne parle que pour réclamer finit par n'être qu'une corvée, si
     * bien écrite soit-elle. Celui-ci arrive le dimanche soir et ne fait que raconter la
     * semaine.
     *
     * Il ne se pose QUE s'il ne reste rien à prendre. Féliciter quelqu'un pendant qu'un
     * rappel est encore affiché, ce serait deux notifications pour la même journée, dont
     * une qui se réjouit trop tôt — exactement ce qu'on a retiré ailleurs.
     */
    fun postRecap(ctx: Context) = CoroutineScope(Dispatchers.IO).launch {
        ensureChannels(ctx)
        scheduleRecap(ctx)                      // celui de dimanche prochain, tout de suite

        val dao = Db.get(ctx).dao()
        val meds = dao.activeMedsOnce()
        if (meds.isEmpty()) return@launch
        if (dao.outstandingToday(meds).isNotEmpty()) return@launch

        val done = dao.weekStatus(meds).count { it == DayState.DONE }
        val line = FloMessages.weeklyRecap(done, dao.currentStreak(meds))

        val open = PendingIntent.getActivity(
            ctx, RECAP_ID,
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_RECAP)
            .setSmallIcon(R.drawable.ic_stat_dragon)
            .setLargeIcon(Dragon.faceBitmap(256, Mood.Cheering))
            .setContentTitle(line.title)
            .setContentText(line.body)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(
                        NotifArt.banner(Mood.Cheering, NotifArt.Vibe.WIN, line.title, line.body)
                    )
                    .bigLargeIcon(null as android.graphics.Bitmap?)
                    .setBigContentTitle(line.title)
            )
            .setColor(0xFF3FA98D.toInt())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        val nm = NotificationManagerCompat.from(ctx)
        if (nm.areNotificationsEnabled()) nm.notify(RECAP_ID, n)
    }

    private fun scheduleNag(ctx: Context, med: Medication, slot: Long, tier: Tier) {
        val now = System.currentTimeMillis()
        val at =
            if (tier == Tier.SERIEUX) Slots.dayAfter(Slots.dayOf(now)) + 60_000L
            else now + med.nagEveryMinutes * 60_000L
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
    private fun scheduleIconUpdate(ctx: Context, vibe: NotifArt.Vibe, delayMs: Long) {
        val pi = PendingIntent.getBroadcast(
            ctx, ACTION_ICON.hashCode(),
            Intent(ctx, ReminderReceiver::class.java).apply {
                action = ACTION_ICON
                putExtra(EXTRA_VIBE, vibe.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        ctx.getSystemService(AlarmManager::class.java)
            .set(AlarmManager.RTC, System.currentTimeMillis() + delayMs, pi)
    }

    /**
     * Il y avait ici un compte à rebours du soir, posé à 21h, qui listait ce qui restait
     * à prendre avant minuit.
     *
     * Il a été retiré parce qu'il ne pouvait apparaître QUE par-dessus un rappel déjà
     * posé : il ne se déclenchait que s'il restait une dose due, et une dose due a
     * toujours son propre rappel permanent. Ça faisait donc systématiquement deux
     * notifications pour une seule pilule, l'une disant qu'il y en avait une à prendre et
     * l'autre étant celle de la pilule. Le compte à rebours, lui, n'a pas disparu : il est
     * passé sur le rappel lui-même, où il avait toujours eu sa place.
     *
     * Tear everything down for a medication being removed. The PendingIntent request code
     * is keyed on tagId + action and ignores the slot, so one cancel per action clears any
     * pending alarm whatever slot it was armed for.
     */
    fun cancelAll(ctx: Context, med: Medication) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val slot = Slots.todayAt(med)
        am.cancel(alarmPI(ctx, ACTION_DUE, med, slot))
        am.cancel(alarmPI(ctx, ACTION_NAG, med, slot))
        val nm = NotificationManagerCompat.from(ctx)
        nm.cancel(notifId(med.tagId))           // l'avance ET le rappel : même identifiant
        nm.cancel(notifId(med.tagId) + 1)       // la félicitation
        am.cancel(alarmPI(ctx, ACTION_SOON, med, slot))
    }

    fun cancelNag(ctx: Context, med: Medication, slot: Long) =
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmPI(ctx, ACTION_NAG, med, slot))

    // ---- the sticky dragon ------------------------------------------------

    /** Pose (ou remplace) le rappel, et rapporte le palier atteint à l'appelant. */
    fun post(ctx: Context, med: Medication, slot: Long, alert: Alert): Tier {
        ensureChannels(ctx)
        val now = System.currentTimeMillis()
        val lateMin = ((now - slot) / 60_000L).coerceAtLeast(0)
        val (tier, line) = FloMessages.line(lateMin, slot, med.name)
        val mood = tier.mood
        val vibe = NotifArt.vibeFor(tier)

        // Le bruit s'arrête avant le rappel. Passé le dernier palier il n'y a plus rien à
        // annoncer — l'escalade est finie, le dragon a dit ce qu'il avait à dire — et la
        // nuit, une relance sonore n'obtient rien qu'une app dont on coupe les
        // notifications. La notification, elle, reste posée dans les deux cas.
        val aloud = when (alert) {
            Alert.DUE -> true
            Alert.NAG -> tier != Tier.SERIEUX && !quietHours(now)
            Alert.SILENT -> false
        }

        IconSwitcher.apply(ctx, vibe)

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
        // Le décor monte le même escalier que le texte : c'est le palier qui décide des
        // deux, donc la couleur et le ton ne peuvent pas escalader à des minutes
        // différentes.
        val banner = NotifArt.banner(mood, vibe, line.title, line.body)

        // Le compte à rebours va jusqu'au HAUT DE L'ÉCHELLE, pas jusqu'à minuit.
        //
        // Minuit était une échéance que le rappel ne tenait pas : rien de particulier ne
        // se produisait à zéro, et une horloge qui égrène six heures ne presse personne.
        // Deux heures, c'est le moment où le dragon cesse de plaisanter — une échéance
        // réelle, dont on voit la conséquence arriver. Une fois passée il n'y a plus rien
        // à décompter, alors l'horloge disparaît plutôt que de compter à l'envers.
        val deadline = slot + ESCALATION_MS
        val counting = now < deadline

        val n = NotificationCompat.Builder(ctx, CHANNEL_NAG)
            .setSmallIcon(R.drawable.ic_stat_dragon)
            .setLargeIcon(face)
            .setContentTitle(line.title)
            .setContentText(line.body)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(banner)
                    .bigLargeIcon(null as android.graphics.Bitmap?)  // banner takes over when expanded
                    // Pas de résumé : il se poserait, coupé à une ligne, juste au-dessus
                    // d'une image qui dit déjà la même chose en entier.
                    .setBigContentTitle(line.title)
            )
            .setColor(if (tier == Tier.SERIEUX) Dragon.Plum else Dragon.Pink)
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!aloud)
            // Tenu par le système : il reste juste sans que l'app se réveille une fois.
            // Duolingo ne dit pas « il te reste du temps », il le montre fondre.
            .setShowWhen(counting)
            .setWhen(if (counting) deadline else now)
            .setUsesChronometer(counting)
            .setChronometerCountDown(counting)
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
            // La preuve qu'on a bel et bien prévenu. Écrite ici et nulle part ailleurs :
            // c'est le seul endroit où la notification est réellement remise au système.
            // Le jour où l'alarme ne part pas, cette ligne manque, et [silentMisses] le
            // voit. Une relance réécrit la même ligne — un rappel par créneau, pas un
            // par sonnerie.
            val app = ctx.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                Db.get(app).dao().recordPost(
                    ReminderPost(med.tagId, slot, System.currentTimeMillis())
                )
            }
        }

        // La tuile monte le même escalier, et elle ne se redessine d'elle-même qu'une
        // fois par demi-heure — le plancher qu'Android impose à `updatePeriodMillis`.
        // Sans ça le fond restait bleu pendant que le rappel en était déjà au bordeaux.
        // Ici on est déjà réveillé par la relance, donc ça ne coûte rien de plus.
        DragonWidget.refresh(ctx)
        return tier
    }

    /** Fires the instant a dose is logged -- scanned or ticked off: dragon celebrates, nagging stops. */
    /** Housekeeping after a dose lands: silence the nag, arm tomorrow, calm the icon. */
    fun resolve(ctx: Context, med: Medication, slot: Long) {
        ensureChannels(ctx)
        NotificationManagerCompat.from(ctx).cancel(notifId(med.tagId))
        cancelNag(ctx, med, slot)
        scheduleNext(ctx, med)
        scheduleIconUpdate(ctx, NotifArt.Vibe.REST_DAY, 60_000L)
        // Une dose notée hors de l'app (une étiquette scannée, téléphone rangé) ne passe
        // par aucun observateur : sans ça la tuile resterait rouge jusqu'à la prochaine
        // demi-heure alors que la dose est prise.
        DragonWidget.refresh(ctx)
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
                    .bigPicture(NotifArt.banner(Mood.Cheering, NotifArt.Vibe.WIN, line.title, line.body))
                    .bigLargeIcon(null as android.graphics.Bitmap?)
                    .setBigContentTitle(line.title)
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
        val dao = Db.get(ctx).dao()
        dao.activeMedsOnce().forEach { scheduleNext(ctx, it) }
        scheduleRecap(ctx)
        // La trace des rappels ne sert qu'à regarder la semaine écoulée : au-delà d'un
        // mois elle ne répond plus à aucune question et n'est plus qu'une table qui gonfle.
        dao.prunePosts(Slots.dayStart(30))
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
                        val tier = post(
                            ctx, med, slot,
                            if (action == ACTION_DUE) Alert.DUE else Alert.NAG
                        )
                        scheduleNag(ctx, med, slot, tier)
                    }
                    ACTION_REPOST -> post(ctx, med, slot, Alert.SILENT)
                }
            } finally { done() }
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action == Reminders.ACTION_SOON) {
            val tag = intent.getStringExtra(Reminders.EXTRA_TAG_ID) ?: return
            val slot = intent.getLongExtra(Reminders.EXTRA_SLOT, 0L)
            val app = ctx.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                Db.get(app).dao().med(tag)?.let { Reminders.soon(app, it, slot) }
            }
            return
        }

        if (action == Reminders.ACTION_RECAP) {
            Reminders.postRecap(ctx.applicationContext)
            return
        }

        if (action == Reminders.ACTION_ICON) {
            val vibe = runCatching {
                NotifArt.Vibe.valueOf(intent.getStringExtra(Reminders.EXTRA_VIBE)!!)
            }.getOrNull() ?: return
            IconSwitcher.apply(ctx.applicationContext, vibe)
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
