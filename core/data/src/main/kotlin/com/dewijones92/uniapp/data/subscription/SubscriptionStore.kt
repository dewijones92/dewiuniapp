package com.dewijones92.uniapp.data.subscription

import com.dewijones92.uniapp.domain.MediaItem
import com.dewijones92.uniapp.domain.MediaSource
import com.dewijones92.uniapp.domain.SourceId
import com.dewijones92.uniapp.domain.Subscription
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Persistence port for subscriptions of one pillar (podcast feeds or video
 * channels). One implementation ([com.dewijones92.uniapp.database.RoomSubscriptionStore])
 * backs both, parameterised by source type — the seam is identical, only the
 * stored `MediaSource` variant differs.
 */
public interface SubscriptionStore {
    public fun observeSubscriptions(): Flow<List<Subscription>>
    public fun observeItems(): Flow<List<MediaItem>>
    public suspend fun contains(id: SourceId): Boolean

    /** Saves (or updates) a manually-added source and its items. */
    public suspend fun saveSource(subscription: Subscription, items: List<MediaItem>)

    public suspend fun removeSource(id: SourceId)

    /**
     * Makes the stored *imported* sources match [sources] — the authoritative
     * list from an external account (e.g. YouTube subscriptions). New ones are
     * added (marked as imported), and previously-imported ones that have left
     * the list are pruned. Manually-added sources are never touched, so a sync
     * can't delete something the user added by URL. Returns how many were added
     * and pruned.
     */
    public suspend fun reconcileImported(sources: List<MediaSource>, subscribedAt: Instant): ReconcileResult
}

/** How many sources a reconcile added and pruned. */
public data class ReconcileResult(val added: Int, val removed: Int)
