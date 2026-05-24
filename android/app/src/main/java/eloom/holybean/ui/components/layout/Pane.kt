package eloom.holybean.ui.components.layout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import eloom.holybean.ui.theme.Dimens

/** radius + elevation + 내부 패딩을 갖춘 흰 패널. */
@Composable
fun Pane(
    modifier: Modifier = Modifier,
    padding: Dp = Dimens.panePadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier,
        shape = RoundedCornerShape(Dimens.radiusPane),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Dimens.paneElevation,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}
