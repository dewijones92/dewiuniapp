package com.dewijones92.totum.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceGroupTest {

    private val a = SourceId("a")
    private val b = SourceId("b")
    private val group = SourceGroup(SourceGroupId("g"), "Politics")

    @Test
    fun `adding the same source twice is one membership`() {
        val twice = group.with(a).with(a)

        assertEquals(listOf(a), twice.sourceIds)
    }

    @Test
    fun `membership keeps the order it was built in`() {
        assertEquals(listOf(a, b), group.with(a).with(b).members)
    }

    @Test
    fun `removing a source that was never there changes nothing`() {
        assertEquals(group, group.without(a))
    }

    @Test
    fun `contains answers what the toggle needs to know`() {
        assertTrue(a in group.with(a))
        assertFalse(b in group.with(a))
    }

    @Test
    fun `a group must be named`() {
        assertThrows(IllegalArgumentException::class.java) { SourceGroup(SourceGroupId("g"), " ") }
    }
}
