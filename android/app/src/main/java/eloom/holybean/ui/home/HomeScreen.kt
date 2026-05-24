package eloom.holybean.ui.home

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import eloom.holybean.ui.components.buttons.AppTextButton
import eloom.holybean.ui.components.buttons.PrimaryButton
import eloom.holybean.ui.components.buttons.SecondaryButton
import eloom.holybean.ui.components.BasketRow
import eloom.holybean.ui.components.MenuTile
import eloom.holybean.ui.components.TileStyle
import eloom.holybean.ui.components.layout.Pane
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.TotalRow
import eloom.holybean.ui.theme.Dimens
import eloom.holybean.ui.theme.HolyBeanTheme
import eloom.holybean.ui.theme.OnSurface
import eloom.holybean.ui.theme.OnSurfaceMuted
import eloom.holybean.ui.theme.Orange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

private const val COUPON_TILE_ID = -1
private const val SETTINGS_TILE_ID = -2

@Composable
fun HomeRoute(
    sharedViewModel: HomeViewModel,
    onNavigateToPayment: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val state by sharedViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // HomeViewModel 은 OrderFlow 그래프 스코프라 Home/Payment 가 동일 인스턴스의 uiEvent
    // (replay=0 fan-out SharedFlow)를 공유한다. 백그라운드 화면이 상대 화면의 일회성 이벤트를
    // 가로채/중복 처리하지 않도록 RESUMED 상태에서만 수집한다(Payment 등 다른 라우트도 동일 규칙).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            sharedViewModel.uiEvent.collect { event ->
                when (event) {
                    is HomeViewModel.UiEvent.ShowToast ->
                        Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    HomeViewModel.UiEvent.NavigateToPayment -> onNavigateToPayment()
                    HomeViewModel.UiEvent.NavigateHome -> Unit
                }
            }
        }
    }

    var couponDialog by remember { mutableStateOf(false) }
    if (couponDialog) {
        CouponAmountDialog(
            onConfirm = { amount -> sharedViewModel.addCoupon(amount); couponDialog = false },
            onDismiss = { couponDialog = false },
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.printFailure) {
        val failure = state.printFailure ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = printFailureMessage(failure),
            actionLabel = "재출력",
            duration = SnackbarDuration.Indefinite,
            withDismissAction = true,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> sharedViewModel.reprintLastOrder()
            SnackbarResult.Dismissed -> sharedViewModel.dismissPrintFailure()
        }
    }

    Box(Modifier.fillMaxSize()) {
        HomeScreen(
            categories = MenuCategories.names.toImmutableList(),
            selectedCategory = state.selectedCategoryIndex,
            menuItems = state.filteredMenuItems.toImmutableList(),
            basket = state.basketItems.toImmutableList(),
            orderId = state.orderId,
            total = state.totalPrice,
            onCategory = sharedViewModel::onCategorySelected,
            onMenuClick = sharedViewModel::addToBasket,
            onCouponClick = { couponDialog = true },
            onSettingsClick = onNavigateToSettings,
            onBasketClick = sharedViewModel::deleteFromBasket,
            onHistoryClick = onNavigateToOrders,
            onCheckout = sharedViewModel::onCheckoutClicked,
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun HomeScreen(
    categories: ImmutableList<String>,
    selectedCategory: Int,
    menuItems: ImmutableList<MenuItem>,
    basket: ImmutableList<CartItem>,
    orderId: Int,
    total: Int,
    onCategory: (Int) -> Unit,
    onMenuClick: (Int) -> Unit,
    onCouponClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBasketClick: (Int) -> Unit,
    onHistoryClick: () -> Unit,
    onCheckout: () -> Unit,
) {
    ScreenContainer {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.paneGap),
        ) {
            Column(Modifier.weight(1f)) {
                CategoryChips(categories, selectedCategory, onCategory)
                Spacer(Modifier.height(Dimens.spaceSm))
                val gridState = rememberLazyGridState()
                // 카테고리를 바꾸면 이전 스크롤 위치가 남지 않도록 맨 위로 되돌린다.
                LaunchedEffect(selectedCategory) { gridState.scrollToItem(0) }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.gridGap),
                    verticalArrangement = Arrangement.spacedBy(Dimens.gridGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(menuItems, key = { it.id }) { item ->
                        MenuTile(item.name, item.price, onClick = { onMenuClick(item.id) })
                    }
                    item(key = COUPON_TILE_ID) {
                        MenuTile("쿠폰", null, onClick = onCouponClick, style = TileStyle.Coupon)
                    }
                    item(key = SETTINGS_TILE_ID) {
                        MenuTile(
                            "설정", null,
                            onClick = onSettingsClick,
                            style = TileStyle.Settings,
                            icon = Icons.Filled.Settings,
                        )
                    }
                }
            }
            BasketPane(
                orderId, basket, total, onBasketClick, onHistoryClick, onCheckout,
                Modifier.fillMaxHeight().fillMaxWidth(Dimens.paneSplitNarrow),
            )
        }
    }
}

