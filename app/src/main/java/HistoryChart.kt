package com.example.medtap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
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

/** L'heure murale d'une prise, en minutes depuis minuit. */
private fun minuteOfDay(millis: Long): Int = Calendar.getInstance().apply {
    timeInMillis = millis
}.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

/** « 6 h » ou « 6 h 30 » — l'heure ronde se lit mieux sans ses deux zéros. */
private fun clockLabel(minuteOfDay: Int): String {
    val h = minuteOfDay / 60
    val m = minuteOfDay % 60
    return if (m == 0) "${h}h" else String.format(Locale.CANADA_FRENCH, "%dh%02d", h, m)
}

/** Combien de marge on laisse de part et d'autre de la plage. */
private const val MARGIN_MIN = 120

/**
 * À quelle heure la dose a été prise, jour après jour, contre la plage prévue.
 *
 * L'axe couvre la PLAGE plus deux heures de chaque côté. Une pilule réglée de 6 h à 8 h
 * donne donc un graphique de 4 h à 10 h. Avant, l'axe était un écart au créneau, figé à
 * plus ou moins deux heures autour d'un point : la plage n'y apparaissait nulle part, et
 * une prise à 8 h — parfaitement dans les clous — se lisait « deux heures de retard ».
 *
 * Un graphique en barres du nombre de doses ne dirait que « oui / non ». Celui-ci montre
 * la dérive, qui annonce l'oubli avant qu'il arrive.
 */
@Composable
fun DoseChart(points: List<DayPoint>, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    if (points.isEmpty()) return

    // Les bornes viennent des plages réellement affichées, pas d'aujourd'hui : sur un
    // horaire réglé jour par jour, l'axe doit contenir la semaine entière.
    var lo = points.minOf { it.window.startMinute } - MARGIN_MIN
    var hi = points.maxOf { if (it.window.instant) it.window.startMinute else it.window.endMinute } +
        MARGIN_MIN

    // Une prise très hors plage étire l'axe plutôt que d'être écrasée sur le bord. Un point
    // collé à la bordure prétendrait une heure qui n'est pas la sienne.
    points.mapNotNull { it.log }.forEach {
        val m = minuteOfDay(it.takenAt)
        if (m < lo) lo = m
        if (m > hi) hi = m
    }
    lo = lo.coerceAtLeast(0)
    hi = hi.coerceAtMost(24 * 60)

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("QUAND TU L'AS PRIS", style = Type.Label, color = Pal.Muted)
            Text(
                points.last().window.let {
                    if (it.instant) clockLabel(it.startMinute)
                    else "${clockLabel(it.startMinute)} – ${clockLabel(it.endMinute)}"
                },
                style = Type.Label, color = Pal.Iris
            )
        }
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            plot(points, lo, hi, tm)
        }
    }
}

private fun DrawScope.plot(points: List<DayPoint>, lo: Int, hi: Int, tm: TextMeasurer) {
    val padL = 46f
    val padB = 30f
    val padT = 10f
    val w = size.width - padL
    val h = size.height - padB
    val span = (hi - lo).coerceAtLeast(1).toFloat()
    val stepX = w / points.size

    fun x(i: Int) = padL + stepX * (i + 0.5f)
    // Plus tard dans la journée = plus bas à l'écran, comme une horloge qu'on lit de haut
    // en bas.
    fun y(minute: Int) =
        padT + ((minute.coerceIn(lo, hi) - lo) / span) * (h - padT)

    // La plage, colonne par colonne : c'est la zone où la dose est « à l'heure ». Une
    // bande par jour plutôt qu'un bandeau continu, parce que l'horaire peut changer d'un
    // jour à l'autre — et alors la bande le montre au lieu de le cacher.
    points.forEachIndexed { i, p ->
        val top = y(p.window.startMinute)
        val bottom = if (p.window.instant) top + 2f else y(p.window.endMinute)
        drawRoundRect(
            color = Pal.IrisSoft,
            topLeft = Offset(x(i) - stepX * 0.34f, top),
            size = Size(stepX * 0.68f, (bottom - top).coerceAtLeast(2f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
        )
    }

    // Les repères : le début et la fin de la plage, puis les deux bornes de l'axe.
    val ref = points.last().window
    val guides = buildList {
        add(ref.startMinute to true)
        if (!ref.instant) add(ref.endMinute to true)
        add(lo to false)
        add(hi to false)
    }.distinctBy { it.first }

    guides.forEach { (minute, isEdge) ->
        val yy = y(minute)
        drawLine(
            color = if (isEdge) Pal.Iris.copy(alpha = 0.45f) else Pal.IrisSoft,
            start = Offset(padL, yy), end = Offset(size.width, yy),
            strokeWidth = if (isEdge) 2f else 1f,
            pathEffect = if (isEdge) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null
        )
        val txt = tm.measure(
            clockLabel(minute),
            Type.Label.copy(color = if (isEdge) Pal.Iris else Pal.Muted)
        )
        drawText(txt, topLeft = Offset(0f, yy - txt.size.height / 2f))
    }

    // La ligne qui relie les prises : c'est elle qui rend la dérive visible.
    val taken = points.mapIndexedNotNull { i, p -> p.log?.let { i to minuteOfDay(it.takenAt) } }
    taken.zipWithNext { (i1, m1), (i2, m2) ->
        drawLine(
            Pal.Mint.copy(alpha = 0.55f), Offset(x(i1), y(m1)), Offset(x(i2), y(m2)),
            strokeWidth = 2.5f, cap = StrokeCap.Round
        )
    }

    points.forEachIndexed { i, p ->
        val cx = x(i)
        if (p.log != null) {
            val m = minuteOfDay(p.log.takenAt)
            // « En retard » se juge par rapport à la FIN de la plage, pas à son début :
            // c'est tout l'intérêt d'avoir une plage.
            val limit = if (p.window.instant) p.window.startMinute else p.window.endMinute
            val late = m > limit || m < p.window.startMinute - MARGIN_MIN
            drawCircle(if (late) Pal.Butter else Pal.Mint, 7f, Offset(cx, y(m)))
            drawCircle(Pal.Card, 3f, Offset(cx, y(m)))
        } else {
            // Un oubli se pose en anneau creux au début de la plage : un trou dans la
            // courbe doit être impossible à survoler sans le voir.
            drawCircle(
                Pal.Apricot.copy(alpha = 0.8f), 6f, Offset(cx, y(p.window.startMinute)),
                style = Stroke(width = 2f)
            )
        }
        val lbl = tm.measure(p.label, Type.Label.copy(color = Pal.Muted))
        drawText(lbl, topLeft = Offset(cx - lbl.size.width / 2f, h + 8f))
    }
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
