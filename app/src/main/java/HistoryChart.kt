package com.example.medtap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medtap.data.DayWindow
import com.example.medtap.data.DoseLog
import com.example.medtap.data.Medication
import com.example.medtap.data.Slots
import com.example.medtap.data.Week
import com.example.medtap.data.windowOn
import java.util.Calendar
import java.util.Locale

/**
 * Une journée du graphique.
 *
 * [window] est la plage de CE jour-là et non celle d'aujourd'hui : avec un horaire réglé
 * jour par jour, la cible du samedi n'est pas celle du mardi, et un graphique qui les
 * mesurerait toutes à l'aune du mardi montrerait un retard qui n'a jamais existé.
 */
data class DayPoint(
    val label: String,
    val slot: Long,
    val log: DoseLog?,
    val window: DayWindow
)

/** Builds the last [days] slots for a medication, paired with the log that filled each. */
fun buildDays(med: Medication, logs: List<DoseLog>, days: Int = 14): List<DayPoint> {
    // Les doses sautées comptent pour la série mais pas pour le graphique de dérive :
    // tracer une heure de prise pour une dose jamais prise inventerait une donnée.
    // Rangées par journée et non par horodatage de créneau : une dose garde l'heure
    // qu'avait le médicament le jour où elle a été prise, et changer l'heure du rappel
    // effacerait sinon toute la courbe d'un coup.
    val byDay = logs.filter { it.tagId == med.tagId && !it.skipped }
        .associateBy { Slots.dayOf(it.scheduledFor) }
    val today = Slots.todayAt(med)
    val names = arrayOf("D", "L", "M", "M", "J", "V", "S")
    // Les jours d'avant l'arrivée du médicament ne sont pas des jours manqués : rien
    // n'était attendu d'eux. La série le sait déjà (voir `dueOn`), mais la bande et le
    // graphique, eux, les dessinaient en ronds creux et annonçaient « 3 jours sur 14 » à
    // quelqu'un qui n'en avait jamais raté un seul.
    val born = Slots.dayOf(med.createdAt)
    return (days - 1 downTo 0).map { back ->
        val c = Calendar.getInstance().apply {
            timeInMillis = today; add(Calendar.DAY_OF_YEAR, -back)
        }
        DayPoint(
            names[c.get(Calendar.DAY_OF_WEEK) - 1],
            c.timeInMillis,
            byDay[Slots.dayOf(c.timeInMillis)],
            med.windowOn(Week.index(c.get(Calendar.DAY_OF_WEEK)))
        )
    }.filter { Slots.dayOf(it.slot) >= born }
}

/**
 * L'heure d'une prise, en minutes depuis le minuit de SA journée.
 *
 * La journée se referme à 3h du matin : une pilule d'hier prise à 1h30 vaut donc
 * 25 h 30 et se pose sous la plage d'hier soir, au lieu de remonter tout en haut du
 * graphique comme si elle avait été prise à l'aube.
 */
private fun minuteOfDay(log: DoseLog): Int {
    val c = Calendar.getInstance().apply { timeInMillis = log.takenAt }
    val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    return if (Slots.dayOf(log.takenAt) > Slots.dayOf(log.scheduledFor)) m + 24 * 60 else m
}

/** « 6h » ou « 6h30 » — l'heure ronde se lit mieux sans ses deux zéros. */
private fun clockLabel(minuteOfDay: Int): String {
    val wrapped = minuteOfDay % (24 * 60)
    val h = wrapped / 60
    val m = wrapped % 60
    return if (m == 0) "${h}h" else String.format(Locale.CANADA_FRENCH, "%dh%02d", h, m)
}

/** La dernière minute où la dose est encore « à l'heure » ce jour-là. */
private fun DayWindow.limit(): Int = if (instant) startMinute else endMinute

/** Combien de marge on laisse de part et d'autre de la plage. */
private const val MARGIN_MIN = 60

/** Combien de jours le graphique montre. Une semaine se lit d'un coup d'œil ; deux, non. */
private const val CHART_DAYS = 7

