package com.example.medtap.reminder

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Android can't repaint a launcher icon at runtime, but it can swap which
 * <activity-alias> is the enabled launcher entry. Exactly one is on at any moment, so
 * the icon on Flo's home screen reflects the dragon's current mood.
 *
 * The thing to understand before touching this: `setComponentEnabledSetting` makes the
 * launcher re-query the package, and a lot of OEM launchers force-stop the app when that
 * happens -- `DONT_KILL_APP` is a request, not a guarantee. If it fires while she is
 * looking at the app, the app vanishes under her and it looks like the phone crashed.
 *
 * So [apply] is only ever called from ReminderReceiver, never from the UI. When a dose
 * is logged in the app, Reminders schedules an ACTION_ICON alarm a minute out instead of
 * repainting on the spot -- late enough that she has left, and that the launcher is idle
 * rather than mid-animation.
 *
 * There is deliberately no "just logged" icon. It would be a four-second state on a
 * surface she isn't even looking at, bought at the price of two swaps in four seconds.
 */
object IconSwitcher {

    /**
     * L'icône monte la même échelle que la tuile et que la bannière — c'est le principe
     * de Duolingo, où l'icône elle-même vire quand la série est en jeu. Quatre marches
     * seulement : un changement d'icône coûte un redessin du lanceur, donc il ne suit pas
     * les huit ambiances, il suit le fait qu'il y ait quelque chose à faire et depuis
     * combien de temps.
     */
    private val aliases = mapOf(
        NotifArt.Vibe.REST_DAY   to ".Calm",
        NotifArt.Vibe.REST_NIGHT to ".Calm",
        NotifArt.Vibe.WIN        to ".Calm",   // dose prise : plus rien n'est dû
        NotifArt.Vibe.DUE        to ".Waiting",
        NotifArt.Vibe.NUDGE      to ".Waiting",
        NotifArt.Vibe.SULK       to ".Late",
        NotifArt.Vibe.DRAMA      to ".Late",
        NotifArt.Vibe.ANGRY      to ".Overdue"
    )

    /** Every alias we own, including the retired ".Happy", so a stale one gets cleared. */
    private val allAliases = listOf(".Calm", ".Waiting", ".Late", ".Overdue", ".Happy")

    fun apply(ctx: Context, vibe: NotifArt.Vibe) {
        val want = aliases[vibe] ?: return
        val pm = ctx.packageManager
        val pkg = ctx.packageName
        val wanted = ComponentName(pkg, pkg + want)

        if (pm.getComponentEnabledSetting(wanted) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return  // already showing, don't churn the launcher
        }

        // Enable the one we want first, so there is never a moment with zero launchers.
        runCatching {
            pm.setComponentEnabledSetting(
                wanted,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            allAliases.filter { it != want }.forEach { other ->
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, pkg + other),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
