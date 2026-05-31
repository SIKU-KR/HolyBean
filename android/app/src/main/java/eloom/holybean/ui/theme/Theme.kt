package eloom.holybean.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val HolyBeanColors = lightColorScheme(
    primary = Orange,
    onPrimary = OnSurface,
    primaryContainer = OrangeContainer,
    onPrimaryContainer = OrangeOnContainer,
    background = ScreenBg,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = ScreenBg,
    error = DangerRed,
)

// 사각형에 가까운 라운드 (Card/Surface/Dialog 등 테마 기본).
// 주의: Material3 Button은 테마 shapes를 읽지 않으므로 버튼에는 호출부에서
// shape = RoundedCornerShape(Dimens.radiusButton) 를 명시한다.
private val HolyBeanShapes = Shapes(
    extraSmall = RoundedCornerShape(Dimens.radiusButton),
    small = RoundedCornerShape(Dimens.radiusButton),
    medium = RoundedCornerShape(Dimens.radiusTile),
    large = RoundedCornerShape(Dimens.radiusPane),
)

@Composable
fun HolyBeanTheme(content: @Composable () -> Unit) {
    // 매장 고정 태블릿: 시스템 글꼴 배율을 1.0으로 고정해 레이아웃 깨짐 차단.
    val density = LocalDensity.current
    val fixedDensity = Density(density = density.density, fontScale = 1f)
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        MaterialTheme(
            colorScheme = HolyBeanColors,
            typography = HolyBeanTypography,
            shapes = HolyBeanShapes,
            content = content,
        )
    }
}
