package com.nutomic.syncthingandroid.ui.screens.settings

import android.util.Log
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.navigation3.ui.NavDisplay
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nutomic.syncthingandroid.ui.nav.BACK_PEEK_PAD_DP
import com.nutomic.syncthingandroid.ui.nav.backPopTransform
import com.nutomic.syncthingandroid.ui.nav.backPredictivePopTransform
import kotlinx.serialization.Serializable

@Serializable
sealed interface SettingsRoute : NavKey {

    @Serializable
    data object Root : SettingsRoute

    @Serializable
    data object RunConditions : SettingsRoute
    @Serializable
    data object UserInterface : SettingsRoute
    @Serializable
    data object Behavior : SettingsRoute
    @Serializable
    data object SyncthingOptions : SettingsRoute
    @Serializable
    data object CustomCertificate : SettingsRoute
    @Serializable
    data object ImportExport : SettingsRoute
    @Serializable
    data object Troubleshooting : SettingsRoute
    @Serializable
    data object Experimental : SettingsRoute
    @Serializable
    data object About : SettingsRoute
    @Serializable
    data object Licenses : SettingsRoute


    companion object {
        private const val TAG = "SettingsRoute"

        // Use these strings to open particular screen directly
        fun fromString(route: String?): SettingsRoute = when (route) {
            "RunConditions" -> RunConditions
            "UserInterface" -> UserInterface
            "Behavior" -> Behavior
            "SyncthingOptions" -> SyncthingOptions
            "CustomCertificate" -> CustomCertificate
            "ImportExport" -> ImportExport
            "Troubleshooting" -> Troubleshooting
            "Experimental" -> Experimental
            "About" -> About
            "Licenses" -> Licenses
            "Root" -> Root
            else -> {
                Log.d(TAG, "Unknown settings path provided: $route. Defaulting to Root.")
                Root
            }
        }
    }
}

interface Navigator<T: NavKey> {
    fun navigateTo(route: T)
    fun navigateBack()
    fun navigateUp()
}

val LocalSettingsNavigator = staticCompositionLocalOf<Navigator<SettingsRoute>> {
    error("Navigator not provided")
}

@Composable
fun rememberSettingsNavBackStack(startDestination: SettingsRoute): NavBackStack<SettingsRoute> {
    return rememberSerializable(
        serializer = NavBackStackSerializer(elementSerializer = NavKeySerializer())
    ) {
        val initialRoute = listOfNotNull(
            SettingsRoute.Root,
            SettingsRoute.About.takeIf { startDestination == SettingsRoute.Licenses },
            startDestination.takeIf { it != SettingsRoute.Root }
        ).toMutableStateList()
        NavBackStack(initialRoute)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsNavDisplay(
    backStack: NavBackStack<SettingsRoute>
) {
    val navigator = LocalSettingsNavigator.current
    val peekPadPx = with(LocalDensity.current) { BACK_PEEK_PAD_DP.dp.roundToPx() }

    NavDisplay(
        backStack = backStack,
        onBack = { navigator.navigateBack() },
        entryProvider = entryProvider {
            settingsRootEntry()
            settingsRunConditionsEntry()
            settingsUserInterfaceEntry()
            settingsBehaviorEntry()
            settingsSyncthingOptionsEntry()
            settingsCustomCertificateEntry()
            settingsImportExportEntry()
            settingsTroubleshootingEntry()
            settingsExperimentalEntry()
            settingsAboutEntry()
            licensesEntry()
        },
        transitionSpec = {
            // Slide in from right when navigating forward
            slideInHorizontally(initialOffsetX = { it }) togetherWith
                    slideOutHorizontally(targetOffsetX = { -it })
        },
        popTransitionSpec = { backPopTransform() },
        predictivePopTransitionSpec = { swipeEdge -> backPredictivePopTransform(swipeEdge, peekPadPx) },
        modifier = Modifier.onKeyEvent { keyEvent ->
            if (keyEvent.key == Key.DirectionLeft
                && keyEvent.type == KeyEventType.KeyDown) {
                navigator.navigateBack()
                true
            } else {
                false
            }
        }
    )
}
