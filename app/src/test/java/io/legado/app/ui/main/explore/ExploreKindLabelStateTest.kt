package io.legado.app.ui.main.explore

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.CacheManager
import io.legado.app.utils.InfoMap
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
class ExploreKindLabelStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun staticLabelUpdatesWhenReusedSlotReceivesAnotherKind() {
        var kind by mutableStateOf(category("全本小说"))
        val sourceUrl = "explore-kind-label-state-test"
        CacheManager.putMemory("infoMap_$sourceUrl", "{}")
        val infoMap = InfoMap(sourceUrl)

        composeRule.setContent {
            val label by rememberExploreKindLabel(kind, null, infoMap)
            Text(label)
        }

        composeRule.onNodeWithText("全本小说").assertExists()

        composeRule.runOnIdle {
            kind = category("修真")
        }

        composeRule.onNodeWithText("修真").assertExists()
        composeRule.onNodeWithText("全本小说").assertDoesNotExist()
    }

    private fun category(title: String) = ExploreKind(
        title = title,
        url = "/$title"
    )
}
