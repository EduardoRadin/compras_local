package br.edu.unoesc.compraslocal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BrandGreen = Color(0xFF1F9D55)
val BrandGreenDark = Color(0xFF157A42)
val BrandMint = Color(0xFFE7F6EE)
val PageBackground = Color(0xFFF3F6F4)
val SoftBorder = Color(0xFFE2E8E4)
val GreenCheap = Color(0xFF1B8A4A)
val RedExpensive = Color(0xFFC62828)
val AmberAverage = Color(0xFFF9A825)

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = Color.White,
    secondary = BrandGreenDark,
    tertiary = RedExpensive,
    background = PageBackground,
    surface = Color.White,
    surfaceVariant = BrandMint,
    outline = SoftBorder,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BD39A),
    secondary = Color(0xFF81C784),
    tertiary = Color(0xFFEF9A9A),
)

@Composable
fun ComprasLocalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
