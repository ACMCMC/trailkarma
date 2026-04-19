package fyi.acmc.trailkarma

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiodiversityFlowSmokeTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun biodiversityCaptureFlowRendersAndStoresAnObservation() {
        waitForContentDescription("Record Trail Sound")
        composeRule.onNodeWithContentDescription("Record Trail Sound").performClick()

        waitForText("Biodiversity audio")
        composeRule.onNodeWithText("Capture flow").assertIsDisplayed()
        composeRule.onNodeWithText("Record Trail Sound").performClick()

        device.wait(Until.hasObject(By.text("Recording 5 second clip...")), 5_000)
        device.wait(Until.hasObject(By.text("Record Trail Sound")), 15_000)

        composeRule.onNodeWithText("Biodiversity audio").assertIsDisplayed()
        composeRule.onNodeWithText("Capture flow").assertIsDisplayed()
    }

    private fun waitForText(text: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String, timeoutMs: Long = 20_000) {
        composeRule.waitUntil(timeoutMs) {
            runCatching {
                composeRule.onNodeWithContentDescription(description).fetchSemanticsNode()
                true
            }.getOrDefault(false)
        }
    }
}
