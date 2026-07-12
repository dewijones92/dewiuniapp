package com.dewijones92.uniapp.ui.videos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.ui.videos.VideosViewModel.CheckState

/**
 * URL-entry dialog that runs the yt-dlp engine against a link and shows
 * what it found — the first visible proof of the embedded extractor.
 */
@Composable
internal fun CheckVideoDialog(
    checkState: CheckState,
    onCheck: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.check_video)) },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.video_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when (checkState) {
                    is CheckState.Found -> FoundSummary(checkState)
                    is CheckState.Error -> Text(
                        text = stringResource(checkState.messageRes()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    CheckState.InProgress -> CircularProgressIndicator(
                        modifier = Modifier.padding(top = 8.dp).size(20.dp),
                    )
                    CheckState.Idle -> Unit
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCheck(url) },
                enabled = checkState != CheckState.InProgress,
            ) { Text(stringResource(R.string.check)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun FoundSummary(state: CheckState.Found, modifier: Modifier = Modifier) {
    val metadata = state.metadata
    Column(modifier = modifier.padding(top = 12.dp)) {
        Text(metadata.title, style = MaterialTheme.typography.titleSmall)
        val details = listOfNotNull(
            metadata.uploader,
            metadata.durationSeconds?.let { stringResource(R.string.duration_minutes, it / SECONDS_PER_MINUTE) },
            pluralStringResource(R.plurals.formats_available, metadata.formats.size, metadata.formats.size),
        )
        Text(
            text = details.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun CheckState.Error.messageRes(): Int = when (this) {
    CheckState.Error.InvalidUrl -> R.string.error_invalid_url
    CheckState.Error.Unsupported -> R.string.error_unsupported_url
    CheckState.Error.Network -> R.string.error_network
    CheckState.Error.Extraction -> R.string.error_extraction
}

private const val SECONDS_PER_MINUTE = 60L
