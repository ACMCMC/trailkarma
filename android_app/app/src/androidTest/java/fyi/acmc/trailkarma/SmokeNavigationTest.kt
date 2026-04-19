package fyi.acmc.trailkarma

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeNavigationTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun commonScreensOpenWithoutCrashing() {
        waitForText("Menu")

        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Rewards").performClick()
        waitForText("Rewards")
        waitForText("KARMA, collectibles, and relay wins")
        goBack()

        waitForText("Menu")
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Profile").performClick()
        waitForText("Profile")
        waitForText("Trail identity")
        goBack()

        waitForText("Menu")
        composeRule.onNodeWithContentDescription("Menu").performClick()
        composeRule.onNodeWithText("Relay Hub").performClick()
        waitForText("Relay Hub")
        waitForText("Voice relay missions")
    }

    private fun waitForText(text: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun goBack() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }
}
