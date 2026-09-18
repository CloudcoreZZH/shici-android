package io.github.shici.app.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
        session = StudySession(StudyProgress("test", 1, mode, listOf(StudyItem("address")), 1, startedAt = now), entry = entry, revealed = revealed),
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
        var revealed = false
        var answered = false
        compose.setContent { TestTheme(Appearance.LIGHT) {
            StudyScreen(state(false, SessionMode.LEARN), {}, {}, { revealed = true }, { answered = true })
        } }
        compose.onNodeWithText("显示答案").performClick()
        assertTrue(revealed)
        assertFalse(answered)
    }

    @Test fun `dictionary explains missing exam statistics instead of fabricating frequency`() {
        var added = 0
        compose.setContent { TestTheme(Appearance.LIGHT) { DictionaryScreen(entry, 3, "考研生词本", {}, {}, { added++ }) } }
        compose.onNodeWithText("本词暂无编辑优先级，以下按词典原顺序展示。").assertExists()
        screenshot("dictionary")
        compose.onNodeWithText("再次加入词书 +1").performClick()
        assertEquals(1, added)
    }

    @Test fun `review overview does not leak Chinese answers`() {
        val card = FsrsScheduler().review(null, Rating.EASY, now).copy(dueAt = now)
        val overview = state(false).copy(session = null,
            snapshot = BookSnapshot(WordBook(1, "考研生词本"), listOf(BookWord("address", 4, 0, now, card)), 0),
            meanings = mapOf("address" to "v. 处理；设法解决"))
        compose.setContent { TestTheme(Appearance.LIGHT) { ReviewOverview(overview, {}, {}) } }
        compose.onNodeWithText("address").assertIsDisplayed()
        compose.onNodeWithText("v. 处理；设法解决").assertDoesNotExist()
        screenshot("review-overview")
    }

    @Test fun `waiting group cannot reveal or grade early`() {
        val waiting = state(false, SessionMode.LEARN).let { original -> original.copy(session = original.session!!.copy(
            progress = original.session.progress.copy(items = listOf(StudyItem("address", availableAt = now.plusSeconds(86400))),
                answers = 1, forgotten = 1, lastActionId = "forgot"))) }
        compose.setContent { TestTheme(Appearance.LIGHT) { StudyScreen(waiting, {}, {}, {}, {}) } }
        compose.onNodeWithText("给记忆一点间隔").assertIsDisplayed()
        compose.onNodeWithText("显示答案").assertDoesNotExist()
        compose.onNodeWithText("记得").assertDoesNotExist()
        compose.onNodeWithContentDescription("撤销上一条").assertIsDisplayed()
        screenshot("learning-waiting")
    }

    @Test fun `finished group reports failure without a mastery claim and allows undo`() {
        val finished = state(true).let { original -> original.copy(session = original.session!!.copy(
            progress = original.session.progress.copy(items = emptyList(), completed = 1, answers = 1,
                forgotten = 1, finishedAt = now, lastActionId = "forgot"))) }
        var undone = false
        compose.setContent { TestTheme(Appearance.LIGHT) { StudyScreen(finished, {}, {}, {}, {}, { undone = true }) } }
        compose.onNodeWithText("这一组，完成了").assertIsDisplayed()
        compose.onNodeWithText("未能回忆").assertIsDisplayed()
        compose.onNodeWithContentDescription("撤销上一条").performClick()
        assertTrue(undone)
        screenshot("group-result")
    }

    @Test @Config(qualifiers = "w891dp-h411dp-land")
    fun `landscape keeps all rating actions reachable`() {
        compose.setContent { TestTheme(Appearance.LIGHT) { StudyScreen(state(true), {}, {}, {}, {}) } }
        Rating.entries.forEach { compose.onNodeWithText(it.label).assertIsDisplayed() }
        compose.onNodeWithText("v. 处理；设法解决").assertIsDisplayed()
        screenshot("review-landscape")
    }

    @Test fun `large system font keeps answer and rating actions accessible`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                TestTheme(Appearance.LIGHT) { StudyScreen(state(true), {}, {}, {}, {}) }
            }
        }
        Rating.entries.forEach { compose.onNodeWithText(it.label).assertIsDisplayed() }
        screenshot("review-large-font")
    }

    @Test fun `answer transition finishes without leaving a definition on the next question`() {
        val page = mutableStateOf(state(false))
        compose.setContent { TestTheme(Appearance.LIGHT) {
            StudyScreen(page.value, {}, {}, { page.value = page.value.copy(session = page.value.session!!.copy(revealed = true)) }, {})
        } }
        compose.onNodeWithText("显示答案").performClick()
        compose.onNodeWithText("v. 处理；设法解决").assertIsDisplayed()
        compose.runOnIdle {
            val previous = page.value.session!!
            page.value = page.value.copy(session = previous.copy(revealed = false, entry = WordEntry("claim", "", listOf(Sense("c", "v. 声称"))),
                progress = previous.progress.copy(items = listOf(StudyItem("claim")))))
        }
        compose.onNodeWithText("claim").assertIsDisplayed()
        compose.onNodeWithText("v. 声称").assertDoesNotExist()
        compose.onNodeWithText("v. 处理；设法解决").assertDoesNotExist()
        compose.onNodeWithText("显示答案").assertIsDisplayed()
    }

    @Test fun `Android sixteen requests 120 Hz on the Compose view and restores its previous vote`() {
        val enabled = mutableStateOf(true)
        var root: android.view.View? = null
        compose.setContent {
            val view = LocalView.current
            SideEffect { root = view }
            if (enabled.value) HighRefreshRateEffect()
        }
        compose.runOnIdle { assertEquals(120f, root!!.requestedFrameRate, 0f); enabled.value = false }
        compose.runOnIdle { assertNotEquals(120f, root!!.requestedFrameRate) }
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
