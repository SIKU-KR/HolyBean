package eloom.holybean.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    // ── 간격 스케일 (8pt 그리드, 4는 half-step) ─────────────────
    val spaceXs = 4.dp    // 칩/텍스트 내부, 미세 조정
    val spaceSm = 8.dp    // 기본 gap, 리스트 아이템 간격, 그리드 gap
    val spaceMd = 16.dp   // 화면 패딩, 패널 내부 패딩, 헤더 하단
    val spaceLg = 24.dp   // 섹션 간 간격
    val spaceXl = 32.dp   // 큰 블록 구분

    // ── 의미 별칭 ──────────────────────────────────────────────
    val screenPadding = spaceMd   // 16
    val panePadding = spaceMd     // 16
    val paneGap = spaceSm         // 8
    val sectionGap = spaceLg      // 24
    val headerGap = spaceMd       // 16
    val gridGap = spaceSm         // 8
    val itemGap = spaceSm         // 8

    // ── 2-pane 분할 비율 ──────────────────────────────────────
    val paneSplitNarrow = 0.38f
    val paneSplitWide = 0.46f

    // ── 라운드 (값 유지) ──────────────────────────────────────
    val radiusButton = 6.dp
    val radiusTile = 10.dp
    val radiusPane = 14.dp
    val radiusChip = 14.dp

    // ── elevation ─────────────────────────────────────────────
    val paneElevation = 2.dp
    val tileElevation = 1.dp

    // ── 터치 타깃 ─────────────────────────────────────────────
    val minTouchTarget = 48.dp
    val primaryTouchTarget = 56.dp

    // ── 고정 치수 (스케일 예외) ───────────────────────────────
    val statusDot = 10.dp
    val menuTileHeight = 90.dp
}
