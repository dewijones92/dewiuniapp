package com.dewijones92.uniapp.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dewijones92.uniapp.domain.DownloadState
import com.dewijones92.uniapp.domain.MediaItemId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RoomDownloadStoreTest {

    private lateinit var database: UniAppDatabase
    private lateinit var store: RoomDownloadStore
    private val id = MediaItemId("ep-1")

    @Before
    fun createStore() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            UniAppDatabase::class.java,
        ).build()
        store = RoomDownloadStore(database.downloadDao())
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun statesRoundTripThroughEachStage() = runTest {
        store.put(id, DownloadState.Downloading(500, 1000))
        assertEquals(DownloadState.Downloading(500, 1000), store.get(id))

        store.put(id, DownloadState.Downloaded("/data/ep1.media"))
        assertEquals(DownloadState.Downloaded("/data/ep1.media"), store.get(id))
        assertEquals(mapOf(id to DownloadState.Downloaded("/data/ep1.media")), store.observeAll().first())

        store.remove(id)
        assertEquals(DownloadState.NotDownloaded, store.get(id))
    }

    @Test
    fun failureIsPersisted() = runTest {
        store.put(id, DownloadState.Failed("HTTP 500"))
        assertEquals(DownloadState.Failed("HTTP 500"), store.get(id))
    }
}
