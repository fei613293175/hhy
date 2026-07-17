package cc.orbexa.hhy.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val HhyLightColors = lightColorScheme(
    primary = HhyColors.BrandPrimary,
    onPrimary = HhyColors.TextInverse,
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
