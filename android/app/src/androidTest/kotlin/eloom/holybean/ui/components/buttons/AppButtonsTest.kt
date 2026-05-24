package eloom.holybean.ui.components.buttons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class AppButtonsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun primary_click_invokesCallback() {
        var clicked = false
        rule.setContent { PrimaryButton("결제 완료", onClick = { clicked = true }) }
        rule.onNodeWithText("결제 완료").assertIsDisplayed().performClick()
        assert(clicked)
    }

    @Test fun primary_disabled_doesNotClick() {
        var clicked = false
        rule.setContent { PrimaryButton("결제 완료", onClick = { clicked = true }, enabled = false) }
        rule.onNodeWithText("결제 완료").assertIsNotEnabled().performClick()
        assert(!clicked)
    }

    @Test fun primary_loading_doesNotClick() {
        var clicked = false
        rule.setContent { PrimaryButton("결제 완료", onClick = { clicked = true }, loading = true) }
        rule.onNodeWithText("결제 완료").assertIsNotEnabled().performClick()
        assert(!clicked)
    }

    @Test fun secondary_click_invokesCallback() {
        var clicked = false
        rule.setContent { SecondaryButton("취소", onClick = { clicked = true }) }
        rule.onNodeWithText("취소").performClick()
        assert(clicked)
    }

    @Test fun danger_click_invokesCallback() {
        var clicked = false
        rule.setContent { DangerButton("삭제", onClick = { clicked = true }) }
        rule.onNodeWithText("삭제").performClick()
        assert(clicked)
    }

    @Test fun textButton_click_invokesCallback() {
        var clicked = false
        rule.setContent { AppTextButton("담기", onClick = { clicked = true }) }
        rule.onNodeWithText("담기").performClick()
        assert(clicked)
    }

    @Test fun iconButton_click_invokesCallback() {
        var clicked = false
        rule.setContent {
            AppIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "추가",
                onClick = { clicked = true },
            )
        }
        rule.onNodeWithContentDescription("추가").performClick()
        assert(clicked)
    }
}
