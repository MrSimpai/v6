package com.example.medtap.reminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.medtap.MainActivity
import com.example.medtap.R
import com.example.medtap.data.Db
import com.example.medtap.data.Slots
import com.example.medtap.data.currentStreak
import com.example.medtap.data.outstandingToday
import com.example.medtap.data.weekStatus
import com.example.medtap.ui.Dragon
import com.example.medtap.ui.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Le dragon sur l'écran d'accueil : son humeur, la série en cours, la semaine, et une
 * phrase courte.
 *
 * Il ne fait rien d'autre que montrer. Noter une dose se fait dans l'app, où l'on peut
 * confirmer, célébrer et ouvrir un coffre — un bouton sur l'écran d'accueil enregistrerait
 * la même chose en escamotant tout ce qui donne envie de recommencer demain. Un widget qui
 * agit ferait gagner deux secondes et perdre le rituel.
 *
 * Le widget se redessine à chaque changement de données, via [refresh].
 */
class DragonWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(ctx, mgr, ids)
    }

    companion object {

        /**
         * Un `RemoteViews` voyage dans un Binder plafonné à un mégaoctet, images
         * comprises. Ces quatre tailles pèsent ensemble un peu plus de la moitié du
         * budget ; les augmenter fait tomber le widget en silence sur les grandes
         * grilles, où le lanceur en demande plusieurs à la fois.
         */
        private const val BG_PX = 320
        private const val DRAGON_PX = 168
        private const val PILL_PX = 56
        private const val WEEK_W = 168
        private const val WEEK_H = 24

        /** Redessine tous les widgets posés. Sans effet s'il n'y en a aucun. */
        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, DragonWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val app = ctx.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val dao = Db.get(app).dao()
                val meds = dao.activeMedsOnce()
                val worn = dao.cosmeticsOnce().filter { it.equipped }.map { it.id }.toSet()
                val week = dao.weekStatus(meds).map { it.ordinal }
                val now = System.currentTimeMillis()

                val owed = dao.outstandingToday(meds).filter { Slots.todayAt(it) <= now }
                val lateMin = owed.maxOfOrNull { (now - Slots.todayAt(it)) / 60_000L } ?: 0L
                val mood = when {
                    owed.isEmpty() -> Mood.Sleeping
                    lateMin >= 120 -> Mood.Overdue
                    lateMin >= 60 -> Mood.Sad
                    else -> Mood.Waiting
                }

                // La série telle qu'elle se lit maintenant, pas celle d'aujourd'hui :
                // sinon le compteur tomberait à zéro chaque matin.
                val streak = dao.currentStreak(meds)

                // La ligne écrite à la main, en grand : c'est le contenu de la tuile.
                // Elle change tous les jours, alors que le dessin est le même du matin au
                // soir — c'est donc elle qui mérite la place, pas le dragon.
                val message = when {
                    meds.isEmpty() -> "Ajoute un médicament pour commencer 🐉"
                    streak > 0 -> FloMessages.dayStreakLine(streak)
                    else -> FloMessages.moodLine(mood)
                }

                // Et en dessous, en petit, ce qu'il y a à faire maintenant. Deux registres
                // séparés : le journal ne doit jamais avoir à dire aussi l'heure qu'il est.
                val moodText = when {
                    meds.isEmpty() -> ""
                    owed.size > 1 -> "${owed.size} doses t'attendent"
                    owed.size == 1 -> owed[0].name
                    else -> FloMessages.widgetLine(mood)
                }

                // Le décor suit deux choses à la fois : le palier de retard, et la pendule.
                // Sans la pendule, la tuile du repos affichait une lune à quinze heures.
                val night = NotifArt.isNight(now)
                val vibe = NotifArt.vibeFor(mood, lateMin, now)

                val views = RemoteViews(app.packageName, R.layout.widget_dragon).apply {
                    // Le dragon entier plutôt que sa tête : c'est le seul endroit hors du casier
                    // où les bottes et le hoodie se voient, et une tenue qu'on ne croise
                    // jamais ne vaut pas la peine d'être gagnée.
                    setImageViewBitmap(R.id.widget_dragon, Dragon.bitmap(DRAGON_PX, mood, worn))
                    setImageViewBitmap(R.id.widget_bg, NotifArt.widgetBg(BG_PX, vibe, night))
                    setImageViewBitmap(R.id.widget_week, NotifArt.weekStrip(WEEK_W, WEEK_H, week))
                    // La pastille reste posée même à zéro, éteinte : un emplacement qui
                    // apparaît et disparaît fait sauter toute la tuile d'un jour à l'autre,
                    // et une flamme grise à côté d'un zéro dit ce qu'il y a à dire.
                    setImageViewBitmap(R.id.widget_streak, NotifArt.streakPill(PILL_PX, streak))

                    setTextViewText(R.id.widget_message, message)
                    setTextViewText(R.id.widget_mood, moodText)

                    // Tout le widget ouvre l'app : il n'y a qu'une seule chose à y faire.
                    val open =
                        PendingIntent.getActivity(
                            app, 0,
                            Intent(app, MainActivity::class.java).addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            ),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    setOnClickPendingIntent(R.id.widget_bg, open)
                    setOnClickPendingIntent(R.id.widget_dragon, open)
                    setOnClickPendingIntent(R.id.widget_message, open)
                    setOnClickPendingIntent(R.id.widget_mood, open)
                    setOnClickPendingIntent(R.id.widget_streak, open)
                }
                ids.forEach { mgr.updateAppWidget(it, views) }
            }
        }
    }
}
