package com.hakan.notdefteri.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

/** Notların ve görevlerin tutulduğu yerel SQLite veritabanı. */
class Db(context: Context) : SQLiteOpenHelper(context, "notdefteri.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE items (
                id INTEGER PRIMARY KEY,
                type TEXT NOT NULL,
                title TEXT NOT NULL DEFAULT '',
                content TEXT NOT NULL DEFAULT '',
                photos TEXT NOT NULL DEFAULT '[]',
                due_at INTEGER,
                remind_before INTEGER NOT NULL DEFAULT 0,
                alarm_at_due INTEGER NOT NULL DEFAULT 1,
                done INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                pinned INTEGER NOT NULL DEFAULT 0,
                favorite INTEGER NOT NULL DEFAULT 0,
                locked INTEGER NOT NULL DEFAULT 0,
                deleted_at INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun all(): List<Item> = readableDatabase.query("items", null, null, null, null, null, null).use { c ->
        buildList { while (c.moveToNext()) add(c.toItem()) }
    }

    fun put(item: Item) {
        writableDatabase.insertWithOnConflict("items", null, item.toValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun putAll(items: List<Item>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            items.forEach { db.insertWithOnConflict("items", null, it.toValues(), SQLiteDatabase.CONFLICT_REPLACE) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun remove(id: Int) {
        writableDatabase.delete("items", "id = ?", arrayOf(id.toString()))
    }

    private fun Item.toValues() = ContentValues().apply {
        put("id", id)
        put("type", type.name)
        put("title", title)
        put("content", content)
        put("photos", JSONArray(photos).toString())
        if (dueAt != null) put("due_at", dueAt) else putNull("due_at")
        put("remind_before", remindBeforeMinutes)
        put("alarm_at_due", if (alarmAtDue) 1 else 0)
        put("done", if (done) 1 else 0)
        put("created_at", createdAt)
        put("updated_at", updatedAt)
        put("pinned", if (pinned) 1 else 0)
        put("favorite", if (favorite) 1 else 0)
        put("locked", if (locked) 1 else 0)
        if (deletedAt != null) put("deleted_at", deletedAt) else putNull("deleted_at")
    }

    private fun Cursor.toItem(): Item {
        fun col(name: String) = getColumnIndexOrThrow(name)
        fun longOrNull(name: String) = if (isNull(col(name))) null else getLong(col(name))
        val photosJson = JSONArray(getString(col("photos")))
        return Item(
            id = getInt(col("id")),
            type = runCatching { ItemType.valueOf(getString(col("type"))) }.getOrDefault(ItemType.NOTE),
            title = getString(col("title")),
            content = getString(col("content")),
            photos = List(photosJson.length()) { photosJson.getString(it) },
            dueAt = longOrNull("due_at"),
            remindBeforeMinutes = getInt(col("remind_before")),
            alarmAtDue = getInt(col("alarm_at_due")) == 1,
            done = getInt(col("done")) == 1,
            createdAt = getLong(col("created_at")),
            updatedAt = getLong(col("updated_at")),
            pinned = getInt(col("pinned")) == 1,
            favorite = getInt(col("favorite")) == 1,
            locked = getInt(col("locked")) == 1,
            deletedAt = longOrNull("deleted_at"),
        )
    }
}
