package eloom.holybean.ui.credits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.ui.components.BasketRow
import eloom.holybean.ui.components.buttons.AppTextButton
import eloom.holybean.ui.components.buttons.PrimaryButton
import eloom.holybean.ui.components.buttons.SecondaryButton
import eloom.holybean.ui.components.layout.Pane
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.theme.Dimens
import kotlinx.collections.immutable.toImmutableList

@Composable
fun CreditsRoute(onClose: () -> Unit, vm: CreditsViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var confirmPaid by remember { mutableStateOf(false) }
    if (confirmPaid) {
        AlertDialog(
            onDismissRequest = { confirmPaid = false },
            title = { Text("외상 결제완료", style = MaterialTheme.typography.titleMedium) },
            text = { Text("${state.selectedOrderNumber}번 외상을 결제완료 처리하시겠습니까?", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { AppTextButton("처리", onClick = { vm.handleDeleteButton(); confirmPaid = false }) },
            dismissButton = { AppTextButton("취소", onClick = { confirmPaid = false }) },
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
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "외상 관리",
                actions = {
                    SecondaryButton("닫기", onClick = onClose)
                },
            )
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.paneGap)) {
                Pane(Modifier.fillMaxWidth(Dimens.paneSplitWide).fillMaxHeight()) {
                    LazyColumn {
                        items(state.creditsList.toImmutableList(), key = { it.orderId }) { c ->
                            Column(
                                Modifier.fillMaxWidth()
                                    .clickable { vm.selectOrder(c.orderId, c.totalAmount, c.date); vm.fetchOrderDetail() }
                                    .padding(vertical = Dimens.spaceSm)
                            ) {
                                Text("${c.orderId}번 · ${c.orderer}", style = MaterialTheme.typography.bodyMedium)
                                Text("%,d원 · ${c.date}".format(c.totalAmount), style = MaterialTheme.typography.labelSmall)
                            }
                            HorizontalDivider()
                        }
                    }
                }
                Pane(Modifier.weight(1f).fillMaxHeight()) {
                    Text("상세", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(state.orderDetails.toImmutableList(), key = { index, _ -> index }) { _, it ->
                            BasketRow(it.name, it.count, it.subtotal) {}
                        }
                    }
                    PrimaryButton(
                        "외상 결제완료 처리",
                        onClick = { confirmPaid = true },
                        enabled = state.selectedOrderNumber != 0,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
