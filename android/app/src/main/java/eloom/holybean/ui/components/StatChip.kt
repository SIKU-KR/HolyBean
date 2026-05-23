package eloom.holybean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eloom.holybean.ui.theme.OnSurfaceMuted

@Composable
fun StatChip(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}
