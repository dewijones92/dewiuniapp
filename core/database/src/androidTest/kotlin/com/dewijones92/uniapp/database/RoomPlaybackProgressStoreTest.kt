package com.dewijones92.uniapp.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dewijones92.uniapp.domain.MediaItemId
import com.dewijones92.uniapp.domain.PlayState
import kotlinx.coroutines.flow.first
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

    /** The whole point of keeping the row: played and never-started must differ. */
    @Test
    fun finishingMarksPlayedRatherThanForgetting() = runTest {
        store.save(id, positionMs = 599_000, durationMs = 600_000)

        assertEquals(PlayState.Played, store.observeStates().first()[id])
        assertNull(store.observeStates().first()[MediaItemId("never-played")])
    }

    @Test
    fun apartWayItemReportsItsProgress() = runTest {
        store.save(id, positionMs = 150_000, durationMs = 600_000)

        assertEquals(PlayState.InProgress(150_000, 600_000), store.observeStates().first()[id])
        assertEquals(0.25f, (store.observeStates().first()[id] as PlayState.InProgress).fraction)
    }

    @Test
    fun markingPlayedByHandNeedsNoPriorPlayback() = runTest {
        store.setPlayed(id, played = true)

        assertEquals(PlayState.Played, store.observeStates().first()[id])
        assertNull(store.resumePositionMs(id))
    }

    @Test
    fun markingUnplayedClearsTheStateEntirely() = runTest {
        store.save(id, positionMs = 150_000, durationMs = 600_000)

        store.setPlayed(id, played = false)

        assertNull(store.observeStates().first()[id])
        assertNull(store.resumePositionMs(id))
    }

    /**
     * Replaying a finished item ticks through small positions first. Those must not
     * clear the played mark, or every replay would silently mark the item unplayed.
     */
    @Test
    fun replayingAPlayedItemKeepsItPlayedUntilRealProgress() = runTest {
        store.setPlayed(id, played = true)

        store.save(id, positionMs = 1_000, durationMs = 600_000)

        assertEquals(PlayState.Played, store.observeStates().first()[id])
    }
}
