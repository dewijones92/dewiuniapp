package com.dewijones92.uniapp.innertube.auth

import com.dewijones92.uniapp.common.HttpUrl

/** Opaque handle identifying one in-flight device sign-in. Redacted in logs. */
@JvmInline
public value class DeviceCode(public val value: String) {
    override fun toString(): String = "DeviceCode(redacted)"
}

/**
 * One pending device sign-in: the user visits [verificationUrl] and types
 * [userCode]; the app polls with [deviceCode] every [pollIntervalSeconds]
 * until Google reports a result or [expiresInSeconds] passes.
 */
public data class DeviceAuthorization(
    val deviceCode: DeviceCode,
    val userCode: String,
    val verificationUrl: HttpUrl,
    val expiresInSeconds: Int,
    val pollIntervalSeconds: Int,
)