/**
 * À quelle heure la dose a été prise, ces sept derniers jours.
 *
 * Pensé pour se lire en une seconde, sans axe à déchiffrer :
 *  - une phrase en haut dit l'essentiel (« 6 sur 7 à l'heure », « en moyenne vers 8h15 ») ;
 *  - chaque jour est une colonne arrondie, avec la plage « à l'heure » peinte en vert ;
 *  - la prise est une grosse pastille — verte dans la plage, abricot en retard — avec
 *    l'heure écrite juste au-dessus, donc on n'a jamais à la deviner ;
 *  - un oubli est un petit rond pointillé au bas de la colonne ;
 *  - aujourd'hui a sa lettre dans une pastille framboise.
 *
 * Plus tard dans la journée = plus bas, comme une horloge qu'on lit de haut en bas.
 */
@Composable
fun DoseChart(allPoints: List<DayPoint>, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val points = allPoints.takeLast(CHART_DAYS)
    if (points.isEmpty()) return

    val taken = points.mapNotNull { p -> p.log?.let { minuteOfDay(it) to p.window } }
    val onTime = taken.count { (m, w) -> m <= w.limit() }

    // Les bornes viennent des plages réellement affichées, pas d'aujourd'hui : sur un
    // horaire réglé jour par jour, l'axe doit contenir la semaine entière. Une prise très
    // hors plage étire l'axe plutôt que d'être écrasée sur le bord.
    val lo = (minOf(points.minOf { it.window.startMinute }, taken.minOfOrNull { it.first } ?: Int.MAX_VALUE) -
        MARGIN_MIN).coerceAtLeast(0)
    val hi = (maxOf(points.maxOf { it.window.limit() }, taken.maxOfOrNull { it.first } ?: 0) +
        MARGIN_MIN).coerceAtMost(Slots.LATEST_END_MINUTE)

    Column(modifier) {
        Text("QUAND TU L'AS PRISE", style = Type.Label, color = Pal.Muted)
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                taken.isEmpty() -> "Pas encore de prise cette semaine 🌱"
                onTime == taken.size -> "Toujours à l'heure! 🎯"
                else -> "$onTime sur ${taken.size} à l'heure"
            },
            style = Type.Title, color = Pal.Ink
        )
        if (taken.isNotEmpty()) {
            Text(
                "En moyenne vers ${clockLabel(taken.map { it.first }.average().toInt())}",
                style = Type.Body, color = Pal.Muted
            )
        }
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.fillMaxWidth().height(236.dp)) {
            plot(points, lo, hi, tm)
        }
        Spacer(Modifier.height(10.dp))
        Legend()
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendDot(Pal.Mint, "à l'heure")
        Spacer(Modifier.width(16.dp))
        LegendDot(Pal.Apricot, "en retard")
        Spacer(Modifier.width(16.dp))
        LegendDot(Pal.Muted.copy(alpha = 0.5f), "oubliée", hollow = true)
    }
}

@Composable
private fun LegendDot(color: Color, label: String, hollow: Boolean = false) {
    Canvas(Modifier.size(10.dp)) {
        if (hollow) drawCircle(color, size.minDimension / 2f - 1.5f, style = Stroke(width = 3f))
        else drawCircle(color)
    }
    Spacer(Modifier.width(5.dp))
    Text(label, style = Type.Label.copy(letterSpacing = 0.sp), color = Pal.Muted)
}

