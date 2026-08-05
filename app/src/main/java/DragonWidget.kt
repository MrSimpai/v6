package com.example.medtap.reminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.medtap.MainActivity
import com.example.medtap.R
import com.example.medtap.data.Db
import com.example.medtap.data.Slots
import com.example.medtap.data.currentStreak
import com.example.medtap.data.outstandingToday
import com.example.medtap.ui.Dragon
import com.example.medtap.ui.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Le dragon sur l'écran d'accueil : son humeur, la série en cours, et la phrase écrite à
 * la main qui va avec.
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

                val moodText = when {
                    meds.isEmpty() -> "Ajoute un médicament pour commencer."
                    owed.size > 1 -> "${owed.size} doses t'attendent."
                    owed.size == 1 -> "${owed[0].name} t'attend."
                    else -> FloMessages.moodLine(mood)
                }

                val views = RemoteViews(app.packageName, R.layout.widget_dragon).apply {
                    // Le dragon entier plutôt que sa tête : c'est le seul endroit hors du casier
                    // où les bottes et le hoodie se voient, et une tenue qu'on ne croise
                    // jamais ne vaut pas la peine d'être gagnée.
                    setImageViewBitmap(R.id.widget_dragon, Dragon.bitmap(240, mood, worn))
                    setImageViewBitmap(R.id.widget_bg, NotifArt.widgetBg(400, mood))
                    setTextViewText(R.id.widget_mood, moodText)

                    if (streak > 0) {
                        setViewVisibility(R.id.widget_streak, View.VISIBLE)
                        setTextViewText(
                            R.id.widget_streak,
                            // Le carré est petit : le chiffre seul, en pastille sur le
                            // dragon. « jours » n'apprend rien à qui regarde un compteur.
                            "$streak"
                        )
                        setTextViewText(R.id.widget_line, FloMessages.dayStreakLine(streak))
                        setViewVisibility(R.id.widget_line, View.VISIBLE)
                    } else {
                        setViewVisibility(R.id.widget_streak, View.GONE)
                        setViewVisibility(R.id.widget_line, View.GONE)
                    }

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
                    setOnClickPendingIntent(R.id.widget_mood, open)
                }
                ids.forEach { mgr.updateAppWidget(it, views) }
            }
        }
    }
}
