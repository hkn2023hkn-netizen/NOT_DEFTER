package com.hakan.notdefteri.data

import android.content.Context
import com.hakan.notdefteri.alarm.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/**
 * Tüm notlar ve görevler SQLite veritabanında saklanır. Her değişiklik anında diske yazılır.
 * Silinen öğeler önce çöp kutusuna gider ve 30 gün sonra kalıcı olarak silinir.
 */
object Repository {
    private const val TRASH_DAYS = 30L

    private lateinit var appContext: Context
    private lateinit var db: Db
    private val _items = MutableStateFlow<List<Item>>(emptyList())

    /** Çöp kutusundakiler dahil tüm öğeler. */
    val items: StateFlow<List<Item>> = _items

    @Synchronized
    fun init(context: Context) {
        if (::db.isInitialized) return
        appContext = context.applicationContext
        db = Db(appContext)
        migrateFromJson()
        _items.value = db.all()
        purgeOldTrash()
    }

    val photoDir: File
        get() = File(appContext.filesDir, "photos").apply { mkdirs() }

    /** Sürüm 1'deki items.json dosyasını veritabanına taşır. */
    private fun migrateFromJson() {
        val file = File(appContext.filesDir, "items.json")
        if (!file.exists()) return
        try {
            val arr = JSONArray(file.readText())
            val list = List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val photosJson = o.optJSONArray("photos") ?: JSONArray()
                Item(
                    id = o.getInt("id"),
                    type = runCatching { ItemType.valueOf(o.optString("type")) }.getOrDefault(ItemType.NOTE),
                    title = o.optString("title"),
                    content = o.optString("content"),
                    photos = List(photosJson.length()) { photosJson.getString(it) },
                    dueAt = if (o.has("dueAt")) o.getLong("dueAt") else null,
                    remindBeforeMinutes = o.optInt("remindBeforeMinutes", 0),
                    alarmAtDue = o.optBoolean("alarmAtDue", true),
                    done = o.optBoolean("done", false),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            }
            db.putAll(list)
            file.renameTo(File(appContext.filesDir, "items.json.yedek"))
        } catch (e: Exception) {
            // Bozuk dosya: olduğu gibi bırak
        }
    }

    fun get(id: Int): Item? = _items.value.firstOrNull { it.id == id }

    @Synchronized
    fun newId(): Int = (_items.value.maxOfOrNull { it.id } ?: 0) + 1

    /** Kaydeder. Alarm alanları değiştiyse alarmları yeniden kurar. */
    @Synchronized
    fun upsert(item: Item) {
        val old = get(item.id)
        db.put(item)
        _items.value = _items.value.filterNot { it.id == item.id } + item
        val alarmChanged = old == null || old.dueAt != item.dueAt || old.remindBeforeMinutes != item.remindBeforeMinutes ||
            old.alarmAtDue != item.alarmAtDue || old.done != item.done || old.deletedAt != item.deletedAt
        if (alarmChanged) AlarmScheduler.schedule(appContext, item)
    }

    @Synchronized
    fun addAll(newItems: List<Item>) {
        db.putAll(newItems)
        _items.value = _items.value.filterNot { o -> newItems.any { it.id == o.id } } + newItems
        newItems.forEach { AlarmScheduler.schedule(appContext, it) }
    }

    /** Öğedeki artık kullanılmayan fotoğraf dosyalarını siler. */
    fun deleteUnusedPhotos(paths: Collection<String>) {
        val used = _items.value.flatMap { it.photos }.toSet()
        paths.filterNot { it in used }.forEach { File(it).delete() }
    }

    fun moveToTrash(id: Int) {
        val item = get(id) ?: return
        upsert(item.copy(deletedAt = System.currentTimeMillis(), pinned = false))
    }

    fun restore(id: Int) {
        val item = get(id) ?: return
        upsert(item.copy(deletedAt = null))
    }

    @Synchronized
    fun deleteForever(id: Int) {
        val item = get(id) ?: return
        AlarmScheduler.cancel(appContext, item)
        db.remove(id)
        _items.value = _items.value.filterNot { it.id == id }
        deleteUnusedPhotos(item.photos)
    }

    fun emptyTrash() {
        _items.value.filter { it.deletedAt != null }.forEach { deleteForever(it.id) }
    }

    private fun purgeOldTrash() {
        val limit = System.currentTimeMillis() - TRASH_DAYS * 24 * 60 * 60 * 1000
        _items.value.filter { (it.deletedAt ?: Long.MAX_VALUE) < limit }.forEach { deleteForever(it.id) }
    }

    fun setDone(id: Int, done: Boolean) {
        val item = get(id) ?: return
        upsert(item.copy(done = done, updatedAt = System.currentTimeMillis()))
    }

    fun togglePinned(id: Int) {
        val item = get(id) ?: return
        upsert(item.copy(pinned = !item.pinned))
    }

    fun toggleFavorite(id: Int) {
        val item = get(id) ?: return
        upsert(item.copy(favorite = !item.favorite))
    }
}
