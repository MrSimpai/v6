package com.example.medtap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.example.medtap.Her
import kotlinx.coroutines.delay

/**
 * Le casier : le dragon habillé en grand, et la collection en dessous, par emplacement.
 *
 * Un seul objet porté par section — deux chapeaux en même temps, ça n'existe pas. Mettre
 * une pièce enlève donc automatiquement celle qui occupait la place, plutôt que d'afficher
 * une erreur : personne n'a envie de lire « retirez d'abord votre tuque ».
 *
 * Les pièces pas encore gagnées apparaissent en ombre chinoise, sans nom. On voit la forme,
 * on devine, on ne sait pas. Tout révéler ferait du casier une liste de courses ; ne rien
 * montrer lui enlèverait sa raison d'être ouvert.
 */
@Composable
fun LockerScreen(
    owned: Set<String>,
    worn: Set<String>,
    /** Fin de la cabine d'essayage, ou `null` hors essayage. */
    previewUntil: Long?,
    onToggle: (String) -> Unit,
    /** Rend vrai si le mot était le bon — c'est le seul retour que le champ obtient. */
    onCode: (String) -> Boolean,
    onOpenSkyLab: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val previewing = previewUntil != null
    val scroll = rememberScrollState()

    // Le dragon rétrécit au lieu de partir.
    //
    // Tout l'écran défilait d'un bloc : dès qu'on descendait vers les pièces, la seule
    // chose qui compte — l'effet que ça fait sur elle — sortait par le haut. On habillait
    // à l'aveugle, on remontait pour regarder, on redescendait. Le carton est donc épinglé,
    // et il maigrit à mesure qu'on défile : grand quand on ne fait que la regarder, compact
    // mais toujours là quand on fouille la collection.
    val collapse = (scroll.value / 300f).coerceIn(0f, 1f)
    val dragonSize = lerp(206.dp, 104.dp, collapse)

    Column(modifier.fillMaxSize().sky()) {

        // ---- l'en-tête, qui ne bouge pas ----
        Surface(
            // Transparent tant que rien n'est passé dessous : c'est en haut de l'écran que
            // le ciel a ses couleurs, et un aplat opaque les masquerait entièrement. Dès
            // qu'on défile, il devient opaque pour que la collection ne transparaisse pas
            // à travers le dragon.
            color = if (collapse > 0.02f) Pal.Mist else Color.Transparent,
            shadowElevation = if (collapse > 0.02f) 8.dp else 0.dp
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 12.dp)
            ) {
                Text("CASIER", style = Type.Label, color = skyMuted())
                Spacer(Modifier.height(4.dp))
                Text("La garde-robe de ${Her.dragon}", style = Type.Title, color = skyInk())

                Spacer(Modifier.height(12.dp))

                Surface(color = Pal.Card, shape = Soft) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Pendant l'essayage, elle répond au mot plutôt que de sourire poliment.
                        Mascot(
                            if (previewing) Mood.Love else Mood.Cheering,
                            Modifier.size(dragonSize), worn = worn
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (worn.isEmpty()) "Rien sur le dos pour l'instant"
                            else "${worn.size} pièce${if (worn.size > 1) "s" else ""} portée${if (worn.size > 1) "s" else ""}",
                            style = Type.Label, color = Pal.Muted
                        )
                    }
                }
            }
        }

        // ---- la collection, qui défile dessous ----
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 40.dp)
        ) {
            Text(
                "COLLECTION — ${owned.size}/${Cosmetics.ALL.size}",
                style = Type.Label, color = Pal.Muted
            )
            Spacer(Modifier.height(12.dp))

            Slot.entries.forEach { slot ->
                val items = Cosmetics.ALL.filter { it.slot == slot }
                if (items.isEmpty()) return@forEach

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        slot.label.uppercase(), style = Type.Label, color = Pal.Iris,
                        modifier = Modifier.weight(1f)
                    )
                    val here = items.count { it.id in owned }
                    Text("$here/${items.size}", style = Type.Label, color = Pal.Muted)
                }
                Spacer(Modifier.height(8.dp))
                items.forEach { item ->
                    CosmeticRow(
                        item = item,
                        // Le compte en haut reste celui des pièces réellement gagnées :
                        // c'est l'essayage qui est temporaire, pas la collection.
                        unlocked = previewing || item.id in owned,
                        equipped = item.id in worn,
                        onToggle = { onToggle(item.id) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Une pièce par journée complète, jamais deux fois la même, et une seule " +
                    "portée par emplacement. Une fois gagnées, elles restent à toi.",
                style = Type.Label, color = Pal.Muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(26.dp))
            PreviewCode(previewUntil, onCode)

            // Le même mot ouvre la cabine d'essayage ET l'atelier du ciel. Il n'apparaît
            // qu'une fois dit : avant, il n'y a rien à voir ici.
            if (SkyLab.unlocked.value) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Pal.Card, shape = Pill,
                    modifier = Modifier.fillMaxWidth().clip(Pill).clickable { onOpenSkyLab() }
                ) {
                    Text(
                        "Atelier du ciel ☁︎",
                        style = Type.Label, color = Pal.Iris, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Le mot de passe, tout en bas du casier.
 *
 * Tout en bas exprès : il faut avoir fait défiler la collection entière pour le trouver,
 * donc on a d'abord vu ce qui reste à gagner. Trouvé avant, il remplacerait l'envie ;
 * trouvé après, il la nourrit.
 */
@Composable
private fun PreviewCode(previewUntil: Long?, onCode: (String) -> Boolean) {
    var typed by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf(false) }

    // Une seconde de battement, et seulement pendant l'essayage : le compte à rebours doit
    // fondre à l'écran, mais il n'y a rien à rafraîchir le reste du temps.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(previewUntil) {
        if (previewUntil == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    if (previewUntil != null) {
        val left = ((previewUntil - now) / 1000L).coerceAtLeast(0L)
        Surface(color = Pal.IrisSoft, shape = Soft) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Tout est ouvert 💗", style = Type.Title, color = Pal.Iris)
                Spacer(Modifier.height(4.dp))
                Text(
                    "%d:%02d".format(left / 60, left % 60),
                    style = Type.Display, color = Pal.Iris
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Essaie tout ce que tu veux. Rien n'est enregistré — après, " +
                        "${Her.dragon} remet ses vraies affaires.",
                    style = Type.Label, color = Pal.Muted, textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    Surface(color = Pal.Card, shape = Soft) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("UN MOT POUR ${Her.dragon.uppercase()}", style = Type.Label, color = Pal.Muted)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it; refused = false },
                    singleLine = true,
                    placeholder = { Text("…", style = Type.Body) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (onCode(typed)) typed = "" else refused = true
                    },
                    shape = Pill,
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Iris)
                ) { Text("Dire", style = Type.Label) }
            }
            if (refused) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${Her.dragon} penche la tête. Ce n'est pas ça.",
                    style = Type.Label, color = Pal.Muted
                )
            }
        }
    }
}

@Composable
private fun CosmeticRow(
    item: Cosmetic,
    unlocked: Boolean,
    equipped: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = if (equipped) Pal.IrisSoft else Pal.Card,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (unlocked) Modifier.clickable { onToggle() } else Modifier)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Vignette : le dragon portant uniquement cette pièce, en petit.
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (unlocked) Pal.Mist else Pal.IrisSoft),
                contentAlignment = Alignment.Center
            ) {
                Mascot(
                    Mood.Sleeping,
                    Modifier.size(60.dp),
                    worn = setOf(item.id),
                    silhouette = !unlocked
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (unlocked) item.name else "? ? ?",
                    style = Type.Title,
                    color = if (unlocked) Pal.Ink else Pal.Muted
                )
                Text(
                    if (unlocked) item.blurb else "Encore une journée complète pour la découvrir.",
                    style = Type.Label, color = Pal.Muted
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    !unlocked -> "🔒"
                    equipped -> "Portée"
                    else -> "Mettre"
                },
                style = Type.Label,
                color = if (equipped) Pal.Iris else Pal.Muted
            )
        }
    }
}
