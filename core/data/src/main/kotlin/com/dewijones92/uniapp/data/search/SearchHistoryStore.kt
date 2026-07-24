package com.dewijones92.uniapp.data.search

import kotlinx.coroutines.flow.Flow

/**
 * Remembers recent search queries so they can be offered again. Most-recent
 * first, de-duplicated (re-running a query moves it to the front), and bounded —
 * one store for the single unified search, both pillars.
 */
public interface SearchHistoryStore {

    /** Recent queries, most-recent first. */
    public fun recent(): Flow<List<String>>

    /** Records [query] as the most recent (moving it to the front if already present). */
    public suspend fun record(query: String)

    /** Forgets a single query. */
    public suspend fun remove(query: String)

    /** Clears the whole history. */
    public suspend fun clear()
}
