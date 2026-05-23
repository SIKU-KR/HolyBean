package eloom.holybean.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class MenuTileTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsNameAndPrice_andClicks() {
        var clicked = false
        rule.setContent { MenuTile(name = "라떼", price = 4500, onClick = { clicked = true }) }
        rule.onNodeWithText("라떼").assertIsDisplayed()
        rule.onNodeWithText("4,500").assertIsDisplayed()
        rule.onNodeWithText("라떼").performClick()
        assert(clicked)
    }
}
