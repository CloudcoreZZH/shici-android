package io.github.shici.app.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import io.github.shici.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudyFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = Instant.parse("2026-09-17T08:00:00Z")
    private val entry = WordEntry("address", "əˈdres", listOf(Sense("1", "v. 处理；设法解决")))
    private fun state(revealed: Boolean, mode: SessionMode = SessionMode.REVIEW) = AppState(
        loading = false, now = now, snapshot = BookSnapshot(WordBook(1, "考研生词本"),
            listOf(BookWord("address", 4, 0, now, null)), 0),
        session = StudySession(1, mode, listOf(StudyItem("address")), entry = entry, revealed = revealed),
    )

    @Test fun `review question hides Chinese definition until reveal`() {
        var didReveal = false
        compose.setContent { TestTheme(Appearance.LIGHT) { StudyScreen(state(false), {}, {}, { didReveal = true }, {}) } }
        compose.onNodeWithText("address").assertIsDisplayed()
        compose.onNodeWithText("v. 处理；设法解决").assertDoesNotExist()
        screenshot("review-question")
        compose.onNodeWithText("显示答案").performClick()
        assertTrue(didReveal)
    }

    @Test fun `revealed review exposes four distinct ratings`() {
        var selected: Rating? = null
        compose.setContent { TestTheme(Appearance.DARK) { StudyScreen(state(true), {}, {}, {}, { selected = it }) } }
        compose.onNodeWithText("忘记").performClick()
        assertEquals(Rating.AGAIN, selected)
        compose.onNodeWithText("困难").assertIsDisplayed()
        compose.onNodeWithText("记得").assertIsDisplayed()
        compose.onNodeWithText("轻松").assertIsDisplayed()
        screenshot("review-answer-dark")
    }

    @Test fun `learning does not grade before user reveals and confirms`() {
        var revealed: Rating? = null
        var answered = false
        compose.setContent { TestTheme(Appearance.LIGHT) {
            StudyScreen(state(false, SessionMode.LEARN), {}, {}, { revealed = it }, { answered = true })
        } }
        compose.onNodeWithText("不认识").performClick()
        assertEquals(Rating.AGAIN, revealed)
        assertFalse(answered)
    }

    @Test fun `dictionary explains missing exam statistics instead of fabricating frequency`() {
        var added = 0
        compose.setContent { TestTheme(Appearance.LIGHT) { DictionaryScreen(entry, 3, "考研生词本", {}, {}, { added++ }) } }
        compose.onNodeWithText("暂无考研义项统计，以下按词典原顺序展示。").assertExists()
        screenshot("dictionary")
        compose.onNodeWithText("再次加入词书 +1").performClick()
        assertEquals(1, added)
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

@Composable private fun TestTheme(appearance: Appearance, content: @Composable () -> Unit) {
    ShiciTheme(appearance) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content) }
}
