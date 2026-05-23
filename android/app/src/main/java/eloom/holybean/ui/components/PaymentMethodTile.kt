package eloom.holybean.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eloom.holybean.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodTile(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick, modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) OrangeContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(2.dp, if (selected) Orange else DividerGray),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = if (selected) OrangeOnContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}
