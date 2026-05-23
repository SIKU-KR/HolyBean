package eloom.holybean.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.data.model.OrderItem
import eloom.holybean.data.model.OrdersDetailItem
import eloom.holybean.ui.components.*
import eloom.holybean.ui.orderlist.OrdersViewModel
import eloom.holybean.ui.theme.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun OrdersRoute(onClose: () -> Unit, viewModel: OrdersViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { e ->
            when (e) {
                is OrdersViewModel.OrdersUiEvent.ShowToast ->
                    android.widget.Toast.makeText(context, e.message, android.widget.Toast.LENGTH_SHORT).show()
                OrdersViewModel.OrdersUiEvent.RefreshOrders -> viewModel.loadOrdersOfDay()
            }
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("주문 삭제") },
            text = { Text("${state.selectedOrderNumber}번 주문을 삭제하시겠습니까? 복구할 수 없습니다.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteOrder(); confirmDelete = false }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
    OrdersScreen(
        summary = state.todaySummary,
        orders = state.ordersList.toImmutableList(),
        selectedOrderNumber = state.selectedOrderNumber,
        details = state.orderDetails.toImmutableList(),
        selectedTotal = state.selectedOrderTotal,
        onClose = onClose,
        onPrintReport = viewModel::printTodayReport,
        onSelect = { viewModel.selectOrder(it.orderId, it.totalAmount) },
        onReprint = viewModel::reprint,
        onDelete = { confirmDelete = true },
    )
}

@Composable
fun OrdersScreen(
    summary: OrdersViewModel.TodaySummary,
    orders: ImmutableList<OrderItem>,
    selectedOrderNumber: Int,
    details: ImmutableList<OrdersDetailItem>,
    selectedTotal: Int,
    onClose: () -> Unit,
    onPrintReport: () -> Unit,
    onSelect: (OrderItem) -> Unit,
    onReprint: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.screenPadding)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("주문기록", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClose) { Text("✕ 닫기") }
        }
        Surface(Modifier.fillMaxWidth().padding(bottom = 10.dp), shape = RoundedCornerShape(Dimens.paneRadius),
            color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                StatChip("오늘 총 판매", "%,d원".format(summary.totalSales), Orange)
                StatChip("총 건수", "${summary.orderCount}건")
                StatChip("총 잔수", "${summary.drinkCount}잔")
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onPrintReport) { Text("보고서 출력") }
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.gap)) {
            Surface(Modifier.fillMaxWidth(0.46f).fillMaxHeight(), shape = RoundedCornerShape(Dimens.paneRadius),
                color = MaterialTheme.colorScheme.surface) {
                LazyColumn(Modifier.padding(12.dp)) {
                    items(orders, key = { it.orderId }) { o ->
                        OrderListItem(o, o.orderId == selectedOrderNumber) { onSelect(o) }
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
            Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(Dimens.paneRadius),
                color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(12.dp)) {
                    Text("${selectedOrderNumber}번 주문", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(details, key = { index, _ -> index }) { _, d -> BasketRow(d.name, d.count, d.subtotal) {} }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("합계", style = MaterialTheme.typography.titleMedium)
                        Text("%,d원".format(selectedTotal), style = MaterialTheme.typography.titleMedium, color = Orange)
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onReprint, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange)) { Text("재출력") }
                        OutlinedButton(onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed)) { Text("삭제") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderListItem(o: OrderItem, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(9.dp),
        color = if (selected) OrangeContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Orange else DividerGray),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${o.orderId}번", style = MaterialTheme.typography.bodyMedium)
                Text("%,d원".format(o.totalAmount), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(o.orderer.ifBlank { "—" }, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
                Text(o.method, style = MaterialTheme.typography.labelSmall, color = OnSurfaceMuted)
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 900, heightDp = 500)
@Composable
private fun OrdersPreview() = HolyBeanTheme {
    OrdersScreen(
        summary = OrdersViewModel.TodaySummary(1240000, 86, 152),
        orders = persistentListOf(OrderItem(128, 15000, "현금", "홍길동")),
        selectedOrderNumber = 128,
        details = persistentListOf(OrdersDetailItem("아메리카노", 2, 7000)),
        selectedTotal = 15000, onClose = {}, onPrintReport = {}, onSelect = {}, onReprint = {}, onDelete = {},
    )
}
