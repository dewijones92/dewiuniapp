package com.dewijones92.uniapp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.dewijones92.uniapp.data.podcast.PodcastRepository
import com.dewijones92.uniapp.data.podcast.fake.FakePodcastRepository
import com.dewijones92.uniapp.navigation.TopLevelDestination
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.library.LibraryScreen
import com.dewijones92.uniapp.ui.podcasts.PodcastsScreen
import com.dewijones92.uniapp.ui.videos.VideosScreen

/**
 * Top-level scaffold: bottom navigation across the app's pillars with
 * animated transitions between them.
 */
@Composable
fun AppShell(podcastRepository: PodcastRepository, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf(TopLevelDestination.Videos) }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val isSelected = destination == selected
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selected = destination },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.padding(innerPadding),
            label = "top-level-destination",
        ) { destination ->
            when (destination) {
                TopLevelDestination.Videos -> VideosScreen()
                TopLevelDestination.Podcasts -> PodcastsScreen(podcastRepository)
                TopLevelDestination.Library -> LibraryScreen()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppShellPreview() {
    UniAppTheme { AppShell(FakePodcastRepository()) }
}