@Composable
private fun CategoryChips(categories: ImmutableList<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm),
    ) {
        itemsIndexed(categories) { index, name ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(name, style = MaterialTheme.typography.bodyMedium) },
                shape = RoundedCornerShape(Dimens.radiusChip),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Orange,
                    selectedLabelColor = OnSurface,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = index == selected,
                    selectedBorderColor = Orange,
                ),
            )
        }
    }
}

@Composable
private fun BasketPane(
    orderId: Int, basket: ImmutableList<CartItem>, total: Int,
    onItemClick: (Int) -> Unit, onHistory: () -> Unit, onCheckout: () -> Unit, modifier: Modifier,
) {
    Pane(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${orderId}번 주문", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            SecondaryButton("주문기록", onClick = onHistory)
        }
        Spacer(Modifier.height(16.dp))
        if (basket.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("담긴 상품이 없습니다", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                // 쿠폰은 항상 id=999 라 key=it.id 면 중복 → 인덱스 키 사용.
                itemsIndexed(basket, key = { index, _ -> index }) { _, item ->
                    BasketRow(item.name, item.count, item.count * item.price, isCoupon = item.id == 999) {
                        onItemClick(item.id)
                    }
                }
            }
        }
        TotalRow(total, Modifier.padding(vertical = Dimens.spaceSm))
        PrimaryButton(
            "결제",
            onClick = onCheckout,
            modifier = Modifier.fillMaxWidth(),
            enabled = basket.isNotEmpty(),
        )
    }
}

@Composable
private fun CouponAmountDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("쿠폰 금액 입력", style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = { AppTextButton("담기", onClick = { text.toIntOrNull()?.let(onConfirm) }) },
        dismissButton = { AppTextButton("취소", onClick = onDismiss) },
    )
}

@Preview(widthDp = 900, heightDp = 500)
@Composable
private fun HomeScreenPreview() = HolyBeanTheme {
    HomeScreen(
        categories = MenuCategories.names.toImmutableList(), selectedCategory = 0,
        menuItems = persistentListOf(MenuItem(1001, "아메리카노", 3500, 1, true)),
        basket = persistentListOf(CartItem(1001, "아메리카노", 3500, 2, 7000)),
        orderId = 128, total = 7000,
        onCategory = {}, onMenuClick = {}, onCouponClick = {}, onSettingsClick = {},
        onBasketClick = {}, onHistoryClick = {}, onCheckout = {},
    )
}

private fun printFailureMessage(f: HomeViewModel.PrintFailure): String {
    val prefix = "${f.orderNum}번 영수증 출력 실패 — "
    return prefix + when (f.reason) {
        eloom.holybean.printer.network.PrintFailureReason.ServerUnreachable ->
            "프린터 서버에 연결되지 않았어요. Pi 전원·네트워크 확인 후 재출력하세요."
        eloom.holybean.printer.network.PrintFailureReason.PrinterOffline ->
            "프린터가 응답하지 않아요. 전원·USB 연결 확인 후 재출력하세요."
        eloom.holybean.printer.network.PrintFailureReason.PrinterError ->
            "출력에 실패했어요. 용지·덮개 상태 확인 후 재출력하세요."
        eloom.holybean.printer.network.PrintFailureReason.Unknown ->
            "다시 출력해 주세요."
    }
}
