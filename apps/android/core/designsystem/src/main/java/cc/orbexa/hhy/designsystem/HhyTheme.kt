package cc.orbexa.hhy.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HhyLightColors = lightColorScheme(
    primary = HhyColors.BrandPrimary,
    onPrimary = Color.White,
    secondary = HhyColors.BrandSecondary,
    background = HhyColors.PageBackground,
    onBackground = HhyColors.TextPrimary,
    surface = HhyColors.Surface,
    onSurface = HhyColors.TextPrimary,
    outline = HhyColors.Border,
    error = HhyColors.Error,
)

@Composable
fun HhyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HhyLightColors,
        content = content,
    )
}
