package com.example.medtap.reminder

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.medtap.ui.Mood

/**
 * Android can't repaint a launcher icon at runtime, but it can swap which
 * <activity-alias> is the enabled launcher entry. Exactly one is on at any moment, so
 * the icon on Flo's home screen reflects the dragon's current mood.
 *
 * Two caveats worth knowing before you rely on this:
 *  - some launchers briefly drop the icon during the swap, and a few reset its home
 *    screen position, so only switch when the state genuinely changes (guarded below);
 *  - the app is force-stopped by some OEM launchers on alias change, which is harmless
 *    here because all state lives in Room and AlarmManager.
 */
object IconSwitcher {

    private val aliases = mapOf(
        Mood.Sleeping to ".Calm",
        Mood.Waiting  to ".Waiting",
        Mood.Overdue  to ".Overdue",
        Mood.Cheering to ".Happy"
    )

    fun apply(ctx: Context, mood: Mood) {
        val want = aliases[mood] ?: aliases[Mood.Sleeping]!!
        val pm = ctx.packageManager
        val pkg = ctx.packageName

        val current = ComponentName(pkg, pkg + want)
        if (pm.getComponentEnabledSetting(current) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            return  // already showing, don't churn the launcher
        }

        // Enable the one we want first, so there is never a moment with zero launchers.
        pm.setComponentEnabledSetting(
            current,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        aliases.values.filter { it != want }.forEach { other ->
            pm.setComponentEnabledSetting(
                ComponentName(pkg, pkg + other),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
