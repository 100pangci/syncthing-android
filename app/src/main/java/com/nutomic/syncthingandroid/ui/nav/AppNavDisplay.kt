package com.nutomic.syncthingandroid.ui.nav

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
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
        predictivePopTransitionSpec = {
            slideInHorizontally(initialOffsetX = { -it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { it })
        },
        modifier = modifier,
    )
}
