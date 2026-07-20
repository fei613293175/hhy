package cc.orbexa.hhy.designsystem

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween

/** Frozen motion tokens and reusable page transitions for every client screen. */
object HhyMotion {
    const val ButtonFeedbackMillis = 120
    const val StandardMillis = 200
    const val ResultEmphasisMillis = 300

    fun forwardEnter(): EnterTransition = slideInHorizontally(
        animationSpec = tween(StandardMillis),
        initialOffsetX = { width -> width / 4 },
    ) + fadeIn(tween(StandardMillis))

    fun forwardExit(): ExitTransition = slideOutHorizontally(
        animationSpec = tween(StandardMillis),
        targetOffsetX = { width -> -width / 8 },
    ) + fadeOut(tween(StandardMillis))

    fun backwardEnter(): EnterTransition = slideInHorizontally(
        animationSpec = tween(StandardMillis),
        initialOffsetX = { width -> -width / 8 },
    ) + fadeIn(tween(StandardMillis))

    fun backwardExit(): ExitTransition = slideOutHorizontally(
        animationSpec = tween(StandardMillis),
        targetOffsetX = { width -> width / 4 },
    ) + fadeOut(tween(StandardMillis))

    fun forwardContent(): ContentTransform = forwardEnter().togetherWith(forwardExit())
}
