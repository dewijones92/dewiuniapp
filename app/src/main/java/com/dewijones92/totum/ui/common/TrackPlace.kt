package com.dewijones92.totum.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.dewijones92.totum.common.Diag

/**
 * Records where a screen was when you left it, and where it came back to.
 *
 * "It doesn't stay in the same place when I press the bottom buttons" was not answerable
 * from a diagnostics report, twice. The first time nothing recorded that a tab had been
 * switched at all; once that was fixed, the switches were visible but what the screen was
 * *showing* still was not — so the report could prove the user did the thing, and say
 * nothing about whether state survived it.
 *
 * Two lines per visit, and the pair is the whole point: if `entered` does not match the
 * previous `left`, state was lost, and the description names which part of it. Guessing
 * from the code got the mechanism wrong twice, because the plausible culprits
 * (a non-saveable scroll state, a flow dropping its value) were both innocent.
 *
 * [place] is read at dispose time, not capture time, so what it reports is where the screen
 * actually ended up rather than where it was when the effect started.
 */
@Composable
fun TrackPlace(screen: String, place: () -> String) {
    val current by rememberUpdatedState(place)
    DisposableEffect(screen) {
        Diag.log("place", "$screen entered at ${current()}")
        onDispose { Diag.log("place", "$screen left at ${current()}") }
    }
}
