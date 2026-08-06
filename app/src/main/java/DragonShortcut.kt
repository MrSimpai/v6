package com.example.medtap.reminder

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.medtap.MainActivity
import com.example.medtap.R
import com.example.medtap.ui.Dragon
import com.example.medtap.ui.Mood

/**
 * Le dragon habillé, sur le lanceur.
 *
 * L'icône d'application elle-même ne peut PAS porter la tenue, et ce n'est pas un manque
 * de volonté : Android ne connaît que des ressources déclarées à la compilation. On peut
 * basculer entre plusieurs icônes prévues d'avance — c'est ce que fait [IconSwitcher] pour
 * l'humeur — mais aucune API publique ne permet de poser une image fabriquée à l'exécution
 * comme icône de lanceur. Avec cinquante-cinq pièces, il faudrait un dessin par
 * combinaison, ce qui se compte en millions.
 *
 * Le raccourci, lui, accepte un bitmap. C'est donc le seul endroit du lanceur où la vraie
 * tenue peut apparaître, dessinée par le même [Dragon] que l'app et la tuile. On appuie
 * longuement sur l'icône, et elle est là, avec son chapeau et sa peluche.
 *
 * Il mène au casier plutôt qu'à l'enregistrement d'une dose : noter une dose se fait dans
 * l'app, où il y a une confirmation, une célébration et un coffre.
 */
object DragonShortcut {

    private const val ID = "casier"

    /** Le bitmap est masqué comme une icône adaptative : un quart de marge est rogné. */
    private const val PX = 320
    private const val SAFE = 0.62f

    fun refresh(ctx: Context, worn: Set<String>) {
        val bmp = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFF43B183.toInt())

        val size = PX * SAFE
        c.save()
        c.translate((PX - size) / 2f, (PX - size) / 2f)
        Dragon.draw(c, Mood.Cheering, size, 0f, worn)
        c.restore()

        val open = Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val shortcut = ShortcutInfoCompat.Builder(ctx, ID)
            .setShortLabel(ctx.getString(R.string.shortcut_locker))
            .setIcon(IconCompat.createWithAdaptiveBitmap(bmp))
            .setIntent(open)
            .build()

        // Silencieux en cas d'échec : certains lanceurs refusent les raccourcis dynamiques,
        // et un raccourci est un agrément. Faire tomber l'app pour ça serait absurde.
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(ctx, shortcut) }
    }
}
