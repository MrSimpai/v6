package com.example.medtap.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Palette tirée du dragon lui-même : framboise pour la marque, ventre rose pâle pour
 * le fond, et deux couleurs d'état — abricot pour « à prendre », menthe pour « pris » —
 * assez tranchées pour se lire de l'autre bout de la pièce.
 */
object Pal {
    val Mist     = Color(0xFFFDF3F6)   // fond : rose crème, presque blanc
    val Card     = Color(0xFFFFFFFF)   // surface surélevée
    val Ink      = Color(0xFF3B1327)   // texte principal, prune très foncé
    val Muted    = Color(0xFF9C7383)   // texte secondaire
    val Iris     = Color(0xFFC03765)   // marque : exactement la couleur du dragon
    val IrisSoft = Color(0xFFFBE1E9)
    val Apricot  = Color(0xFFE08A5F)   // dose à prendre
    val Mint     = Color(0xFF3FA98D)   // dose prise
    val MintSoft = Color(0xFFDDF1EA)
    val Teal     = Color(0xFF94C9CF)   // accent froid, repris de l'icône
    val Butter   = Color(0xFFF2C48A)
    val Blush    = Color(0xFFF1BBCB)
    val Danger   = Color(0xFFB3324B)   // retrait d'un médicament, rien d'autre
}

val Soft = RoundedCornerShape(28.dp)
val Pill = RoundedCornerShape(999.dp)

object Type {
    val Display = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black,
        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1).sp
    )
    val Title = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, letterSpacing = (-0.3).sp
    )
    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 22.sp
    )
    /** Utility face for labels and axis ticks. */
    val Label = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, letterSpacing = 1.4.sp
    )
}

@Composable
fun MedTapTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = Pal.Iris, onPrimary = Color.White,
        background = Pal.Mist, onBackground = Pal.Ink,
        surface = Pal.Card, onSurface = Pal.Ink,
        secondary = Pal.Apricot, tertiary = Pal.Mint
    ),
    content = content
)
