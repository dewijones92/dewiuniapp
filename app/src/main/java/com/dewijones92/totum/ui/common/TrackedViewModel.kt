package com.dewijones92.totum.ui.common

import androidx.lifecycle.ViewModel
import com.dewijones92.totum.common.Diag

/**
 * A [ViewModel] that says when it is built and when it is thrown away.
 *
 * This exists to settle one question that nothing else could answer: whether switching
 * bottom tabs **recreates** a screen's view model. If it does, every piece of state the
 * screen holds resets and no amount of saveable-state plumbing above it will help — so it
 * has to be ruled in or out first, before any subtler theory about scroll restoration is
 * worth entertaining. A "created" line on every tab switch is the whole diagnosis; one
 * "created" per app launch clears it entirely.
 *
 * `viewModel()` resolves against the nearest `ViewModelStoreOwner`, which is normally the
 * activity — so these are expected to be built once and survive. Expected is not the same
 * as verified, which is the point.
 */
abstract class TrackedViewModel(private val name: String) : ViewModel() {

    init {
        Diag.log("vm", "$name created")
    }

    override fun onCleared() {
        Diag.log("vm", "$name cleared")
    }
}
