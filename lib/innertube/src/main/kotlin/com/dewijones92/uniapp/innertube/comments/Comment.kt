package com.dewijones92.uniapp.innertube.comments

import com.dewijones92.uniapp.common.HttpUrl

/** A top-level comment on a video. */
public data class Comment(
    val id: String,
    val author: String,
    val authorAvatarUrl: HttpUrl?,
    val text: String,
    /** Display string as YouTube renders it, e.g. "2 days ago". */
    val publishedTime: String?,
    /** Display string, e.g. "1.2K"; null when hidden. */
    val likeCount: String?,
    val replyCount: Int,
    val isCreator: Boolean,
    val isVerified: Boolean,
)
