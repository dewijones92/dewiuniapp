package com.dewijones92.totum.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dewijones92.totum.domain.SourceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Groups of sources, against the real database rather than a fake.
 *
 * The cascade matters and SQLite does not enforce `ON DELETE CASCADE` unless asked, so a
 * deleted group leaving its memberships behind is exactly the kind of thing a fake store
 * would happily pretend was fine.
 */
class RoomSourceGroupStoreTest {

    private lateinit var database: TotumDatabase
    private lateinit var store: RoomSourceGroupStore

    private val channel = SourceId("https://www.youtube.com/channel/UCaaa")
    private val podcast = SourceId("https://example.com/feed.xml")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TotumDatabase::class.java,
        ).build()
        store = RoomSourceGroupStore(database.sourceGroupDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a new group starts empty and is observed`() = runTest {
        store.create("Politics")

        val groups = store.observeGroups().first()

        assertEquals(1, groups.size)
        assertEquals("Politics", groups.single().name)
        assertEquals(emptyList<SourceId>(), groups.single().sourceIds)
    }

    @Test
    fun `toggling adds then removes and says which it did`() = runTest {
        val id = store.create("Politics")

        assertTrue("first toggle should add", store.toggleMember(id, channel))
        assertEquals(listOf(channel), store.observeGroups().first().single().sourceIds)

        assertFalse("second toggle should remove", store.toggleMember(id, channel))
        assertEquals(emptyList<SourceId>(), store.observeGroups().first().single().sourceIds)
    }

    @Test
    fun `a group holds both pillars because it is a group of sources`() = runTest {
        val id = store.create("Mixed")
        store.toggleMember(id, channel)
        store.toggleMember(id, podcast)

        assertEquals(listOf(channel, podcast), store.observeGroups().first().single().sourceIds)
    }

    @Test
    fun `memberships of two groups do not leak into each other`() = runTest {
        val politics = store.create("Politics")
        val tech = store.create("Tech")
        store.toggleMember(politics, channel)

        val groups = store.observeGroups().first().associateBy { it.name }

        assertEquals(listOf(channel), groups.getValue("Politics").sourceIds)
        assertEquals(emptyList<SourceId>(), groups.getValue("Tech").sourceIds)
        assertEquals(tech, groups.getValue("Tech").id)
    }

    @Test
    fun `deleting a group takes its memberships with it`() = runTest {
        val id = store.create("Politics")
        store.toggleMember(id, channel)

        store.delete(id)

        assertEquals(emptyList<Any>(), store.observeGroups().first())
        // Directly, because an orphaned membership is invisible through the store: it would
        // only surface later, attached to a new group that happened to reuse the id.
        assertEquals(emptyList<SourceGroupMemberEntity>(), database.sourceGroupDao().observeMembers().first())
    }

    @Test
    fun `renaming keeps the membership`() = runTest {
        val id = store.create("Politics")
        store.toggleMember(id, channel)

        store.rename(id, "UK politics")

        val group = store.observeGroups().first().single()
        assertEquals("UK politics", group.name)
        assertEquals(listOf(channel), group.sourceIds)
    }
}
