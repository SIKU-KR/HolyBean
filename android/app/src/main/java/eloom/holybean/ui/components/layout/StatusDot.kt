package eloom.holybean.ui.components.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import eloom.holybean.ui.theme.Dimens
import eloom.holybean.ui.theme.StatusError
import eloom.holybean.ui.theme.StatusOk
import eloom.holybean.ui.theme.StatusUnknown

/** 원형 상태 점. true=정상/false=오류/null=미상. */
@Composable
fun StatusDot(ok: Boolean?, modifier: Modifier = Modifier) {
    val color = when (ok) {
        true -> StatusOk
        false -> StatusError
        null -> StatusUnknown
    }
    Box(
        modifier
            .size(Dimens.statusDot)
            .clip(CircleShape)
            .background(color),
    )
}
