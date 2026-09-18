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

    @Test fun `supplementary definitions expand with offline attribution and historical year`() {
        awaitText("去查词")
        compose.onNodeWithText("查词").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("hospital")
        val result = hasText("hospital") and !hasSetTextAction()
        compose.waitUntil(20_000) { compose.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(result).performClick()
        awaitText("加入词书 +1")
        compose.onNodeWithText("全部释义").performClick()
        compose.onNodeWithText("展开维基补充释义", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("hospital · 原词条").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("名词 · 醫院").assertExists()
        screenshot("dictionary-reference-real")
        compose.onNodeWithText("NETEM 2024 参考词表收录 · 非 2027 大纲").performScrollTo().assertIsDisplayed()
    }

    @Test fun `real lookup two additions and one completed learning task`() {
        awaitText("去查词")
        screenshot("home-real")
        compose.onNodeWithText("查词").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("address")
        val result = hasText("address") and !hasSetTextAction()
        compose.waitUntil(20_000) { compose.onAllNodes(result).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(result).performClick()
        awaitText("加入词书 +1")
        screenshot("dictionary-real")
        compose.onNodeWithText("加入词书 +1").performClick()
        awaitText("考研生词本 · 已加入 1 次")
        awaitText("再次加入词书 +1")
        compose.onNodeWithText("再次加入词书 +1").performClick()
        awaitText("考研生词本 · 已加入 2 次")
        compose.onNodeWithText("去学习").performClick()
        awaitText("累计加入 2 次 · 本组学习")
        screenshot("learning-question-real")
        compose.onNodeWithText("显示答案").performClick()
        compose.onNodeWithText("记得").performClick()
        awaitText("这一组，完成了")
        compose.onNodeWithContentDescription("撤销上一条").performClick()
        awaitText("显示答案")
        compose.onNodeWithText("显示答案").performClick()
        screenshot("learning-answer-real")
        compose.onNodeWithText("记得").performClick()
        awaitText("这一组，完成了")
        compose.onNodeWithText("先到这里").performClick()
        compose.onNodeWithText("词书").performClick()
        awaitText("待学习 1 次")
        compose.onNodeWithText("2 次").assertIsDisplayed()
        screenshot("wordbook-real")
        compose.onNodeWithText("学习").performClick()
        awaitText("把见过的词，记住。")
        screenshot("home-progress-real")
        compose.onNodeWithText("开始学习").performClick()
        awaitText("显示答案")
        compose.onNodeWithText("显示答案").performClick()
        compose.onNodeWithText("记得").performClick()
        awaitText("这一组，完成了")
        compose.onNodeWithText("先到这里").performClick()
        awaitText("复习日历")
        compose.onNodeWithText("复习日历").performScrollTo().assertIsDisplayed()
        screenshot("review-calendar-real")
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
