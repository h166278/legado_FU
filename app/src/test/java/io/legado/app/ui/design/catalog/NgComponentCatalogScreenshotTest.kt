package io.legado.app.ui.design.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import android.app.Application
import com.github.takahirom.roborazzi.captureRoboImage
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
class NgComponentCatalogScreenshotTest {

    @Test
    fun warmCatalog() {
        captureRoboImage {
            NgComponentCatalog(
                initialThemeId = "warm",
                showThemePicker = false
            )
        }
    }

    @Test
    fun bambooCatalog() {
        captureRoboImage {
            NgComponentCatalog(initialThemeId = "bamboo", showThemePicker = false)
        }
    }

    @Test
    fun mistCatalog() {
        captureRoboImage {
            NgComponentCatalog(initialThemeId = "mist", showThemePicker = false)
        }
    }

    @Test
    fun darkCatalog() {
        captureRoboImage {
            NgComponentCatalog(initialThemeId = "dark", showThemePicker = false)
        }
    }

    @Test
    fun einkCatalog() {
        captureRoboImage {
            NgComponentCatalog(
                initialThemeId = "eink",
                showThemePicker = false
            )
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h900dp-xxhdpi")
    fun warmCatalogWide() {
        captureRoboImage {
            NgComponentCatalog(initialThemeId = "warm", showThemePicker = false)
        }
    }

    @Test
    fun warmCatalogLargeFont() {
        captureRoboImage {
            NgComponentCatalog(
                initialThemeId = "warm",
                showThemePicker = false,
                fontScale = 1.3f
            )
        }
    }
}
