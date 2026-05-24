package eloom.holybean.ui.orders

import androidx.compose.foundation.BorderStroke
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
import eloom.holybean.ui.components.buttons.*
import eloom.holybean.ui.components.layout.Pane
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.components.layout.TotalRow
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
            title = { Text("주문 삭제", style = MaterialTheme.typography.titleMedium) },
            text = { Text("${state.selectedOrderNumber}번 주문을 삭제하시겠습니까? 복구할 수 없습니다.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { AppTextButton("삭제", onClick = { viewModel.deleteOrder(); confirmDelete = false }) },
            dismissButton = { AppTextButton("취소", onClick = { confirmDelete = false }) },
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
    ScreenContainer {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "주문기록",
                actions = {
                    SecondaryButton("닫기", onClick = onClose)
                },
            )
            Pane(Modifier.fillMaxWidth().padding(bottom = Dimens.spaceSm)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                ) {
                    StatChip("오늘 총 판매", "%,d원".format(summary.totalSales), OrangeOnContainer)
                    VerticalDivider(Modifier.height(Dimens.spaceXl), color = DividerGray)
                    StatChip("총 건수", "${summary.orderCount}건")
                    VerticalDivider(Modifier.height(Dimens.spaceXl), color = DividerGray)
                    StatChip("총 잔수", "${summary.drinkCount}잔")
                    Spacer(Modifier.weight(1f))
                    SecondaryButton("보고서 출력", onClick = onPrintReport)
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.paneGap)) {
                Pane(Modifier.fillMaxWidth(Dimens.paneSplitWide).fillMaxHeight()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Dimens.itemGap)) {
                        items(orders, key = { it.orderId }) { o ->
                            OrderListItem(o, o.orderId == selectedOrderNumber) { onSelect(o) }
                        }
                    }
                }
                Pane(Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        if (selectedOrderNumber == 0) "주문을 선택하세요" else "${selectedOrderNumber}번 주문",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(details, key = { index, _ -> index }) { _, d -> BasketRow(d.name, d.count, d.subtotal) {} }
                    }
                    HorizontalDivider(color = DividerGray, modifier = Modifier.padding(vertical = Dimens.spaceSm))
                    TotalRow(selectedTotal)
                    Row(
                        Modifier.fillMaxWidth().padding(top = Dimens.spaceMd),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
                    ) {
                        PrimaryButton("재출력", onClick = onReprint, modifier = Modifier.weight(1f))
                        DangerButton("삭제", onClick = onDelete)
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
        onClick = onClick, shape = RoundedCornerShape(Dimens.radiusButton),
        color = if (selected) OrderSelectedBg else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) Orange else DividerGray),
    ) {
        Column(Modifier.fillMaxWidth().padding(Dimens.spaceSm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${o.orderId}번", style = MaterialTheme.typography.titleMedium)
                Text("%,d원".format(o.totalAmount), style = MaterialTheme.typography.titleMedium)
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
