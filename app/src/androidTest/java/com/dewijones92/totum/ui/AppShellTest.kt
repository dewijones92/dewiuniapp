package com.dewijones92.totum.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dewijones92.totum.di.fake.FakeAppContainer
import com.dewijones92.totum.theme.TotumTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppShellTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        composeTestRule.setContent {
            TotumTheme { AppShell(FakeAppContainer()) }
        }
    }

    @Test
    fun videosPillar_isShownByDefault() {
        composeTestRule.onNodeWithText("No videos yet").assertIsDisplayed()
    }

    @Test
    fun tappingPodcasts_showsPodcastsPillar() {
        composeTestRule.onNodeWithText("Podcasts").performClick()
        composeTestRule.onNodeWithText("No podcasts yet").assertIsDisplayed()
    }

    @Test
    fun tappingLibrary_showsLibraryPillar() {
        composeTestRule.onNodeWithText("Library").performClick()
        composeTestRule.onNodeWithText("Your library is empty").assertIsDisplayed()
    }
}
