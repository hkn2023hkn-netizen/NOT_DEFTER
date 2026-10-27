package com.hakan.notdefteri.data

enum class ItemType { NOTE, TASK }

/**
 * Not ya da görev. İçerik Markdown biçimindedir.
 * Fotoğraflar uygulamanın kendi klasöründeki dosya yollarıdır.
 * dueAt: hatırlatma zamanı (millis), null ise hatırlatma yok.
 * remindBeforeMinutes: zamanından kaç dakika önce alarm çalsın (0 = önceden alarm yok).
 * locked: true ise başlık ve içerik şifrelidir; [content] AES-256 ile şifrelenmiş veriyi tutar, [title] boştur.
 * deletedAt: null değilse not çöp kutusundadır.
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
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val locked: Boolean = false,
    val deletedAt: Long? = null,
)