private fun DrawScope.plot(points: List<DayPoint>, lo: Int, hi: Int, tm: TextMeasurer) {
    val dayRow = 30.dp.toPx()            // la rangée des lettres, en bas
    val labelRoom = 32.dp.toPx()         // l'heure écrite au-dessus de la pastille du haut
    val top = labelRoom
    val bottom = size.height - dayRow
    val span = (hi - lo).coerceAtLeast(1).toFloat()
    val stepX = size.width / points.size
    val trackW = (stepX * 0.56f).coerceAtMost(30.dp.toPx())
    val dotR = (trackW / 2f).coerceAtMost(12.dp.toPx())
    val corner = CornerRadius(trackW / 2f, trackW / 2f)
    val todayStart = Slots.dayOf(System.currentTimeMillis())

    fun x(i: Int) = stepX * (i + 0.5f)
    fun y(minute: Int) = top + ((minute.coerceIn(lo, hi) - lo) / span) * (bottom - top)

    val timeStyle = Type.Label.copy(letterSpacing = 0.sp, fontSize = 11.sp)

    points.forEachIndexed { i, p ->
        val cx = x(i)

        // La colonne du jour, toute douce.
        drawRoundRect(
            color = Pal.IrisSoft.copy(alpha = 0.55f),
            topLeft = Offset(cx - trackW / 2f, top),
            size = Size(trackW, bottom - top),
            cornerRadius = corner
        )

        // La plage « à l'heure », en vert menthe. Une bande par jour plutôt qu'un bandeau
        // continu : si l'horaire change d'un jour à l'autre, la bande le montre.
        val wTop = y(p.window.startMinute)
        val wBottom = y(p.window.limit())
        drawRoundRect(
            color = Pal.MintSoft,
            topLeft = Offset(cx - trackW / 2f, wTop - dotR / 2f),
            size = Size(trackW, (wBottom - wTop) + dotR),
            cornerRadius = corner
        )

        val log = p.log
        if (log != null) {
            val m = minuteOfDay(log)
            val late = m > p.window.limit()
            val color = if (late) Pal.Apricot else Pal.Mint
            val cy = y(m)
            // Une petite ombre pour l'effet « bouton » des pastilles.
            drawCircle(color.copy(alpha = 0.25f), dotR + 3.dp.toPx(), Offset(cx, cy + 2.dp.toPx()))
            drawCircle(color, dotR, Offset(cx, cy))
            drawCheck(Offset(cx, cy), dotR * 0.5f)

            val txt = tm.measure(clockLabel(m), timeStyle.copy(color = if (late) Pal.Apricot else Pal.Mint))
            drawText(
                txt,
                topLeft = Offset(
                    (cx - txt.size.width / 2f).coerceIn(0f, maxOf(0f, size.width - txt.size.width)),
                    cy - dotR - txt.size.height - 2.dp.toPx()
                )
            )
        } else {
            // Un oubli : un petit rond pointillé tout en bas, discret mais impossible à
            // confondre avec une prise.
            drawCircle(
                Pal.Muted.copy(alpha = 0.45f), dotR * 0.7f,
                Offset(cx, bottom - dotR),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            )
        }

        // La lettre du jour. Aujourd'hui dans une pastille framboise, comme chez le hibou.
        val isToday = Slots.dayOf(p.slot) == todayStart
        val lbl = tm.measure(
            p.label,
            Type.Label.copy(letterSpacing = 0.sp, color = if (isToday) Color.White else Pal.Muted)
        )
        val ly = bottom + (dayRow - lbl.size.height) / 2f + 2.dp.toPx()
        if (isToday) {
            val pad = 7.dp.toPx()
            val w = maxOf(lbl.size.width + pad * 2, lbl.size.height + pad)
            drawRoundRect(
                Pal.Iris,
                topLeft = Offset(cx - w / 2f, ly - pad / 2f),
                size = Size(w, lbl.size.height + pad),
                cornerRadius = CornerRadius(w, w)
            )
        }
        drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, ly))
    }
}

/** Le petit crochet blanc dans la pastille. */
private fun DrawScope.drawCheck(c: Offset, r: Float) {
    val path = Path().apply {
        moveTo(c.x - r * 0.75f, c.y + r * 0.05f)
        lineTo(c.x - r * 0.2f, c.y + r * 0.6f)
        lineTo(c.x + r * 0.8f, c.y - r * 0.55f)
    }
    drawPath(
        path, Color.White,
        style = Stroke(width = r * 0.45f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

/** Compact streak strip: one rounded tick per day, filled when the dose was logged. */
@Composable
fun StreakStrip(points: List<DayPoint>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(34.dp)) {
        val gap = 5f
        val wEach = (size.width - gap * (points.size - 1)) / points.size
        points.forEachIndexed { i, p ->
            drawRoundRect(
                color = if (p.log != null) Pal.Mint else Pal.IrisSoft,
                topLeft = Offset(i * (wEach + gap), 0f),
                size = Size(wEach, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(wEach / 2, wEach / 2)
            )
        }
    }
}
