package com.fantasyidler.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

/**
 * The full motion vocabulary for Idle Fantasy.
 *
 * Every animation in the app should pick one of these specs rather than inlining a tween.
 * Snappy: press feedback. Smooth: property animation, nav transitions. Bouncy: arrivals
 * (rewards landing, level-up). Idle: looped breathing/glow effects on tier halos.
 */
object FantasyMotion {

    const val SNAPPY_MS = 120
    const val SMOOTH_MS = 240
    const val IDLE_MS = 1800

    val Snappy = tween<Float>(durationMillis = SNAPPY_MS, easing = FastOutSlowInEasing)

    val Smooth = tween<Float>(durationMillis = SMOOTH_MS, easing = FastOutSlowInEasing)

    val Bouncy = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness    = Spring.StiffnessMediumLow,
    )

    val Idle = infiniteRepeatable<Float>(
        animation = tween(durationMillis = IDLE_MS, easing = LinearEasing),
        repeatMode = RepeatMode.Reverse,
    )

    // --- Nav transitions ---------------------------------------------------
    // Lambdas of type AnimatedContentTransitionScope<NavBackStackEntry>.() -> X for use
    // with NavHost composable(...) entries. Forward navigation slides left + fades; the
    // pop reverses direction so back feels right.

    val NavEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(SlideDirection.Left, tween(SMOOTH_MS, easing = FastOutSlowInEasing)) +
            fadeIn(tween(SMOOTH_MS, easing = FastOutSlowInEasing))
    }

    val NavExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(SlideDirection.Left, tween(SMOOTH_MS, easing = FastOutSlowInEasing)) +
            fadeOut(tween(SMOOTH_MS, easing = FastOutSlowInEasing))
    }

    val NavPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(SlideDirection.Right, tween(SMOOTH_MS, easing = FastOutSlowInEasing)) +
            fadeIn(tween(SMOOTH_MS, easing = FastOutSlowInEasing))
    }

    val NavPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(SlideDirection.Right, tween(SMOOTH_MS, easing = FastOutSlowInEasing)) +
            fadeOut(tween(SMOOTH_MS, easing = FastOutSlowInEasing))
    }

    // Sub-screens (Farming, Shop, Settings) fade in place instead of sliding, so they
    // feel like overlays rather than peer destinations.

    val SubEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        fadeIn(tween(SMOOTH_MS, easing = FastOutSlowInEasing))
    }

    val SubExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        fadeOut(tween(SMOOTH_MS, easing = FastOutSlowInEasing))
    }
}
