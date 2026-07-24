package com.dewijones92.uniapp.data.search

import com.dewijones92.uniapp.data.search.fake.InMemorySearchHistoryStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHistoryStoreTest {

    private suspend fun InMemorySearchHistoryStore.list() = recent().first()

    @Test
    fun `records most-recent first`() = runTest {
        val store = InMemorySearchHistoryStore()
        store.record("kotlin")
        store.record("compose")

        assertEquals(listOf("compose", "kotlin"), store.list())
    }

    @Test
    fun `re-recording moves a query to the front and de-duplicates case-insensitively`() = runTest {
        val store = InMemorySearchHistoryStore()
        store.record("kotlin")
        store.record("compose")
        store.record("Kotlin")

        assertEquals(listOf("Kotlin", "compose"), store.list())
    }

    @Test
    fun `blank queries are ignored`() = runTest {
        val store = InMemorySearchHistoryStore()
        store.record("   ")
        store.record("")

        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `history is capped at the limit, dropping the oldest`() = runTest {
        val store = InMemorySearchHistoryStore(limit = 3)
        listOf("a", "b", "c", "d").forEach { store.record(it) }

        assertEquals(listOf("d", "c", "b"), store.list())
    }

    @Test
    fun `remove and clear`() = runTest {
        val store = InMemorySearchHistoryStore()
        listOf("a", "b", "c").forEach { store.record(it) }
        store.remove("B".lowercase())
        assertEquals(listOf("c", "a"), store.list())

        store.clear()
        assertTrue(store.list().isEmpty())
    }
}
