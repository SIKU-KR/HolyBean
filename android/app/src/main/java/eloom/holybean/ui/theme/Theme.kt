package eloom.holybean.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HolyBeanColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OrangeOnContainer,
    background = ScreenBg,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = ScreenBg,
    error = DangerRed,
)

@Composable
fun HolyBeanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HolyBeanColors,
        typography = HolyBeanTypography,
        content = content,
    )
}
