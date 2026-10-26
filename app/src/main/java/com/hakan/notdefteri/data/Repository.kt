package com.hakan.notdefteri.data

import android.content.Context
import com.hakan.notdefteri.alarm.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import java.io.File

/** Tüm notlar ve görevler tek bir JSON dosyasında, telefonun içinde saklanır. */
object Repository {
    private lateinit var appContext: Context
    private lateinit var file: File
    private val _items = MutableStateFlow<List<Item>>(emptyList())
    val items: StateFlow<List<Item>> = _items

    fun init(context: Context) {
        appContext = context.applicationContext
        file = File(appContext.filesDir, "items.json")
        _items.value = load()
    }

    val photoDir: File
        get() = File(appContext.filesDir, "photos").apply { mkdirs() }

    private fun load(): List<Item> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            List(arr.length()) { Item.fromJson(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun save(list: List<Item>) {
        _items.value = list
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        val tmp = File(file.parentFile, "items.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    fun get(id: Int): Item? = _items.value.firstOrNull { it.id == id }

    fun newId(): Int = (_items.value.maxOfOrNull { it.id } ?: 0) + 1

    fun upsert(item: Item) {
        val list = _items.value.toMutableList()
        val index = list.indexOfFirst { it.id == item.id }
        val old = if (index >= 0) list[index] else null
        if (index >= 0) list[index] = item else list.add(item)
        save(list)
        // Artık kullanılmayan fotoğraf dosyalarını sil
        old?.photos?.filterNot { it in item.photos }?.forEach { File(it).delete() }
        AlarmScheduler.schedule(appContext, item)
    }

    fun delete(id: Int) {
        val item = get(id) ?: return
        AlarmScheduler.cancel(appContext, item)
        item.photos.forEach { File(it).delete() }
        save(_items.value.filterNot { it.id == id })
    }

    fun setDone(id: Int, done: Boolean) {
        val item = get(id) ?: return
        upsert(item.copy(done = done))
    }
}
