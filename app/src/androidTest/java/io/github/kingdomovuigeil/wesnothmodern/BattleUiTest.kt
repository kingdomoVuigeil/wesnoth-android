/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.kingdomovuigeil.wesnothmodern

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.kingdomovuigeil.wesnothmodern.game.*
import io.github.kingdomovuigeil.wesnothmodern.ui.BattleStore
import io.github.kingdomovuigeil.wesnothmodern.ui.WesnothApp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BattleUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun start(ai: Boolean = false) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { WesnothApp(newGame("patrol", ai, seed = 1)) }
        }
        compose.waitForIdle()
    }
    private fun hex(column: Int, row: Int) {
        val h = Hex.at(column, row)
        compose.onNodeWithTag("hex-${h.q}-${h.r}", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitForIdle()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun movementPreviewAndWeaponSelectionWork() {
        start()
        hex(2, 3)
        hex(3, 3)
        compose.onNodeWithTag("confirm-move").assertIsDisplayed()
        screenshot("01-path-preview-portrait")
        compose.onNodeWithTag("confirm-move").performClick()
        compose.waitForIdle()
        val h = Hex.at(3, 3)
        compose.onNodeWithTag("hex-${h.q}-${h.r}", true).assertContentDescriptionContains("Elvish Fighter", substring = true)
        hex(4, 3)
        compose.onNodeWithText("Choose your attack").assertIsDisplayed()
        compose.onNodeWithTag("weapon-1").performClick()
        compose.onNodeWithText("No retaliation").assertExists()
        screenshot("02-attack-preview")
        compose.onNodeWithTag("confirm-attack").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Choose your attack").fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
    }

    @Test fun recruitingConsumesGoldAndCreatesAUnit() {
        start()
        compose.onNodeWithTag("recruit").performClick()
        compose.onNodeWithTag("recruit-fighter").assertIsEnabled()
        screenshot("03-recruitment")
        compose.onNodeWithTag("recruit-fighter").performClick()
        compose.onNodeWithText("36 gold").assertIsDisplayed()
        compose.onNodeWithText("Elvish Fighter recruited. Ready next turn.").assertExists()
    }

    @Test fun manualSaveLoadRestoresTurnAndRotationKeepsControlsVisible() {
        start()
        compose.onNodeWithTag("battle-menu").performClick()
        compose.onNodeWithText("Save battle").performClick()
        val store = BattleStore(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.waitUntil(5000) { store.load("manual") != null }
        compose.onNodeWithTag("end-turn").performClick()
        compose.onNodeWithText("End turn now").performClick()
        compose.waitUntil(5000) { store.load()?.currentSide == 2 }
        compose.onNodeWithTag("battle-menu").performClick()
        compose.onNodeWithText("Load saved battle").performClick()
        compose.waitUntil(5000) { store.load()?.currentSide == 1 }
        compose.onNodeWithTag("end-turn").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitForIdle()
        compose.onNodeWithTag("end-turn").assertIsDisplayed()
        compose.onNodeWithTag("recruit").assertIsDisplayed()
        screenshot("04-landscape-battle")
    }

    @Test fun computerPlaysAndReturnsControlToPlayer() {
        start(ai = true)
        compose.onNodeWithTag("end-turn").performClick()
        compose.onNodeWithText("End turn now").performClick()
        val store = BattleStore(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.waitUntil(30_000) { store.load()?.let { it.turn == 2 && it.currentSide == 1 || it.winner != null } == true }
        assertTrue(store.load()!!.units.none { it.hp <= 0 })
        screenshot("05-after-computer-turn")
    }
}
