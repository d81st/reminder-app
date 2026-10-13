package com.h4isenb.reminder

import org.json.JSONArray
import org.json.JSONObject

/** Пункт списка дел. key — для интерфейса, в базу не пишется. */
data class TaskItem(val text: String, val done: Boolean = false, val key: Long = Rule.newKey())

/** Список дел без времени, как заметка-чек-лист. */
data class TaskList(
    val title: String = "",
    val items: List<TaskItem> = emptyList(),
    val color: Int = 0,
    val id: Long = 0,
    val updated: Long = 0,
    val key: Long = if (id > 0) id else Rule.newKey(),
) {
    val open: List<TaskItem> get() = items.filter { !it.done }
    val doneCount: Int get() = items.count { it.done }
}

/** Пункты списка в JSON для базы и текст для отправки в мессенджер. Без Android, проверяется тестами. */
object ListCodec {
    const val MAX_ITEMS = 300
    const val MAX_TEXT = 1000
    const val MAX_TITLE = 200

    fun itemsToJson(items: List<TaskItem>): String {
        val a = JSONArray()
        for (it in items) a.put(JSONObject().put("t", it.text).put("d", if (it.done) 1 else 0))
        return a.toString()
    }

    fun itemsFromJson(s: String?): List<TaskItem> {
        if (s.isNullOrEmpty()) return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until minOf(a.length(), MAX_ITEMS)).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                TaskItem(o.optString("t", "").take(MAX_TEXT), o.optInt("d", 0) == 1)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** «Покупки\n☐ хлеб\n☑ молоко» — чтобы отправить список текстом. */
    fun toText(l: TaskList): String = buildString {
        if (l.title.isNotBlank()) append(l.title.trim()).append('\n')
        for (it in l.items.filter { it.text.isNotBlank() }.sortedBy { it.done }) {
            append(if (it.done) "☑ " else "☐ ").append(it.text.trim()).append('\n')
        }
    }.trimEnd()
}
