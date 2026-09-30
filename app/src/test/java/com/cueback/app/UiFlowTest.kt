package com.cueback.app

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.cueback.app.core.model.UseCase
import com.cueback.app.data.repo.ReentryOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Drives the real activity, navigation, view models, database and engine. Screenshots are written to
 * build/ui-screens for visual review.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class UiFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<CueBackApp>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun tearDown() { scenario?.close() }

    private fun onboard() = runBlocking { app.container.settings.update { it.copy(onboarded = true) } }

    private fun launch() { scenario = ActivityScenario.launch(MainActivity::class.java) }

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File("build/ui-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun onboardingWalkthrough() {
        runBlocking { app.container.settings.update { it.copy(onboarded = false) } }
        launch()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Continue")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Don't save the task.\nSave your place.").assertExists()
        shot("01_onboarding_hook")
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("What CueBack sees").assertExists()
        shot("02_onboarding_privacy")
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Study").performClick()
        shot("02b_onboarding_usecase")
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Turn on automatic detection").assertExists()
        shot("03_onboarding_setup")
        rule.onNodeWithText("Start using CueBack").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Pick up where you left off.")).fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            val s = app.container.settings.current()
            assertTrue(s.onboarded)
            assertEquals(UseCase.STUDY, s.useCase)
        }
        shot("04_home_empty")
    }

    @Test fun demoWarmStartResumeAndReentry() {
        onboard()
        launch()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Replay the demo story (debug build)")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Replay the demo story (debug build)").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Welcome back.")).fetchSemanticsNodes().isNotEmpty() }
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithText("Run the expired-token test in auth/refresh_test.go").assertExists()
        rule.onNodeWithText("Expired-token request still returns 401").assertExists()
        rule.onNodeWithText("While you were away").assertExists()
        rule.onNode(hasText("Pulled away by", substring = true)).assertExists()
        shot("05_warm_start_card")

        rule.onNodeWithText("Resume where I left off").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Measuring your re-entry")).fetchSemanticsNodes().isNotEmpty() }
        // The exact anchor (the shared RFC section) was launched.
        val launched = Shadows.shadowOf(app).nextStartedActivity
        assertEquals("https://datatracker.ietf.org/doc/html/rfc6749#section-6", launched.dataString)
        shot("06_warm_start_measuring")

        rule.onNodeWithText("I'm back on track").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Back in", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        shot("07_reentry_result")
        runBlocking {
            val rec = app.container.reentry.all().single()
            assertEquals(ReentryOutcome.COMPLETED, rec.outcome)
            assertTrue(rec.session.reentryMs!! >= 0)
        }
    }

    @Test fun manualCaptureShowsOnHomeAndInLibrary() {
        onboard()
        launch()
        rule.waitUntil(5_000) { rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Save my place")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescriptionSafe("Save my place")
        rule.onNodeWithText("What were you about to do next?").performTextInput("Outline section 3 of the essay")
        rule.onNodeWithText("Task").performTextInput("Climate essay")
        shot("08_capture")
        rule.onNode(hasText("Save my place") and androidx.compose.ui.test.hasClickAction()).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Next: Outline section 3 of the essay")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Climate essay").assertExists()
        shot("09_home_with_context")

        rule.onNodeWithContentDescriptionSafe("Search contexts")
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Library")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Search task, link, file, or app").performTextInput("essay")
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Climate essay")).fetchSemanticsNodes().isNotEmpty() }
        shot("10_library")
    }

    @Test fun contextDetailShowsThePlaceAndItsActions() {
        onboard()
        runBlocking {
            val c = app.container
            c.detection.saveManual(
                c.builder.manual(c.newId(), c.clock(), "Quarterly report", "Send the draft to finance", "Pulled the Q3 numbers", "Waiting on the travel budget", "Write the summary paragraph", null, UseCase.WRITING),
            )
        }
        launch()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Quarterly report")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Quarterly report").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Mark done")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Write the summary paragraph").assertExists()
        rule.onNodeWithText("Waiting on the travel budget").assertExists()
        rule.onNodeWithText("Resume").assertExists()
        shot("12_context_detail")
    }

    @Test fun notificationDeepLinkOpensWarmStart() {
        onboard()
        val id = runBlocking { app.container.detection.replayDemo()!! }
        scenario = ActivityScenario.launch(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("cueback://warmstart/$id?src=notification"), app, MainActivity::class.java),
        )
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Welcome back.")).fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            assertEquals(com.cueback.app.data.repo.WarmStartSource.NOTIFICATION, app.container.reentry.all().single().source)
        }
    }

    @Test fun settingsRendersPrivacyControls() {
        onboard()
        launch()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Pick up where you left off.")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescriptionSafe("Settings")
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Usage access")).fetchSemanticsNodes().isNotEmpty() }
        shot("11_settings")
        val list = rule.onNode(androidx.compose.ui.test.hasScrollToNodeAction())
        list.performScrollToNode(hasText("Delete all CueBack data"))
        rule.onNodeWithText("Delete all CueBack data").assertExists()
        // The subscription line depends on whether keys are set locally, so check the section, not a specific state.
        list.performScrollToNode(hasText("Subscription"))
        rule.onNodeWithText("Subscription").assertExists()
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onNodeWithContentDescriptionSafe(desc: String) {
        onNode(androidx.compose.ui.test.hasContentDescription(desc)).performClick()
    }
}
