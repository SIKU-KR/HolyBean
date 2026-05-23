package eloom.holybean.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedToggle(options: ImmutableList<String>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { i, opt ->
            SegmentedButton(
                selected = opt == selected, onClick = { onSelect(opt) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(opt) }
        }
    }
}
