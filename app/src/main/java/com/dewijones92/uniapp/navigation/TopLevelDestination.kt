package com.dewijones92.uniapp.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.ui.graphics.vector.ImageVector
import com.dewijones92.uniapp.R

/**
 * The app's top-level pillars, shown in the bottom navigation bar.
 */
enum class TopLevelDestination(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Videos(
        labelRes = R.string.destination_videos,
        selectedIcon = Icons.Filled.SmartDisplay,
        unselectedIcon = Icons.Outlined.SmartDisplay,
    ),
    Podcasts(
        labelRes = R.string.destination_podcasts,
        selectedIcon = Icons.Filled.Podcasts,
        unselectedIcon = Icons.Outlined.Podcasts,
    ),
    Library(
        labelRes = R.string.destination_library,
        selectedIcon = Icons.Filled.CollectionsBookmark,
        unselectedIcon = Icons.Outlined.CollectionsBookmark,
    ),
}
