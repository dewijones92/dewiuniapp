package com.dewijones92.uniapp.domain

import java.time.Instant

/** A user's subscription to a [MediaSource]. */
public data class Subscription(
    val source: MediaSource,
    val subscribedAt: Instant,
)
