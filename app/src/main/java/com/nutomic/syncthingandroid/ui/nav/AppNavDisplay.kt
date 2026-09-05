package com.nutomic.syncthingandroid.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

/**
 * Thin wrapper around the Navigation 3 NavDisplay with shared transition specs.
 */
@Composable
fun <T : NavKey> AppNavDisplay(
    backStack: NavBackStack<T>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    entryProvider: EntryProviderScope<T>.() -> Unit,
) {
    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        entryProvider = entryProvider(builder = entryProvider),
        transitionSpec = {
            slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { it } togetherWith
                    slideOutHorizontally(tween(350, easing = FastOutSlowInEasing)) { -it }
        },
        popTransitionSpec = {
            // Material "stack" reveal: the previous screen scales up in place while
            // the top screen slides off to the trailing edge. Tweens with the standard
            // M3 easing keep the gesture-seeked transition smooth (spring defaults
            // feel stiff when seeked by the back gesture).
            (fadeIn(tween(350, easing = LinearOutSlowInEasing)) +
                    scaleIn(tween(350, easing = LinearOutSlowInEasing), initialScale = 0.9f)) togetherWith
                    slideOutHorizontally(tween(350, easing = FastOutSlowInEasing)) { it }
        },
        predictivePopTransitionSpec = {
            (fadeIn(tween(350, easing = LinearOutSlowInEasing)) +
                    scaleIn(tween(350, easing = LinearOutSlowInEasing), initialScale = 0.9f)) togetherWith
                    slideOutHorizontally(tween(350, easing = FastOutSlowInEasing)) { it }
        },
        modifier = modifier,
    )
}
