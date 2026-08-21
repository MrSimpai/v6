package com.example.medtap.reminder

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
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
import com.example.medtap.ui.Sky
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

    /**
     * Redessine la tuile qu'on vient d'étirer.
     *
     * Sans ça, redimensionner gardait l'ancien fond — aux anciennes proportions — jusqu'au
     * prochain réveil, c'est-à-dire jusqu'à une demi-heure plus tard. On voyait donc le
     * décor déformé pendant tout ce temps, ce qui donnait l'impression que c'était l'état
     * normal.
     */
    override fun onAppWidgetOptionsChanged(
        ctx: Context,
        mgr: AppWidgetManager,
        id: Int,
        newOptions: Bundle?
    ) {
        render(ctx, mgr, intArrayOf(id))
    }

    companion object {

        /**
         * Un `RemoteViews` voyage dans un Binder plafonné à un mégaoctet, images
         * comprises. Le fond suit maintenant la taille de la tuile, donc c'est lui qui
         * pourrait déraper : une tuile 4x4 sur un écran à 3x ferait un bitmap de près de
         * quatre mégaoctets, et le widget disparaîtrait en silence.
         *
         * D'où le plafond sur le grand côté. Le décor n'est que des dégradés et quelques
         * étincelles : agrandi deux fois, ça ne se voit pas. Du texte, oui ; un ciel, non.
         */
        private const val BG_MAX = 320
        private const val DRAGON_PX = 240
        // La pastille et la semaine sont dessinées à bien plus que leur taille d'écran :
        // ce sont les deux seuls chiffres de la tuile, et une gélule de 24 dp rendue à
        // 56 px se lit floue sur un écran à 3x.
        private const val PILL_PX = 96
        private const val WEEK_W = 320
        private const val WEEK_H = 48

        /** La taille par défaut d'une 2x2, quand le lanceur ne dit rien. */
        private const val FALLBACK_DP = 110

        /** Ce qu'il faut savoir d'une tuile pour la dessiner : sa forme, et la place du dragon. */
        private class Tile(
            val wPx: Int,
            val hPx: Int,
            val radiusPx: Float,
            val dragonDp: Float
        )

        /** Le plus grand arrondi qu'on s'autorise, proche de celui d'Android 12. */
        private const val RADIUS_MAX_DP = 20f

        /**
         * Mesure une tuile posée.
         *
         * `MIN_WIDTH` et `MAX_HEIGHT` sont ce que le lanceur rapporte pour l'orientation
         * courante ; c'est le couple qui décrit le mieux la tuile telle qu'elle est là,
         * maintenant. Le lanceur ne les remplit pas toujours, d'où le repli sur la 2x2.
         *
         * Le dragon prend un peu moins du tiers de la hauteur, borné des deux côtés : en
         * dessous de 42 dp il n'est plus lisible, au-delà de 80 dp il mange la phrase du
         * jour, qui est le contenu de la tuile.
         */
        private fun measure(ctx: Context, mgr: AppWidgetManager, id: Int): Tile {
            val o = mgr.getAppWidgetOptions(id)
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                .takeIf { it > 0 } ?: FALLBACK_DP
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
                .takeIf { it > 0 } ?: FALLBACK_DP

            val d = ctx.resources.displayMetrics.density
            val w = wDp * d
            val h = hDp * d
            val k = (BG_MAX / maxOf(w, h)).coerceAtMost(1f)

            // Le rayon est calculé sur la tuile réelle puis ramené à l'échelle du bitmap,
            // sinon le plafonnement se ferait sur une image déjà réduite et l'arrondi
            // rétrécirait avec elle.
            val radiusDp = (minOf(wDp, hDp) * 0.14f).coerceAtMost(RADIUS_MAX_DP)

            return Tile(
                wPx = (w * k).toInt().coerceAtLeast(1),
                hPx = (h * k).toInt().coerceAtLeast(1),
                radiusPx = radiusDp * d * k,
                dragonDp = (hDp * 0.30f).coerceIn(42f, 80f)
            )
        }

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
                // Le retard qui compte, plage horaire déduite : la tuile ne doit pas
                // afficher une tête catastrophée à 8h pour une dose qu'on a jusqu'à 10h
                // pour prendre.
                val lateMin = owed.maxOfOrNull {
                    Slots.pressureMinutes(it, Slots.todayAt(it), now)
                } ?: 0L
                // Le visage suit exactement les paliers du rappel : c'est [Tier] qui
                // décide, pas une deuxième liste de seuils. Sans ça la tuile et la
                // notification finiraient par montrer deux têtes différentes à la même
                // minute, ce qui est le genre d'écart qui fait douter de tout le reste.
                val mood =
                    if (owed.isEmpty()) Mood.Sleeping else Tier.forLateness(lateMin).mood

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

                // Le décor de la tuile est celui de l'app, pas un décor à lui : même
                // heure, même saison, même lune, même ciel. Deux systèmes de couleurs
                // pour une seule maison, c'était la garantie d'un couchant sur l'écran
                // d'accueil pendant que l'app était déjà à la nuit.
                val sky = Sky.moment(now)

                // Tout ce qui ne dépend pas de la TAILLE est dessiné une seule fois et
                // partagé par toutes les tuiles posées. Seul le fond change d'une à
                // l'autre, parce que lui seul doit épouser leurs proportions.
                val dragonBmp = Dragon.bitmap(DRAGON_PX, mood, worn)
                val weekBmp = NotifArt.weekStrip(WEEK_W, WEEK_H, week)
                val pillBmp = NotifArt.streakPill(PILL_PX, streak)

                // Tout le widget ouvre l'app : il n'y a qu'une seule chose à y faire.
                val open = PendingIntent.getActivity(
                    app, 0,
                    Intent(app, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                ids.forEach { id ->
                    val tile = measure(app, mgr, id)
                    val views = RemoteViews(app.packageName, R.layout.widget_dragon).apply {
                        // Le dragon entier plutôt que sa tête : c'est le seul endroit hors
                        // du casier où les bottes et le hoodie se voient, et une tenue
                        // qu'on ne croise jamais ne vaut pas la peine d'être gagnée.
                        setImageViewBitmap(R.id.widget_dragon, dragonBmp)
                        setImageViewBitmap(
                            R.id.widget_bg,
                            NotifArt.skyTile(tile.wPx, tile.hPx, tile.radiusPx, sky)
                        )
                        setImageViewBitmap(R.id.widget_week, weekBmp)
                        // La pastille reste posée même à zéro, éteinte : un emplacement qui
                        // apparaît et disparaît fait sauter toute la tuile d'un jour à
                        // l'autre, et une flamme grise à côté d'un zéro dit ce qu'il faut.
                        setImageViewBitmap(R.id.widget_streak, pillBmp)

                        setTextViewText(R.id.widget_message, message)
                        setTextViewText(R.id.widget_mood, moodText)

                        // Le dragon grandit avec la tuile. La mise en page XML ne sait pas
                        // faire ça — une taille en dp est la même sur une 2x2 et une 4x4 —
                        // et `setViewLayoutWidth` n'existe qu'à partir d'Android 12. En
                        // dessous, la tuile garde la taille écrite dans le XML, ce qui est
                        // exactement ce qu'elle faisait avant.
                        if (Build.VERSION.SDK_INT >= 31) {
                            setViewLayoutWidth(
                                R.id.widget_dragon, tile.dragonDp, TypedValue.COMPLEX_UNIT_DIP
                            )
                            setViewLayoutHeight(
                                R.id.widget_dragon, tile.dragonDp, TypedValue.COMPLEX_UNIT_DIP
                            )
                        }

                        setOnClickPendingIntent(R.id.widget_bg, open)
                        setOnClickPendingIntent(R.id.widget_dragon, open)
                        setOnClickPendingIntent(R.id.widget_message, open)
                        setOnClickPendingIntent(R.id.widget_mood, open)
                        setOnClickPendingIntent(R.id.widget_streak, open)
                    }
                    mgr.updateAppWidget(id, views)
                }
            }
        }
    }
}
