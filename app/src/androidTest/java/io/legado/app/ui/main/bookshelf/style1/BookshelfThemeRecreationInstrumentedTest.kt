package io.legado.app.ui.main.bookshelf.style1

import android.os.SystemClock
import android.view.View
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager.widget.ViewPager
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BookshelfHomeMode
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.bookshelf.style1.books.BooksFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookshelfThemeRecreationInstrumentedTest {

    @Test
    fun selectedGroupRemainsMountedAfterThemeChange() {
        assumeFalse(AppConfig.bookshelfHomeMode == BookshelfHomeMode.GROUP_GRID)
        val originalThemeMode = AppConfig.themeMode
        val targetThemeMode = if (originalThemeMode == DARK_THEME_MODE) {
            LIGHT_THEME_MODE
        } else {
            DARK_THEME_MODE
        }
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        try {
            scenario.onActivity { activity ->
                activity.findViewById<ViewPager>(R.id.view_pager_main)
                    .setCurrentItem(0, false)
            }
            val before = awaitReadyBookshelf(scenario)

            scenario.onActivity { activity ->
                ThemeConfig.applyThemeMode(activity, targetThemeMode)
            }
            val after = awaitReadyBookshelf(
                scenario = scenario,
                previousActivityIdentity = before.activityIdentity,
                expectedGroupId = before.groupId,
                expectedBookCount = before.bookCount,
            )

            assertEquals(before.groupId, after.groupId)
            assertEquals(before.bookCount, after.bookCount)
        } finally {
            if (AppConfig.themeMode != originalThemeMode) {
                runCatching {
                    var currentActivityIdentity: Int? = null
                    scenario.onActivity { activity ->
                        currentActivityIdentity = System.identityHashCode(activity)
                        ThemeConfig.applyThemeMode(activity, originalThemeMode)
                    }
                    awaitReadyBookshelf(
                        scenario = scenario,
                        previousActivityIdentity = currentActivityIdentity,
                    )
                }
            }
            scenario.close()
        }
    }

    private fun awaitReadyBookshelf(
        scenario: ActivityScenario<MainActivity>,
        previousActivityIdentity: Int? = null,
        expectedGroupId: Long? = null,
        expectedBookCount: Int? = null,
    ): BookshelfSnapshot {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        var latest: BookshelfSnapshot? = null
        while (SystemClock.uptimeMillis() < deadline) {
            runCatching {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    latest = activity.supportFragmentManager.snapshotBookshelf(activity)
                }
            }
            val snapshot = latest
            if (snapshot != null &&
                snapshot.ready &&
                (previousActivityIdentity == null ||
                    snapshot.activityIdentity != previousActivityIdentity) &&
                (expectedGroupId == null || snapshot.groupId == expectedGroupId) &&
                (expectedBookCount == null || snapshot.bookCount == expectedBookCount)
            ) {
                return snapshot
            }
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        fail("书架主题重建后未恢复: $latest")
        error("unreachable")
    }

    private fun FragmentManager.snapshotBookshelf(activity: MainActivity): BookshelfSnapshot? {
        val bookshelf = findBookshelfFragment() ?: return null
        val booksFragment = bookshelf.childFragmentManager.fragments
            .filterIsInstance<BooksFragment>()
            .firstOrNull { it.isAdded && !it.isDetached }
            ?: return null
        val container = activity.findViewById<View>(R.id.bookshelf_page_container)
            ?: return null
        val fragmentView = booksFragment.view
        val composeContent = fragmentView
            ?.findViewById<View>(R.id.compose_view)
            ?.let { composeView ->
                composeView.isShown &&
                    composeView.width > 0 &&
                    composeView.height > 0 &&
                    (composeView as? android.view.ViewGroup)?.childCount?.let { it > 0 } == true
            } == true
        return BookshelfSnapshot(
            activityIdentity = System.identityHashCode(activity),
            groupId = booksFragment.configuredGroupId,
            bookCount = booksFragment.getBooksCount(),
            ready = fragmentView != null &&
                fragmentView.parent === container &&
                container.isShown &&
                container.width > 0 &&
                container.height > 0 &&
                fragmentView.isShown &&
                composeContent,
        )
    }

    private fun FragmentManager.findBookshelfFragment(): BookshelfFragment1? {
        fragments.forEach { fragment ->
            if (fragment is BookshelfFragment1) return fragment
            fragment.childFragmentManager.findBookshelfFragment()?.let { return it }
        }
        return null
    }

    private data class BookshelfSnapshot(
        val activityIdentity: Int,
        val groupId: Long,
        val bookCount: Int,
        val ready: Boolean,
    )

    private companion object {
        const val LIGHT_THEME_MODE = "1"
        const val DARK_THEME_MODE = "2"
        const val TIMEOUT_MS = 15_000L
        const val POLL_INTERVAL_MS = 100L
    }
}
