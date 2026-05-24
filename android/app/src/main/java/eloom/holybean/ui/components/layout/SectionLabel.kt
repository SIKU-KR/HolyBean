package eloom.holybean.ui.components.layout

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eloom.holybean.ui.theme.OnSurfaceMuted

/** 섹션 제목 라벨(작은 muted 텍스트). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = OnSurfaceMuted,
        modifier = modifier,
    )
}
