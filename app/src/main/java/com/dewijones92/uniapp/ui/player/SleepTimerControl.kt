package com.dewijones92.uniapp.ui.player

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dewijones92.uniapp.R
import com.dewijones92.uniapp.playback.SleepTimerState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Sleep-timer control on the player: while off, a moon button opens a menu of
 * durations; while running, it shows the time left and tapping cancels it.
 * Drives the one [com.dewijones92.uniapp.playback.SleepTimer].
 */
@Composable
internal fun SleepTimerControl(
    state: SleepTimerState,
    onStart: (Duration) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TextButton(
        onClick = { if (state is SleepTimerState.Running) onCancel() else menuOpen = true },
        modifier = modifier,
    ) {
        Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(20.dp))
        val label = when (state) {
            SleepTimerState.Off -> stringResource(R.string.sleep_timer)
            is SleepTimerState.Running -> formatTime(state.remaining.inWholeMilliseconds)
        }
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        SLEEP_OPTIONS.forEach { minutes ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.duration_minutes, minutes)) },
                onClick = {
                    onStart(minutes.minutes)
                    menuOpen = false
                },
            )
        }
    }
}

private val SLEEP_OPTIONS = listOf(5, 10, 15, 30, 45, 60)
