package com.example.medtap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.medtap.Her

/**
 * Le casier : le dragon habillé en grand, et la collection en dessous.
 *
 * Les pièces verrouillées restent visibles, en silhouette. C'est le ressort entier d'un
 * casier : savoir précisément ce qui manque vaut bien plus que de le découvrir. Une grille
 * qui ne montrerait que ce qu'on possède n'aurait aucune raison d'être ouverte.
 */
@Composable
fun LockerScreen(
    owned: Set<String>,
    worn: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(Pal.Mist)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 40.dp)
    ) {
        Text("CASIER", style = Type.Label, color = Pal.Muted)
        Spacer(Modifier.height(6.dp))
        Text("La garde-robe de ${Her.dragon}", style = Type.Display, color = Pal.Ink)

        Spacer(Modifier.height(18.dp))

        Surface(color = Pal.Card, shape = Soft) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Mascot(Mood.Cheering, Modifier.size(210.dp), worn = worn)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (worn.isEmpty()) "Rien sur le dos pour l'instant"
                    else "${worn.size} pièce${if (worn.size > 1) "s" else ""} portée${if (worn.size > 1) "s" else ""}",
                    style = Type.Label, color = Pal.Muted
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "COLLECTION — ${owned.size}/${Cosmetics.ALL.size}",
            style = Type.Label, color = Pal.Muted
        )
        Spacer(Modifier.height(12.dp))

        Slot.entries.forEach { slot ->
            val items = Cosmetics.ALL.filter { it.slot == slot }
            if (items.isEmpty()) return@forEach
            Text(slot.label.uppercase(), style = Type.Label, color = Pal.Iris)
            Spacer(Modifier.height(8.dp))
            items.forEach { item ->
                CosmeticRow(
                    item = item,
                    unlocked = item.id in owned,
                    equipped = item.id in worn,
                    onToggle = { onToggle(item.id) }
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Une pièce par journée complète. Elles ne se répètent jamais, et une fois " +
                "gagnées elles restent à toi.",
            style = Type.Label, color = Pal.Muted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
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
                    Modifier.size(60.dp).alpha(if (unlocked) 1f else 0.18f),
                    worn = setOf(item.id)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (unlocked) item.name else "Pièce verrouillée",
                    style = Type.Title,
                    color = if (unlocked) Pal.Ink else Pal.Muted
                )
                Text(
                    if (unlocked) item.blurb else "Termine une journée de plus pour l'ouvrir.",
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
