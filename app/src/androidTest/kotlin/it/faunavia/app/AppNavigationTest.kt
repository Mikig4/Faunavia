package it.faunavia.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun navigatesThroughEveryPlaceholderScreen() {
        composeRule.onNodeWithTag("screen-home").assertIsDisplayed()

        AppDestination.entries.drop(1).forEachIndexed { index, destination ->
            composeRule.onNodeWithTag("destination-bar").performScrollToIndex(index + 1)
            composeRule.onNodeWithTag("nav-${destination.route}").performClick()
            composeRule.onNodeWithTag("screen-${destination.route}").assertIsDisplayed()
            composeRule.onNodeWithTag("screen-title-${destination.route}").assertIsDisplayed()
        }
    }
}
