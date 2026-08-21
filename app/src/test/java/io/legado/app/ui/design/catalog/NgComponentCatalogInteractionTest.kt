package io.legado.app.ui.design.catalog

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = "w411dp-h891dp-xxhdpi",
    application = Application::class
)
class NgComponentCatalogInteractionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun primaryButtonShowsInteractionFeedback() {
        composeRule.setContent {
            NgComponentCatalog(initialThemeId = "warm")
        }

        composeRule.onNodeWithText("主操作").performClick()

        composeRule.onNodeWithText("已触发主操作").assertExists()
    }

    @Test
    fun settingsRowTogglesSwitchAndShowsFeedback() {
        composeRule.setContent {
            NgComponentCatalog(initialThemeId = "warm")
        }

        composeRule.onNodeWithTag("ng_catalog_compose_switch").performClick()

        composeRule.onNodeWithText("Compose 开关已关闭").assertExists()
    }

    @Test
    fun themeChipChangesThemeAndShowsFeedback() {
        composeRule.setContent {
            NgComponentCatalog(initialThemeId = "warm")
        }

        composeRule.onNodeWithText("竹影").performClick()

        composeRule.onNodeWithText("已切换到竹影主题").assertExists()
    }
}
