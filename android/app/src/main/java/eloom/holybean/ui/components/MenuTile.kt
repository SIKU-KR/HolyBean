package eloom.holybean.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        TileStyle.Settings -> SettingsTileBg
    }
    val border: BorderStroke? = when (style) {
        TileStyle.Menu -> null
        TileStyle.Coupon -> BorderStroke(1.dp, OrangeLight)
        TileStyle.Settings -> BorderStroke(1.dp, OnSurfaceMuted)
    }
    val labelColor = when (style) {
        TileStyle.Coupon -> OrangeText
        TileStyle.Settings -> OnSurfaceMuted
        TileStyle.Menu -> OnSurface
    }
    Card(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(Dimens.radiusTile),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.tileElevation),
        border = border,
    ) {
        Column(
            Modifier.fillMaxSize().padding(Dimens.spaceXs),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor,
                fontWeight = if (style == TileStyle.Menu) FontWeight.Medium else FontWeight.Bold,
            )
            if (price != null) {
                Text(
                    "%,d".format(price),
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangeText,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Preview
@Composable
private fun MenuTilePreview() = HolyBeanTheme {
    MenuTile(name = "아메리카노", price = 3500, onClick = {})
}
