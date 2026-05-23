package eloom.holybean.ui.credits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.ui.components.BasketRow
import kotlinx.collections.immutable.toImmutableList

@Composable
fun CreditsRoute(onClose: () -> Unit, vm: CreditsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmPaid by remember { mutableStateOf(false) }
    if (confirmPaid) {
        AlertDialog(
            onDismissRequest = { confirmPaid = false },
            title = { Text("외상 결제완료") },
            text = { Text("${state.selectedOrderNumber}번 외상을 결제완료 처리하시겠습니까?") },
            confirmButton = { TextButton(onClick = { vm.handleDeleteButton(); confirmPaid = false }) { Text("처리") } },
            dismissButton = { TextButton(onClick = { confirmPaid = false }) { Text("취소") } },
        )
    }
    LaunchedEffect(Unit) {
        vm.uiEvent.collect { e ->
            when (e) {
                is CreditsViewModel.CreditsUiEvent.ShowToast ->
                    android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT).show()
                CreditsViewModel.CreditsUiEvent.RefreshCredits -> vm.loadCredits()
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("외상 관리", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("닫기") }
        }
        Row(Modifier.weight(1f).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(Modifier.fillMaxWidth(0.46f).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                LazyColumn(Modifier.padding(12.dp)) {
                    items(state.creditsList.toImmutableList(), key = { it.orderId }) { c ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable { vm.selectOrder(c.orderId, c.totalAmount, c.date); vm.fetchOrderDetail() }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${c.orderId}번 · ${c.orderer}", style = MaterialTheme.typography.bodyMedium)
                            Text("%,d원 · ${c.date}".format(c.totalAmount), style = MaterialTheme.typography.labelSmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
            Surface(Modifier.weight(1f).fillMaxHeight(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp)) {
                    Text("상세", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(state.orderDetails.toImmutableList(), key = { index, _ -> index }) { _, it ->
                            BasketRow(it.name, it.count, it.subtotal) {}
                        }
                    }
                    Button(
                        onClick = { confirmPaid = true },
                        enabled = state.selectedOrderNumber != 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("외상 결제완료 처리")
                    }
                }
            }
        }
    }
}
