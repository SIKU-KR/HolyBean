package eloom.holybean.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eloom.holybean.ui.theme.HolyBeanTheme
import eloom.holybean.ui.theme.OnSurfaceMuted
import eloom.holybean.ui.theme.OrangeText

@Composable
fun BasketRow(name: String, count: Int, amount: Int, isCoupon: Boolean = false, onClick: () -> Unit) {
    val nameColor = if (isCoupon) OrangeText else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$name ", style = MaterialTheme.typography.bodyMedium, color = nameColor,
            modifier = Modifier.weight(1f),
        )
        Text("${count}개 ", style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
        Text("%,d".format(amount), style = MaterialTheme.typography.bodyMedium, color = nameColor)
    }
}

@Preview
@Composable
private fun BasketRowPreview() = HolyBeanTheme {
    BasketRow("아메리카노", 2, 7000, onClick = {})
}
