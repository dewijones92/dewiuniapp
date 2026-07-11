package com.dewijones92.uniapp.ui.videos

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.common.EmptyState

@Composable
fun VideosScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.SmartDisplay,
        headline = stringResource(R.string.videos_empty_headline),
        supportingText = stringResource(R.string.videos_empty_supporting),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun VideosScreenPreview() {
    UniAppTheme { VideosScreen() }
}
