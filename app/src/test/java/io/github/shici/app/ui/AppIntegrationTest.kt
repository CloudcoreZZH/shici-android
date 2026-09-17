package io.github.shici.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.shici.app.MainActivity
import io.github.shici.app.ShiciApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Exercises the actual activity, bundled 768k-word dictionary, ViewModel, and both databases. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = ShiciApplication::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun `real lookup two additions and one completed learning task`() {
        awaitText("persist")
        screenshot("home-real")
        compose.onNodeWithText("查词").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("address")
        val result = hasText("address") and !hasSetTextAction()
        compose.waitUntil(20_000) { compose.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(result).performClick()
        awaitText("加入词书 +1")
        screenshot("dictionary-real")
        compose.onNodeWithText("加入词书 +1").performClick()
        awaitText("累计加入 1 次")
        compose.onNodeWithText("继续查词").performClick()
        compose.onNodeWithText("再次加入词书 +1").performClick()
        awaitText("累计加入 2 次")
        compose.onNodeWithText("去学习").performClick()
        awaitText("累计加入 2 次 · 重学")
        compose.onNodeWithText("认识").performClick()
        compose.onNodeWithText("下一词").performClick()
        awaitText("2 / 2")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("词书").performClick()
        awaitText("待学习 1 次")
        compose.onNodeWithText("2 次").assertIsDisplayed()
        screenshot("wordbook-real")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String) {
        val folder = File("build/ui-screenshots").apply { mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
