package cc.orbexa.hhy.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

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

private val HhyTypography = Typography(
    headlineSmall = TextStyle(
        fontSize = HhyType.PageTitleSize,
        lineHeight = HhyType.PageTitleLineHeight,
        fontWeight = FontWeight.SemiBold,
    ),
    titleLarge = TextStyle(
        fontSize = HhyType.SectionTitleSize,
        lineHeight = HhyType.SectionTitleLineHeight,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = HhyType.CardTitleSize,
        lineHeight = HhyType.CardTitleLineHeight,
        fontWeight = FontWeight.SemiBold,
    ),
    labelLarge = TextStyle(
        fontSize = HhyType.ButtonSize,
        lineHeight = HhyType.ButtonLineHeight,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = HhyType.BodySize,
        lineHeight = HhyType.BodyLineHeight,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = HhyType.SecondaryBodySize,
        lineHeight = HhyType.SecondaryBodyLineHeight,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontSize = HhyType.CaptionSize,
        lineHeight = HhyType.CaptionLineHeight,
        fontWeight = FontWeight.Normal,
    ),
    labelMedium = TextStyle(
        fontSize = HhyType.NavigationSize,
        lineHeight = HhyType.NavigationLineHeight,
        fontWeight = FontWeight.Medium,
    ),
)

private val HhyShapes = Shapes(
    small = RoundedCornerShape(HhyRadius.Button),
    medium = RoundedCornerShape(HhyRadius.NormalCard),
    large = RoundedCornerShape(HhyRadius.LargeCard),
)

@Composable
fun HhyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HhyLightColors,
        typography = HhyTypography,
        shapes = HhyShapes,
        content = content,
    )
}
