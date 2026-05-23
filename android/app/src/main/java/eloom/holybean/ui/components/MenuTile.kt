package eloom.holybean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eloom.holybean.ui.theme.*

enum class TileStyle { Menu, Coupon, Settings }

@Composable
fun MenuTile(
    name: String,
    price: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TileStyle = TileStyle.Menu,
) {
    val container = when (style) {
        TileStyle.Menu -> Surface
        TileStyle.Coupon -> OrangeContainer
        TileStyle.Settings -> Color(0xFFEEEEEE)
    }
    Card(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(Dimens.tileRadius),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (price != null) {
                Text("%,d".format(price), style = MaterialTheme.typography.labelSmall, color = Orange)
            }
        }
    }
}

@Preview
@Composable
private fun MenuTilePreview() = HolyBeanTheme {
    MenuTile(name = "아메리카노", price = 3500, onClick = {})
}
