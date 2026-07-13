package com.dewijones92.uniapp.innertube.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Drives one complete device sign-in as a cold flow: request a code, surface
 * it to the user, poll until Google reports a result. All protocol pacing
 * (poll interval, slow-down backoff, expiry) lives here — any UI just
 * collects events. Transient poll failures are retried until the code
 * expires, so a network blip mid-login doesn't kill the attempt.
 */
public class DeviceLogin(private val auth: YouTubeAuth) {

    public fun start(): Flow<DeviceLoginEvent> = flow {
        val authorization = when (val started = auth.requestDeviceAuthorization()) {
            is DeviceAuthorizationResult.Started -> started.authorization
            is DeviceAuthorizationResult.Failure -> {
                emit(DeviceLoginEvent.Failed(LoginFailure.Network(started.detail)))
                return@flow
            }
        }
        emit(DeviceLoginEvent.AwaitingUser(authorization.userCode, authorization.verificationUrl))
        pollUntilSettled(authorization)
    }

    private suspend fun FlowCollector<DeviceLoginEvent>.pollUntilSettled(authorization: DeviceAuthorization) {
        var intervalSeconds = authorization.pollIntervalSeconds
        var elapsedSeconds = 0
        while (elapsedSeconds < authorization.expiresInSeconds) {
            delay(intervalSeconds * MILLIS_PER_SECOND)
            elapsedSeconds += intervalSeconds
            when (val poll = auth.pollForTokens(authorization.deviceCode)) {
                is TokenPollResult.Authorized -> {
                    emit(DeviceLoginEvent.Succeeded(poll.tokens))
                    return
                }
                TokenPollResult.Pending -> Unit
                TokenPollResult.SlowDown -> intervalSeconds += SLOW_DOWN_EXTRA_SECONDS
                TokenPollResult.Denied -> {
                    emit(DeviceLoginEvent.Failed(LoginFailure.Denied))
                    return
                }
                TokenPollResult.Expired -> {
                    emit(DeviceLoginEvent.Failed(LoginFailure.Expired))
                    return
                }
                is TokenPollResult.Failure -> Unit // transient; bounded by expiry
            }
        }
        emit(DeviceLoginEvent.Failed(LoginFailure.Expired))
    }

    private companion object {
        const val SLOW_DOWN_EXTRA_SECONDS = 5
    }
}

public sealed interface DeviceLoginEvent {
    /** Show these to the user: visit the URL, type the code. */
    public data class AwaitingUser(
        val userCode: String,
        val verificationUrl: com.dewijones92.uniapp.common.HttpUrl,
    ) : DeviceLoginEvent

    public data class Succeeded(val tokens: OAuthTokens) : DeviceLoginEvent

    public data class Failed(val reason: LoginFailure) : DeviceLoginEvent
}

public sealed interface LoginFailure {
    public data object Denied : LoginFailure
    public data object Expired : LoginFailure
    public data class Network(val detail: String) : LoginFailure
}

internal const val MILLIS_PER_SECOND: Long = 1000L
