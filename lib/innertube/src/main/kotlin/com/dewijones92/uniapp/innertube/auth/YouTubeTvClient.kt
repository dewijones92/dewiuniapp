package com.dewijones92.uniapp.innertube.auth

/** OAuth client identity used for the device-code flow. */
public data class OAuthClientCredentials(
    val clientId: String,
    val clientSecret: String,
)

/**
 * The public OAuth identity of YouTube on TV, and Google's device-flow
 * endpoints.
 *
 * These are not secrets: every TV/console ships them, and third-party
 * living-room clients (SmartTube et al.) authenticate as this client because
 * the device-code flow is the one login Google expects from a device without
 * a browser. Both values were verified live against Google's endpoints
 * (2026-07-13): the device endpoint issues codes and the token endpoint
 * answers `authorization_pending` (not `invalid_client`) for this pair.
 */
public object YouTubeTvClient {
    public val CREDENTIALS: OAuthClientCredentials = OAuthClientCredentials(
        clientId = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com",
        clientSecret = "SboVhoG9s0rNafixCSGGKXAT",
    )

    public const val DEVICE_CODE_URL: String = "https://oauth2.googleapis.com/device/code"
    public const val TOKEN_URL: String = "https://oauth2.googleapis.com/token"
    public const val SCOPE: String = "https://www.googleapis.com/auth/youtube"
}
