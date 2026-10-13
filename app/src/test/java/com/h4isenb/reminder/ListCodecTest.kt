package com.h4isenb.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListCodecTest {
    @Test
    fun itemsRoundTrip() {
        val items = listOf(TaskItem("Хлеб"), TaskItem("Молоко", true), TaskItem("с \"кавычками\" и\nпереносом"))
        val back = ListCodec.itemsFromJson(ListCodec.itemsToJson(items))
        assertEquals(items.map { it.text to it.done }, back.map { it.text to it.done })
    }

    @Test
    fun badJsonGivesEmptyList() {
        assertTrue(ListCodec.itemsFromJson("не json").isEmpty())
        assertTrue(ListCodec.itemsFromJson(null).isEmpty())
        assertTrue(ListCodec.itemsFromJson("[1, 2]").isEmpty())
    }

    @Test
    fun limitsAreApplied() {
        val many = (1..1000).map { TaskItem("x".repeat(5000)) }
        val back = ListCodec.itemsFromJson(ListCodec.itemsToJson(many))
        assertEquals(ListCodec.MAX_ITEMS, back.size)
        assertEquals(ListCodec.MAX_TEXT, back[0].text.length)
    }

    @Test
    fun textForSharing() {
        val l = TaskList("Покупки", listOf(TaskItem("Молоко", true), TaskItem("Хлеб"), TaskItem("  ")))
        assertEquals("Покупки\n☐ Хлеб\n☑ Молоко", ListCodec.toText(l))
    }
}
