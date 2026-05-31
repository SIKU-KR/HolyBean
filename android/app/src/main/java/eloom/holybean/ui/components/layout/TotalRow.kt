package eloom.holybean.ui.components.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eloom.holybean.ui.theme.OrangeOnContainer

/** "합계 / N원" 행. 정렬을 SpaceBetween으로 통일. */
@Composable
fun TotalRow(total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("합계", style = MaterialTheme.typography.titleMedium)
        Text(
            "%,d원".format(total),
            style = MaterialTheme.typography.titleMedium,
            color = OrangeOnContainer,
        )
    }
}
