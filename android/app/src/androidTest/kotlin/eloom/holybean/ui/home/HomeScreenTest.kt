package eloom.holybean.ui.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import eloom.holybean.data.model.CartItem
import eloom.holybean.data.model.MenuItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun checkoutDisabledWhenBasketEmpty() {
        rule.setContent {
            HomeScreen(
                categories = MenuCategories.names.toImmutableList(), selectedCategory = 0,
                menuItems = persistentListOf(MenuItem(1001, "아메리카노", 3500, 1, true)),
                basket = persistentListOf(), orderId = 1, total = 0,
                onCategory = {}, onMenuClick = {}, onCouponClick = {}, onSettingsClick = {},
                onBasketClick = {}, onHistoryClick = {}, onCheckout = {},
            )
        }
        rule.onNodeWithText("결제").assertIsNotEnabled()
    }

    @Test fun menuClickEmitsId() {
        var clickedId = -99
        rule.setContent {
            HomeScreen(
                categories = MenuCategories.names.toImmutableList(), selectedCategory = 0,
                menuItems = persistentListOf(MenuItem(1001, "아메리카노", 3500, 1, true)),
                basket = persistentListOf(CartItem(1001, "아메리카노", 3500, 1, 3500)),
                orderId = 1, total = 3500,
                onCategory = {}, onMenuClick = { clickedId = it }, onCouponClick = {}, onSettingsClick = {},
                onBasketClick = {}, onHistoryClick = {}, onCheckout = {},
            )
        }
        rule.onNodeWithText("아메리카노").performClick()
        assert(clickedId == 1001)
    }
}
