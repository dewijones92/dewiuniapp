package com.dewijones92.uniapp.ui.podcasts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.common.EmptyState

@Composable
fun PodcastsScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Podcasts,
        headline = stringResource(R.string.podcasts_empty_headline),
        supportingText = stringResource(R.string.podcasts_empty_supporting),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun PodcastsScreenPreview() {
    UniAppTheme { PodcastsScreen() }
}
