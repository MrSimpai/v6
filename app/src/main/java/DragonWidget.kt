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
import com.example.medtap.data.outstandingToday
import com.example.medtap.ui.Dragon
import com.example.medtap.ui.Mood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Le dragon sur l'écran d'accueil, avec un bouton qui note la dose sans ouvrir l'app.
 *
 * C'est le raccourci le plus court qui existe entre « j'y pense » et « c'est noté » : deux
 * secondes, sans lancer quoi que ce soit. Le rappel le plus efficace est celui qu'on peut
 * satisfaire depuis l'endroit où on le voit.
 *
 * Le widget se redessine à chaque changement de données, via [refresh].
 */
class DragonWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(ctx, mgr, ids)
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        super.onReceive(ctx, intent)
        if (intent.action == ACTION_LOG) {
            val app = ctx.applicationContext
            CoroutineScope(Dispatchers.IO).launch {
                val dao = Db.get(app).dao()
                val meds = dao.activeMedsOnce()
                // La plus en retard d'abord : c'est celle qu'on avait en tête en tapant.
                val target = dao.outstandingToday(meds)
                    .filter { Slots.canLogNow(it) }
                    .minByOrNull { Slots.todayAt(it) } ?: return@launch
                Reminders.logFromOutside(app, target)
                refresh(app)
            }
        }
    }

    companion object {
        const val ACTION_LOG = "com.example.medtap.WIDGET_LOG"

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

                val label = when {
                    owed.isEmpty() && meds.isEmpty() -> "Aucun médicament"
                    owed.isEmpty() -> "Tout est à jour"
                    owed.size == 1 -> owed[0].name
                    else -> "${owed.size} doses"
                }

                val open = PendingIntent.getActivity(
                    app, 0,
                    Intent(app, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val log = PendingIntent.getBroadcast(
                    app, 1,
                    Intent(app, DragonWidget::class.java).setAction(ACTION_LOG),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val views = RemoteViews(app.packageName, R.layout.widget_dragon).apply {
                    setImageViewBitmap(R.id.widget_dragon, Dragon.faceBitmap(220, mood, worn))
                    setTextViewText(R.id.widget_label, label)
                    setOnClickPendingIntent(R.id.widget_dragon, open)
                    setOnClickPendingIntent(R.id.widget_label, open)
                    setTextViewText(
                        R.id.widget_button,
                        if (owed.isEmpty()) "Ouvrir" else "Je l'ai prise"
                    )
                    setOnClickPendingIntent(
                        R.id.widget_button, if (owed.isEmpty()) open else log
                    )
                }
                ids.forEach { mgr.updateAppWidget(it, views) }
            }
        }
    }
}
