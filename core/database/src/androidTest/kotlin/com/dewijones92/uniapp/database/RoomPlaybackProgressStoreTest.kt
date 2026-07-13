package com.dewijones92.uniapp.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dewijones92.uniapp.domain.MediaItemId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RoomPlaybackProgressStoreTest {

    private lateinit var database: UniAppDatabase
    private lateinit var store: RoomPlaybackProgressStore
    private val id = MediaItemId("vid-1")

    @Before
    fun createStore() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UniAppDatabase::class.java,
        ).build()
        store = RoomPlaybackProgressStore(database.playbackProgressDao()) { 0L }
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun savesAndResumesAPosition() = runTest {
        store.save(id, positionMs = 42_000, durationMs = 600_000)
        assertEquals(42_000L, store.resumePositionMs(id))
    }

    @Test
    fun ignoresTrivialPositionsSoAQuickTapLeavesNoResumePoint() = runTest {
        store.save(id, positionMs = 1_000, durationMs = 600_000)
        assertNull(store.resumePositionMs(id))
    }

    @Test
    fun nearTheEndCountsAsFinishedAndClearsAnyResumePoint() = runTest {
        store.save(id, positionMs = 100_000, durationMs = 600_000)
        assertEquals(100_000L, store.resumePositionMs(id))

        // Watched to within the last few seconds: it should restart next time.
        store.save(id, positionMs = 599_000, durationMs = 600_000)
        assertNull(store.resumePositionMs(id))
    }

    @Test
    fun unknownItemResumesFromTheStart() = runTest {
        assertNull(store.resumePositionMs(MediaItemId("never-played")))
    }
}
