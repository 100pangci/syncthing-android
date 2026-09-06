package com.nutomic.syncthingandroid.ui.theme

import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.nutomic.syncthingandroid.service.Constants

/** App theme pref value meaning "AMOLED black". Not an AppCompatDelegate mode. */
const val APP_THEME_AMOLED = 3

/**
 * True while the pure AMOLED theme is active. Surfaces that deliberately keep a
 * container tone in the other themes (cards, code blocks) use this to switch to a
 * pure-black container with only a faint outline instead.
 */
val LocalAmoledTheme = staticCompositionLocalOf { false }

/**
 * Alpha of the faint card outline in the pure AMOLED theme: visible on the black
 * background, but subtle enough to read as a trace rather than a border.
 */
const val AMOLED_CARD_BORDER_ALPHA = 0.4f

/**
 * Observes the app theme preference so in-place switches that do not change
 * the uiMode configuration (e.g. dark -> AMOLED) still recompose.
 */
@Composable
private fun rememberAppThemePref(): Int {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    var pref by remember {
        mutableIntStateOf(
            prefs.getString(Constants.PREF_APP_THEME, "")?.toIntOrNull() ?: 0
        )
    }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == Constants.PREF_APP_THEME) {
                pref = sp.getString(Constants.PREF_APP_THEME, "")?.toIntOrNull() ?: 0
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return pref
}

/**
 * AMOLED variant of a dark scheme: pure black background/surface tones.
 *
 * Every card in the app uses [ColorScheme.surfaceContainerLow] as its container,
 * so it is pure black here too - card separation comes from a faint outline drawn
 * by the components (see [LocalAmoledTheme]), not from a tinted container.
 */
private fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF0F0F0F),
    surfaceContainerHigh = Color(0xFF151515),
    surfaceContainerHighest = Color(0xFF1C1C1C)
)

@Composable
fun ApplicationTheme(
    content: @Composable () -> Unit
) {
    val appTheme = rememberAppThemePref()
    val isDarkTheme = when (appTheme) {
        1 -> false
        2, APP_THEME_AMOLED -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val baseScheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDarkTheme)
                dynamicDarkColorScheme(context)
            else
                dynamicLightColorScheme(context)
        } else {
            if (isDarkTheme)
                darkColorScheme()
            else
                lightColorScheme()
        }
    val colorScheme =
        if (appTheme == APP_THEME_AMOLED && isDarkTheme) baseScheme.toAmoled() else baseScheme
    val isAmoled = appTheme == APP_THEME_AMOLED && isDarkTheme

    CompositionLocalProvider(LocalAmoledTheme provides isAmoled) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
