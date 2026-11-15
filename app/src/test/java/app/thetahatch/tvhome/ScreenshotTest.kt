package app.thetahatch.tvhome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.thetahatch.tvhome.data.Day
import app.thetahatch.tvhome.data.Entry
import app.thetahatch.tvhome.data.Event
import app.thetahatch.tvhome.data.Forecast
import app.thetahatch.tvhome.skin.paletteFor
import app.thetahatch.tvhome.ui.HomeScreen
import app.thetahatch.tvhome.ui.LocalPalette
import app.thetahatch.tvhome.ui.PinPad
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZonedDateTime

/** 見本の画面（docs/screenshots）。テレビ 1920×1080（xhdpi で 960×540dp）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-television-xhdpi", application = TvHomeApp::class)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun sample(app: TvHomeApp) {
        val now = ZonedDateTime.now()
        app.state.entries.value = listOf(
            Entry("tv", "テレビ", "📺", android.content.Intent(), "auto", "fixed"),
            Entry("terrestrial", "ちデジ", "📡", null, "none", "fixed"),
            Entry("bs", "BS", "🛰", null, "none", "fixed"),
            Entry("recordings", "ろくが", "⏺", null, "none", "fixed"),
            Entry("input:hdmi2", "ゲーム", "🎮", android.content.Intent(), "set", "input"),
        )
        val t = System.currentTimeMillis()
        app.state.forecast.value = Forecast(14.0, 1, true, listOf(Day(t, 1, 18.0, 9.0, 10), Day(t, 61, 15.0, 10.0, 70), Day(t, 0, 19.0, 8.0, 0)), t)
        app.state.events.value = listOf(
            Event("ピアノ", now.plusHours(2), now.plusHours(3), false),
            Event("遠足", now.plusDays(1).withHour(0), null, true),
            Event("歯医者", now.plusDays(1).withHour(16).withMinute(30), null, false),
        )
    }

    private fun shot(skin: String, name: String) {
        val app = ApplicationProvider.getApplicationContext<TvHomeApp>()
        app.store.skin = skin
        sample(app)
        compose.setContent {
            CompositionLocalProvider(LocalPalette provides paletteFor(skin, true)) {
                Box(Modifier.fillMaxSize().background(LocalPalette.current.bg)) {
                    HomeScreen(app, remember { FocusRequester() }, {}, {}, { it() })
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png")
    }

    @Test fun homeDark() = shot("dark", "home-dark")
    @Test fun homePop() = shot("pop", "home-pop")

    @Test fun pin() {
        compose.setContent {
            CompositionLocalProvider(LocalPalette provides paletteFor("dark", true)) { PinPad("おうちの人の番号", {}, {}) }
        }
        compose.onRoot().captureRoboImage("../docs/screenshots/pin.png")
    }
}
