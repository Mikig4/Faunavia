package it.faunavia.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigatesThroughPrimaryScreensAndOptionalTripTools() {
        composeRule.onNodeWithTag("screen-trips").assertIsDisplayed()
        composeRule.onNodeWithTag("nav-results").assertDoesNotExist()
        composeRule.onNodeWithTag("nav-routes").assertDoesNotExist()
        var navigationSafeBottom = 0
        composeRule.runOnIdle {
            val root = composeRule.activity.window.decorView
            val insets = requireNotNull(ViewCompat.getRootWindowInsets(root))
            navigationSafeBottom = root.height - insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        }

        PrimaryDestinations.forEachIndexed { index, destination ->
            composeRule.onNodeWithTag("destination-bar").performScrollToIndex(index)
            val button = composeRule.onNodeWithTag("nav-${destination.route}").fetchSemanticsNode()
            assertTrue("Navigation must stay above the Android system bar", button.boundsInWindow.bottom <= navigationSafeBottom + 1)
            composeRule.onNodeWithTag("nav-${destination.route}").performClick()
            composeRule.onNodeWithTag("screen-${destination.route}").assertIsDisplayed()
            composeRule.onNodeWithTag("screen-title-${destination.route}").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("nav-trips").performClick()
        composeRule.onNodeWithTag("trip-content").performScrollToNode(hasTestTag("trip-explore"))
        composeRule.onNodeWithTag("trip-explore").performClick()
        composeRule.onNodeWithTag("screen-results").assertIsDisplayed()
        composeRule.onNodeWithTag("back-to-trips").performClick()
        composeRule.onNodeWithTag("trip-content").performScrollToNode(hasTestTag("trip-import-route"))
        composeRule.onNodeWithTag("trip-import-route").performClick()
        composeRule.onNodeWithTag("screen-routes").assertIsDisplayed()
        composeRule.onNodeWithTag("back-to-trips").performClick()
        composeRule.onNodeWithTag("screen-trips").assertIsDisplayed()
    }
}
