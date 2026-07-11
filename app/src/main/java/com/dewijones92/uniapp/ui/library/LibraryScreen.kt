package com.dewijones92.uniapp.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.common.EmptyState

@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.CollectionsBookmark,
        headline = stringResource(R.string.library_empty_headline),
        supportingText = stringResource(R.string.library_empty_supporting),
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    UniAppTheme { LibraryScreen() }
}
