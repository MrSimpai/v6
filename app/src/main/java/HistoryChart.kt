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
import com.example.medtap.data.DoseLog
import com.example.medtap.data.driftMinutes
import com.example.medtap.data.Medication
import com.example.medtap.data.Slots
import java.util.Calendar
import kotlin.math.abs

data class DayPoint(val label: String, val slot: Long, val log: DoseLog?)

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
            byDay[Slots.dayOf(c.timeInMillis)]
        )
    }.filter { Slots.dayOf(it.slot) >= born }
}

/**
 * The drift plot: x is the day, y is the clock time you actually took it, and the dashed
 * line is when you meant to. A count-of-doses bar chart would only tell you "yes/no" --
 * this shows the drift that predicts a miss before it happens.
 */
@Composable
fun DriftChart(points: List<DayPoint>, targetLabel: String, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val spanMin = 180f   // plot +/- 3 hours around the target time

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("QUAND TU L'AS PRIS", style = Type.Label, color = Pal.Muted)
            Text(targetLabel, style = Type.Label, color = Pal.Iris)
        }
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(190.dp)) {
            plot(points, spanMin, tm)
        }
    }
}

private fun DrawScope.plot(points: List<DayPoint>, spanMin: Float, tm: TextMeasurer) {
    if (points.isEmpty()) return
    val padL = 46f
    val padB = 30f
    val w = size.width - padL
    val h = size.height - padB
    val midY = h / 2f
    val stepX = w / points.size
    fun x(i: Int) = padL + stepX * (i + 0.5f)
    fun y(driftMin: Int) = midY + (driftMin.coerceIn(-spanMin.toInt(), spanMin.toInt()) / spanMin) * (h / 2f - 14f)

    // horizontal guides at -2h / target / +2h
    listOf(-120 to "-2 h", 0 to "à l'heure", 120 to "+2 h").forEach { (d, label) ->
        val yy = y(d)
        val onTime = d == 0
        drawLine(
            color = if (onTime) Pal.Iris.copy(alpha = 0.45f) else Pal.IrisSoft,
            start = Offset(padL, yy), end = Offset(size.width, yy),
            strokeWidth = if (onTime) 2f else 1f,
            pathEffect = if (onTime) PathEffect.dashPathEffect(floatArrayOf(8f, 8f)) else null
        )
        val txt = tm.measure(label, Type.Label.copy(color = if (onTime) Pal.Iris else Pal.Muted))
        drawText(txt, topLeft = Offset(0f, yy - txt.size.height / 2f))
    }

    // connect the taken doses so the trend is visible
    val taken = points.mapIndexedNotNull { i, p -> p.log?.let { i to it.driftMinutes } }
    taken.zipWithNext { (i1, d1), (i2, d2) ->
        drawLine(
            Pal.Mint.copy(alpha = 0.55f), Offset(x(i1), y(d1)), Offset(x(i2), y(d2)),
            strokeWidth = 2.5f, cap = StrokeCap.Round
        )
    }

    points.forEachIndexed { i, p ->
        val cx = x(i)
        if (p.log != null) {
            val late = abs(p.log.driftMinutes) > 60
            drawCircle(if (late) Pal.Butter else Pal.Mint, 7f, Offset(cx, y(p.log.driftMinutes)))
            drawCircle(Pal.Card, 3f, Offset(cx, y(p.log.driftMinutes)))
        } else {
            // a miss sits on the target line as a hollow ring, so gaps are impossible to skim past
            drawCircle(
                Pal.Apricot.copy(alpha = 0.8f), 6f, Offset(cx, y(0)),
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
