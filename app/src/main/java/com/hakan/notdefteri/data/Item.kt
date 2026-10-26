package com.hakan.notdefteri.data

import org.json.JSONArray
import org.json.JSONObject

enum class ItemType { NOTE, TASK }

/**
 * Not ya da görev. Fotoğraflar uygulamanın kendi klasöründeki dosya yollarıdır.
 * dueAt: hatırlatma zamanı (millis), null ise hatırlatma yok.
 * remindBeforeMinutes: zamanından kaç dakika önce alarm çalsın (0 = önceden alarm yok).
 */
data class Item(
    val id: Int,
    val type: ItemType,
    val title: String = "",
    val content: String = "",
    val photos: List<String> = emptyList(),
    val dueAt: Long? = null,
    val remindBeforeMinutes: Int = 0,
    val alarmAtDue: Boolean = true,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("title", title)
        put("content", content)
        put("photos", JSONArray(photos))
        if (dueAt != null) put("dueAt", dueAt)
        put("remindBeforeMinutes", remindBeforeMinutes)
        put("alarmAtDue", alarmAtDue)
        put("done", done)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Item {
            val photosJson = o.optJSONArray("photos") ?: JSONArray()
            return Item(
                id = o.getInt("id"),
                type = ItemType.valueOf(o.optString("type", ItemType.NOTE.name)),
                title = o.optString("title"),
                content = o.optString("content"),
                photos = List(photosJson.length()) { photosJson.getString(it) },
                dueAt = if (o.has("dueAt")) o.getLong("dueAt") else null,
                remindBeforeMinutes = o.optInt("remindBeforeMinutes", 0),
                alarmAtDue = o.optBoolean("alarmAtDue", true),
                done = o.optBoolean("done", false),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            )
        }
    }
}
