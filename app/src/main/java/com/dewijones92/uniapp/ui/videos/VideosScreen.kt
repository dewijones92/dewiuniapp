package com.dewijones92.uniapp.ui.videos

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.theme.UniAppTheme
import com.dewijones92.uniapp.ui.common.EmptyState
import com.dewijones92.uniapp.ytdlp.YtDlpEngine
import com.dewijones92.uniapp.ytdlp.fake.FakeYtDlpEngine

@Composable
fun VideosScreen(engine: YtDlpEngine, modifier: Modifier = Modifier) {
    val viewModel: VideosViewModel = viewModel(factory = VideosViewModel.factory(engine))
    val checkState by viewModel.checkState.collectAsStateWithLifecycle()

    VideosContent(
        checkState = checkState,
        onCheck = viewModel::check,
        onDialogClosed = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
internal fun VideosContent(
    checkState: VideosViewModel.CheckState,
    onCheck: (String) -> Unit,
    onDialogClosed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCheckDialog by rememberSaveable { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        EmptyState(
            icon = Icons.Outlined.SmartDisplay,
            headline = stringResource(R.string.videos_empty_headline),
            supportingText = stringResource(R.string.videos_empty_supporting),
        )

        FloatingActionButton(
            onClick = { showCheckDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.check_video))
        }

        if (showCheckDialog) {
            CheckVideoDialog(
                checkState = checkState,
                onCheck = onCheck,
                onDismiss = {
                    showCheckDialog = false
                    onDialogClosed()
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun VideosContentPreview() {
    UniAppTheme {
        VideosContent(
            checkState = VideosViewModel.CheckState.Found(FakeYtDlpEngine.sampleMetadata()),
            onCheck = {},
            onDialogClosed = {},
        )
    }
}
