package eloom.holybean.ui.payment

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import eloom.holybean.data.model.CartItem
import eloom.holybean.ui.components.BasketRow
import eloom.holybean.ui.components.PaymentMethodTile
import eloom.holybean.ui.components.SegmentedToggle
import eloom.holybean.ui.components.layout.Pane
import eloom.holybean.ui.components.layout.ScreenContainer
import eloom.holybean.ui.components.layout.ScreenHeader
import eloom.holybean.ui.components.layout.SectionLabel
import eloom.holybean.ui.components.layout.TotalRow
import eloom.holybean.ui.home.HomeViewModel
import eloom.holybean.ui.theme.Dimens
import eloom.holybean.ui.theme.DividerGray
import eloom.holybean.ui.theme.HolyBeanTheme
import eloom.holybean.ui.theme.OnSurfaceMuted
import eloom.holybean.ui.theme.Orange
import eloom.holybean.ui.theme.OrangeText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun PaymentRoute(sharedViewModel: HomeViewModel, onClose: () -> Unit, onPaid: () -> Unit) {
    val state by sharedViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // HomeViewModel 은 OrderFlow 그래프 스코프라 Home/Payment 가 동일 인스턴스의 uiEvent
    // (replay=0 fan-out SharedFlow)를 공유한다. 백그라운드 화면이 상대 화면의 일회성 이벤트를
    // 가로채/중복 처리하지 않도록 RESUMED 상태에서만 수집한다(HomeRoute 와 동일 규칙).
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            sharedViewModel.uiEvent.collect { event ->
                when (event) {
                    is HomeViewModel.UiEvent.ShowToast ->
                        android.widget.Toast.makeText(context, event.message, android.widget.Toast.LENGTH_SHORT).show()
                    HomeViewModel.UiEvent.NavigateHome -> onPaid()
                    HomeViewModel.UiEvent.NavigateToPayment -> Unit
                }
            }
        }
    }

    PaymentScreen(
        orderId = state.orderId,
        items = state.basketItems.toImmutableList(),
        total = state.totalPrice,
        isSubmitting = state.isSubmitting,
        onCancel = onClose,
        onConfirm = { selection ->
            PaymentForm.build(selection, state.basketItems, state.totalPrice, state.orderId, state.currentDate)
                .onSuccess { sharedViewModel.onOrderConfirmed(it, selection.cupOption) }
                .onFailure {
                    android.widget.Toast.makeText(context, it.message, android.widget.Toast.LENGTH_SHORT).show()
                }
        },
    )
}

@Composable
fun PaymentScreen(
    orderId: Int,
    items: ImmutableList<CartItem>,
    total: Int,
    isSubmitting: Boolean = false,
    onCancel: () -> Unit,
    onConfirm: (PaymentSelection) -> Unit,
) {
    var cup by rememberSaveable { mutableStateOf("일회용컵") }
    var first by rememberSaveable { mutableStateOf("현금") }
    var orderer by rememberSaveable { mutableStateOf("") }
    var split by rememberSaveable { mutableStateOf(false) }
    var second by rememberSaveable { mutableStateOf<String?>(null) }
    var secondAmt by rememberSaveable { mutableStateOf("") }

    // 무료제공 선택 시 분할 비활성화
    LaunchedEffect(first) { if (first == "무료제공") { split = false } }

    Box(Modifier.fillMaxSize()) {
        ScreenContainer {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(
                    "${orderId}번 주문 · 결제",
                    actions = {
                        OutlinedButton(
                            onClick = onCancel,
                            enabled = !isSubmitting,
                            modifier = Modifier.heightIn(min = Dimens.minTouchTarget),
                            shape = RoundedCornerShape(Dimens.radiusButton),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceMuted),
                        ) { Text("✕ 취소", style = MaterialTheme.typography.bodyMedium) }
                    },
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Dimens.paneGap)) {
                    Pane(Modifier.fillMaxWidth(Dimens.paneSplitNarrow).fillMaxHeight()) {
                        SectionLabel("주문 요약")
                        if (items.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text("담긴 상품이 없습니다", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceMuted)
                            }
                        } else {
                            LazyColumn(Modifier.weight(1f)) {
                                itemsIndexed(items, key = { index, _ -> index }) { _, it ->
                                    BasketRow(it.name, it.count, it.count * it.price, isCoupon = it.id == 999) {}
                                }
                            }
                        }
                        HorizontalDivider(color = DividerGray, modifier = Modifier.padding(vertical = Dimens.spaceSm))
                        TotalRow(total)
                    }
                    Pane(Modifier.weight(1f).fillMaxHeight(), padding = 0.dp) {
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Dimens.panePadding),
                            verticalArrangement = Arrangement.spacedBy(Dimens.sectionGap),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                                SectionLabel("컵 선택")
                                SegmentedToggle(persistentListOf("일회용컵", "머그컵"), cup) { cup = it }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                                SectionLabel("결제 수단")
                                MethodGrid(PaymentForm.methods.toImmutableList(), first) { first = it }
                                if (first != "무료제공") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(checked = split, onCheckedChange = { split = it })
                                        Text("  결제수단 추가 (분할결제)", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            if (split) {
                                val candidates = PaymentForm.secondCandidates(first).toImmutableList()
                                LaunchedEffect(first) {
                                    if (second !in candidates) {
                                        second = candidates.firstOrNull()
                                        secondAmt = ""
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                                    SectionLabel("2번째 결제 수단 (${first} 제외)")
                                    MethodRow(candidates, second) { second = it }
                                    OutlinedTextField(
                                        value = secondAmt, onValueChange = { secondAmt = it.filter(Char::isDigit) },
                                        label = { Text("${second ?: ""} 금액", style = MaterialTheme.typography.labelSmall) }, singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    val breakdown = PaymentForm.splitBreakdown(first, second, total, secondAmt)
                                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
                                        breakdown.forEach { line ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(line.label, style = MaterialTheme.typography.labelSmall, color = OrangeText)
                                                Text("%,d원".format(line.amount), style = MaterialTheme.typography.labelSmall, color = OrangeText)
                                            }
                                        }
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                                SectionLabel("주문자명")
                                OutlinedTextField(orderer, { orderer = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                        }
                        HorizontalDivider(color = DividerGray)
                        Button(
                            onClick = { onConfirm(PaymentSelection(cup, first, orderer, split, second, secondAmt)) },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth().padding(Dimens.panePadding).height(Dimens.primaryTouchTarget),
                            shape = RoundedCornerShape(Dimens.radiusButton),
                            colors = ButtonDefaults.buttonColors(containerColor = Orange),
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(Dimens.spaceSm))
                                Text("처리 중…", style = MaterialTheme.typography.titleMedium)
                            } else {
                                Text("결제 완료", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
        if (isSubmitting) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.08f))
                    .pointerInput(Unit) { detectTapGestures {} },
            )
        }
    }
}

@Composable
private fun MethodGrid(methods: ImmutableList<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        methods.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
                rowItems.forEach { m ->
                    PaymentMethodTile(m, m == selected, { onSelect(m) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MethodRow(methods: ImmutableList<String>, selected: String?, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
        methods.forEach { m -> PaymentMethodTile(m, m == selected, { onSelect(m) }, Modifier.weight(1f)) }
    }
}

@androidx.compose.ui.tooling.preview.Preview(widthDp = 900, heightDp = 500)
@Composable
private fun PaymentPreview() = HolyBeanTheme {
    PaymentScreen(128, persistentListOf(CartItem(1001, "아메리카노", 3500, 2, 7000)), 7000, onCancel = {}, onConfirm = {})
}
