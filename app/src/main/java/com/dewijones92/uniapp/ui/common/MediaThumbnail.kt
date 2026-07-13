package com.dewijones92.uniapp.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dewijones92.uniapp.common.HttpUrl

/**
 * The one thumbnail/artwork image in the app — used identically for podcast
 * episodes, videos, channels and search hits. Give it whatever [url] the item
 * carries ([com.dewijones92.uniapp.domain.MediaItem.thumbnailUrl],
 * `SearchHit.artworkUrl`, `PlaybackState.artworkUrl`); a missing or
 * still-loading image shows a neutral placeholder glyph, so a null [url] is a
 * perfectly valid, tidy state rather than a blank hole.
 *
 * The image crops to fill its box ([ContentScale.Crop]) so mixed aspect ratios
 * (square podcast art, 16:9 video stills) sit uniformly in a list.
 */
@Composable
fun MediaThumbnail(
    url: HttpUrl?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // Sits behind the image, so it shows through while loading and stays
        // visible if the url is null or the fetch fails.
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxSize(PLACEHOLDER_GLYPH_FRACTION),
        )
        if (url != null) {
            AsyncImage(
                model = url.value,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val PLACEHOLDER_GLYPH_FRACTION = 0.4f
