package eu.kanade.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.rememberDynamicColorScheme
import eu.kanade.presentation.theme.colorscheme.BaseColorScheme
import eu.kanade.presentation.theme.colorscheme.CatppuccinColorScheme
import eu.kanade.presentation.theme.colorscheme.GreenAppleColorScheme
import eu.kanade.presentation.theme.colorscheme.LavenderColorScheme
import eu.kanade.presentation.theme.colorscheme.MidnightDuskColorScheme
import eu.kanade.presentation.theme.colorscheme.MonochromeColorScheme
import eu.kanade.presentation.theme.colorscheme.NordColorScheme
import eu.kanade.presentation.theme.colorscheme.StrawberryColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import eu.kanade.presentation.theme.colorscheme.TakoColorScheme
import eu.kanade.presentation.theme.colorscheme.TealTurqoiseColorScheme
import eu.kanade.presentation.theme.colorscheme.TidalWaveColorScheme
import eu.kanade.presentation.theme.colorscheme.TokyoNightColorScheme
import eu.kanade.presentation.theme.colorscheme.YinYangColorScheme
import eu.kanade.presentation.theme.colorscheme.YotsubaColorScheme

enum class IosAppTheme(val displayName: String) {
    DEFAULT("Default (Mihon Blue)"),
    DYNAMIC("Dynamic (Material You)"),
    CATPPUCCIN("Catppuccin"),
    GREEN_APPLE("Green Apple"),
    LAVENDER("Lavender"),
    MIDNIGHT_DUSK("Midnight Dusk"),
    MONOCHROME("Monochrome"),
    NORD("Nord"),
    STRAWBERRY_DAIQUIRI("Strawberry Daiquiri"),
    TAKO("Tako"),
    TEALTURQUOISE("Teal & Turquoise"),
    TIDAL_WAVE("Tidal Wave"),
    TOKYONIGHT("Tokyo Night"),
    YINYANG("Yin & Yang"),
    YOTSUBA("Yotsuba"),
}

enum class IosDarkMode(val displayName: String) {
    SYSTEM("Follow system"),
    LIGHT("Off"),
    DARK("On"),
}

@Composable
fun IosMihonTheme(
    appTheme: IosAppTheme = IosAppTheme.DEFAULT,
    darkMode: IosDarkMode = IosDarkMode.SYSTEM,
    isAmoled: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (darkMode) {
        IosDarkMode.SYSTEM -> systemDark
        IosDarkMode.LIGHT -> false
        IosDarkMode.DARK -> true
    }

    val colorScheme: ColorScheme = if (appTheme == IosAppTheme.DYNAMIC) {
        val dynamic = rememberDynamicColorScheme(
            seedColor = Color(0xFF0057CE),
            isDark = isDark,
            isAmoled = isAmoled,
        )
        dynamic
    } else {
        remember(appTheme, isDark, isAmoled) {
            val scheme: BaseColorScheme = colorSchemes[appTheme] ?: TachiyomiColorScheme
            scheme.getColorScheme(
                isDark = isDark,
                isAmoled = isAmoled,
                overrideDarkSurfaceContainers = true,
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

private val colorSchemes: Map<IosAppTheme, BaseColorScheme> = mapOf(
    IosAppTheme.DEFAULT to TachiyomiColorScheme,
    IosAppTheme.CATPPUCCIN to CatppuccinColorScheme,
    IosAppTheme.TOKYONIGHT to TokyoNightColorScheme,
    IosAppTheme.GREEN_APPLE to GreenAppleColorScheme,
    IosAppTheme.LAVENDER to LavenderColorScheme,
    IosAppTheme.MIDNIGHT_DUSK to MidnightDuskColorScheme,
    IosAppTheme.MONOCHROME to MonochromeColorScheme,
    IosAppTheme.NORD to NordColorScheme,
    IosAppTheme.STRAWBERRY_DAIQUIRI to StrawberryColorScheme,
    IosAppTheme.TAKO to TakoColorScheme,
    IosAppTheme.TEALTURQUOISE to TealTurqoiseColorScheme,
    IosAppTheme.TIDAL_WAVE to TidalWaveColorScheme,
    IosAppTheme.YINYANG to YinYangColorScheme,
    IosAppTheme.YOTSUBA to YotsubaColorScheme,
)

