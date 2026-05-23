package eloom.holybean.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import eloom.holybean.ui.components.BasketRow
import eloom.holybean.ui.components.MenuTile
import eloom.holybean.ui.components.TileStyle
import eloom.holybean.ui.theme.Dimens
import eloom.holybean.ui.theme.DividerGray
import eloom.holybean.ui.theme.HolyBeanTheme
import eloom.holybean.ui.theme.Orange
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
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        sharedViewModel.uiEvent.collect { event ->
            when (event) {
                is HomeViewModel.UiEvent.ShowToast ->
                    android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                HomeViewModel.UiEvent.NavigateToPayment -> onNavigateToPayment()
                HomeViewModel.UiEvent.NavigateHome -> Unit
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
    Row(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(Dimens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        Column(Modifier.weight(1f)) {
            CategoryChips(categories, selectedCategory, onCategory)
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(menuItems, key = { it.id }) { item ->
                    MenuTile(item.name, item.price, onClick = { onMenuClick(item.id) })
                }
                item(key = COUPON_TILE_ID) {
                    MenuTile("쿠폰", null, onClick = onCouponClick, style = TileStyle.Coupon)
                }
                item(key = SETTINGS_TILE_ID) {
                    MenuTile("⚙ 설정", null, onClick = onSettingsClick, style = TileStyle.Settings)
                }
            }
        }
        BasketPane(orderId, basket, total, onBasketClick, onHistoryClick, onCheckout,
            Modifier.fillMaxHeight().fillMaxWidth(Dimens.basketWidthFraction))
    }
}

@Composable
private fun CategoryChips(categories: ImmutableList<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        itemsIndexed(categories) { index, name ->
            FilterChip(selected = index == selected, onClick = { onSelect(index) }, label = { Text(name) })
        }
    }
}

@Composable
private fun BasketPane(
    orderId: Int, basket: ImmutableList<CartItem>, total: Int,
    onItemClick: (Int) -> Unit, onHistory: () -> Unit, onCheckout: () -> Unit, modifier: Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(Dimens.paneRadius), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${orderId}번 주문", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onHistory) { Text("주문기록") }
            }
            LazyColumn(Modifier.weight(1f)) {
                // 쿠폰은 항상 id=999 라 key=it.id 면 중복 → 인덱스 키 사용.
                itemsIndexed(basket, key = { index, _ -> index }) { _, item ->
                    BasketRow(item.name, item.count, item.count * item.price) { onItemClick(item.id) }
                    HorizontalDivider(color = DividerGray)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                Text("합계 ", style = MaterialTheme.typography.titleMedium)
                Text("%,d원".format(total), style = MaterialTheme.typography.titleMedium, color = Orange)
            }
            Button(
                onClick = onCheckout, enabled = basket.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Orange),
            ) { Text("결제 →") }
        }
    }
}

@Composable
private fun CouponAmountDialog(onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("쿠폰 금액 입력") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.filter(Char::isDigit) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { text.toIntOrNull()?.let(onConfirm) }) { Text("담기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 900, heightDp = 500)
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
