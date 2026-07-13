package com.dewijones92.uniapp.di

import com.dewijones92.uniapp.innertube.actions.YouTubeActions
import com.dewijones92.uniapp.innertube.auth.YouTubeAccount
import com.dewijones92.uniapp.innertube.feeds.YouTubeFeeds

/**
 * The signed-in YouTube services grouped as one collaborator, so screens that
 * use several of them (feeds, actions, account state) take a single dependency
 * rather than a long parameter list.
 */
class YouTubeAccountServices(
    val account: YouTubeAccount,
    val feeds: YouTubeFeeds,
    val actions: YouTubeActions,
)
