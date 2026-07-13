package com.dewijones92.uniapp.ui.account

import com.dewijones92.uniapp.data.channel.SubscriptionImporter
import com.dewijones92.uniapp.data.channel.fake.FakeChannelRepository
import com.dewijones92.uniapp.innertube.auth.AccessToken
import com.dewijones92.uniapp.innertube.auth.OAuthTokens
import com.dewijones92.uniapp.innertube.auth.RefreshToken
import com.dewijones92.uniapp.innertube.auth.TokenPollResult
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.auth.fake.FakeYouTubeAuth
import com.dewijones92.uniapp.innertube.auth.fake.InMemoryTokenStore
import com.dewijones92.uniapp.innertube.subscriptions.fake.FakeYouTubeSubscriptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val auth = FakeYouTubeAuth()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(signedIn: Boolean = false): AccountViewModel {
        val initial = if (signedIn) TOKENS else null
        val account = YouTubeAccount(auth, InMemoryTokenStore(initial), nowEpochSeconds = { 0 })
        val importer = SubscriptionImporter(FakeYouTubeSubscriptions(), FakeChannelRepository())
        return AccountViewModel(account, importer)
    }

    @Test
    fun `starts signed out when the store is empty`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(AccountViewModel.UiState.SignedOut, vm.state.value)
    }

    @Test
    fun `restores a signed-in session on construction`() = runTest {
        val vm = viewModel(signedIn = true)
        advanceUntilIdle()
        assertEquals(AccountViewModel.UiState.SignedIn, vm.state.value)
    }

    @Test
    fun `sign-in surfaces the code then lands signed in`() = runTest {
        auth.pollResults.add(TokenPollResult.Authorized(TOKENS))
        val vm = viewModel()
        advanceUntilIdle()

        vm.signIn()
        advanceUntilIdle()

        assertEquals(AccountViewModel.UiState.SignedIn, vm.state.value)
    }

    @Test
    fun `a denied sign-in ends in a failed state`() = runTest {
        auth.pollResults.add(TokenPollResult.Denied)
        val vm = viewModel()
        advanceUntilIdle()

        vm.signIn()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state is AccountViewModel.UiState.Failed)
        assertEquals(AccountViewModel.FailureReason.DENIED, (state as AccountViewModel.UiState.Failed).reason)
    }

    @Test
    fun `sign-out returns to signed out`() = runTest {
        val vm = viewModel(signedIn = true)
        advanceUntilIdle()

        vm.signOut()
        advanceUntilIdle()

        assertEquals(AccountViewModel.UiState.SignedOut, vm.state.value)
    }

    private companion object {
        val TOKENS = OAuthTokens(AccessToken("at"), RefreshToken("rt"), expiresAtEpochSeconds = 3_600)
    }
}
