package com.hakan.notdefteri.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Metin içinde (ör. fotoğraftan okunan yazıda) tarih ve saat bulur. */
object DateFinder {

    data class Found(val dateTime: LocalDateTime, val hasTime: Boolean, val text: String)

    private val months = listOf(
        "ocak", "şubat", "mart", "nisan", "mayıs", "haziran",
        "temmuz", "ağustos", "eylül", "ekim", "kasım", "aralık"
    )
    // Fotoğraftan okunan yazıda Türkçe harfler bazen kaybolur (Subat, Agustos, Eylul...)
    private val monthAliases: Map<String, Int> = buildMap {
        months.forEachIndexed { i, m ->
            put(m, i + 1)
            put(ascii(m), i + 1)
            put(m.take(3), i + 1)
            put(ascii(m).take(3), i + 1)
        }
    }

    private fun ascii(s: String) = s.replace('ş', 's').replace('ğ', 'g').replace('ı', 'i').replace('ü', 'u')
        .replace('ö', 'o').replace('ç', 'c')

    private fun lowerTr(s: String) = s.replace('I', 'ı').replace('İ', 'i').lowercase()

    private val numeric = Regex("""\b(\d{1,2})[./\-](\d{1,2})[./\-](\d{4}|\d{2})\b""")
    private val iso = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")
    private val named = Regex("""\b(\d{1,2})\s*([a-zçğıöşü]{3,9})\.?(?:\s*(\d{4}))?""")
    private val time = Regex("""\b(?:saat\s*)?([01]?\d|2[0-3])[:.]([0-5]\d)\b""")

    /** Metindeki tüm geçerli tarihleri, yanlarında saat varsa onunla birlikte döndürür. */
    fun findAll(text: String, today: LocalDate = LocalDate.now()): List<Found> {
        val lower = lowerTr(text)
        val out = mutableListOf<Pair<IntRange, Found>>()

        fun add(range: IntRange, date: LocalDate?) {
            if (date == null) return
            if (out.any { it.first.first <= range.last && range.first <= it.first.last }) return
            // Tarihin hemen arkasındaki (30 karakter) saat
            val after = lower.substring(range.last + 1, minOf(lower.length, range.last + 31))
            val t = time.find(after)
            val lt = t?.let { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
            val label = text.substring(range.first, minOf(text.length, range.last + 1)).trim()
            out += range to Found(LocalDateTime.of(date, lt ?: LocalTime.of(9, 0)), lt != null, label)
        }

        for (m in iso.findAll(lower)) {
            add(m.range, safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()))
        }
        for (m in numeric.findAll(lower)) {
            var y = m.groupValues[3].toInt()
            if (m.groupValues[3].length == 2) y += 2000
            add(m.range, safeDate(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()))
        }
        for (m in named.findAll(lower)) {
            val month = monthAliases[m.groupValues[2]] ?: monthAliases[m.groupValues[2].take(3)].takeIf {
                // Kısaltma yalnızca ayın başı olarak geçerli (ör. "kas", "kasım")
                months.any { mo -> mo.startsWith(m.groupValues[2]) || ascii(mo).startsWith(m.groupValues[2]) }
            } ?: continue
            val day = m.groupValues[1].toInt()
            val year = m.groupValues[3].toIntOrNull()
            val date = if (year != null) safeDate(year, month, day) else {
                // Yıl yazmıyorsa: bu yıl geçtiyse gelecek yıl
                safeDate(today.year, month, day)?.let { if (it.isBefore(today)) it.plusYears(1) else it }
            }
            add(m.range, date)
        }
        return out.sortedBy { it.first.first }.map { it.second }
    }

    /** Hatırlatma için en uygun tarih: bugünden sonraki en yakın tarih. Yoksa null. */
    fun findUpcoming(text: String, now: LocalDateTime = LocalDateTime.now()): Found? =
        findAll(text, now.toLocalDate()).filter { it.dateTime.isAfter(now) }.minByOrNull { it.dateTime }

    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        if (y !in 2000..2100) null else runCatching { LocalDate.of(y, m, d) }.getOrNull()
}
