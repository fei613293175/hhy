package cc.orbexa.hhy.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object HhyColors {
    val BrandPrimary = Color(0xFF1677FF)
    val BrandPrimaryDark = Color(0xFF0B63CE)
    val BrandSecondary = Color(0xFF13B8A6)
    val BrandTertiary = Color(0xFF6D5DFB)
    val RewardRed = Color(0xFFFF4D4F)
    val RewardOrange = Color(0xFFFF8A34)
    val RewardGold = Color(0xFFFFB020)
    val PageBackground = Color(0xFFF5F7FA)
    val Surface = Color(0xFFFFFFFF)
    val SoftBlue = Color(0xFFEEF5FF)
    val TextPrimary = Color(0xFF182230)
    val TextSecondary = Color(0xFF667085)
    val TextTertiary = Color(0xFF98A2B3)
    val TextInverse = Color(0xFFFFFFFF)
    val Border = Color(0xFFE4E7EC)
    val Success = Color(0xFF12B76A)
    val Warning = Color(0xFFF79009)
    val Error = Color(0xFFF04438)
    val BrandGradientEnd = Color(0xFF39A0FF)
    val LivenessDark = Color(0xFF102F68)
    val SuccessSoft = Color(0xFFECFDF3)
    val WarningSoft = Color(0xFFFFFAEB)
    val ErrorSoft = Color(0xFFFEF3F2)
}

object HhySpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 20.dp
    val Xxl = 24.dp
    val Xxxl = 32.dp
}

object HhyRadius {
    val LargeCard = 16.dp
    val NormalCard = 14.dp
    val Button = 12.dp
    val Input = 12.dp
    val Tag = 8.dp
    val Dialog = 20.dp
    val BottomSheetTop = 24.dp
    val Pill = 999.dp
    val IdentityLivenessPanel = 20.dp
    val H5CallbackCard = 20.dp
}

/** Frozen Android type scale from hhy_design_tokens_v1.2.2.json. */
object HhyType {
    val PageTitleSize = 20.sp
    val PageTitleLineHeight = 28.sp
    val SectionTitleSize = 18.sp
    val SectionTitleLineHeight = 26.sp
    val CardTitleSize = 16.sp
    val CardTitleLineHeight = 24.sp
    val ButtonSize = 15.sp
    val ButtonLineHeight = 22.sp
    val BodySize = 14.sp
    val BodyLineHeight = 22.sp
    val SecondaryBodySize = 13.sp
    val SecondaryBodyLineHeight = 20.sp
    val CaptionSize = 12.sp
    val CaptionLineHeight = 18.sp
    val NavigationSize = 11.sp
    val NavigationLineHeight = 16.sp
    val ChallengeSuccessIconSize = 48.sp
    val ChallengeSuccessIconLineHeight = 56.sp
}

/** Frozen component dimensions used by the B01 mobile authentication pages. */
object HhySize {
    val Hairline = 1.dp
    val TopAppBarHeight = 56.dp
    val PrimaryButtonHeight = 48.dp
    val InputHeight = 52.dp
    val MinimumTouchTarget = 48.dp
    val StandardProgress = 24.dp
    val AppLogo = 64.dp
    val TabHeight = 48.dp
    val DialogMinWidth = 280.dp
    val ChallengeDialogWidth = 328.dp
    val ChallengeDialogMaxHeight = 560.dp
    val ChallengeImageWidth = 216.dp
    val ChallengeImageHeight = 72.dp
    val ChallengeCancelButtonWidth = 84.dp
    val IdentityLivenessFrame = 240.dp
    val StandardIcon = 24.dp
    val SmallIcon = 20.dp
    val ChatAvatar = 40.dp
    val ChatComposerHeight = 48.dp
}

object HhyElevation {
    val Card = 1.dp
    val Dialog = 8.dp
}

object HhyOpacity {
    const val Scrim = 0.48f
}
