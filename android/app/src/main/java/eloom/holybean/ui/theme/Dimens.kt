package eloom.holybean.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    // 터치 타깃 (접근성)
    val minTouchTarget = 48.dp
    val primaryTouchTarget = 56.dp

    // 간격 스케일
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 12.dp
    val spaceLg = 16.dp

    // 라운드 스케일 (버튼은 목업보다 사각 — 사용자 선호 override)
    val radiusButton = 6.dp
    val radiusTile = 10.dp
    val radiusPane = 14.dp

    // elevation
    val paneElevation = 2.dp
    val tileElevation = 1.dp

    // 기존 이름 alias (값은 새 스케일에 정렬)
    val gap = spaceSm                 // 8.dp (was 10.dp)
    val screenPadding = spaceMd       // 12.dp
    val tileRadius = radiusTile       // 10.dp (was 8.dp)
    val paneRadius = radiusPane       // 14.dp (was 10.dp)
    val basketWidthFraction = 0.38f
}
