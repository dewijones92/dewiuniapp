package com.dewijones92.uniapp.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R

/**
 * The header of a source's page — back, title, subscribe toggle. One component
 * for both pillars: a video channel and a podcast feed are the same thing here
 * (a [com.dewijones92.uniapp.domain.MediaSource] you can follow), so their pages
 * share this rather than each growing their own.
 */
@Composable
fun SourceHeader(
    title: String,
    subscribed: Boolean,
    onBack: () -> Unit,
    onToggleSubscribed: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        if (subscribed) {
            OutlinedButton(onClick = onToggleSubscribed) {
                Text(stringResource(R.string.channel_unsubscribe))
            }
        } else {
            FilledTonalButton(onClick = onToggleSubscribed) {
                Text(stringResource(R.string.channel_subscribe))
            }
        }
    }
}
