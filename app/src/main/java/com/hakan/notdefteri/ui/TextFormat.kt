package com.hakan.notdefteri.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Düzenleyici araç çubuğu için Markdown biçimlendirme yardımcıları. */
object TextFormat {

    /** Seçili metni işaretlerle sarar; seçim yoksa işaretleri ekleyip imleci araya koyar. Zaten sarılıysa kaldırır. */
    fun wrap(v: TextFieldValue, prefix: String, suffix: String = prefix): TextFieldValue {
        val start = v.selection.min
        val end = v.selection.max
        val text = v.text
        // Zaten sarılıysa işaretleri kaldır
        if (start >= prefix.length && end + suffix.length <= text.length &&
            text.substring(start - prefix.length, start) == prefix && text.substring(end, end + suffix.length) == suffix
        ) {
            val newText = text.removeRange(end, end + suffix.length).removeRange(start - prefix.length, start)
            return v.copy(text = newText, selection = TextRange(start - prefix.length, end - prefix.length))
        }
        val newText = text.substring(0, start) + prefix + text.substring(start, end) + suffix + text.substring(end)
        return v.copy(text = newText, selection = TextRange(start + prefix.length, end + prefix.length))
    }

    /** Seçimin kapsadığı satırların başına işaret ekler; hepsinde varsa kaldırır. */
    fun linePrefix(v: TextFieldValue, prefix: String): TextFieldValue {
        val text = v.text
        val lineStart = if (v.selection.min == 0) 0 else text.lastIndexOf('\n', v.selection.min - 1) + 1
        val lineEnd = text.indexOf('\n', v.selection.max).let { if (it < 0) text.length else it }
        val lines = text.substring(lineStart, lineEnd).split("\n")
        val allHave = lines.all { it.startsWith(prefix) }
        val newLines = lines.map { line ->
            if (allHave) line.removePrefix(prefix)
            else stripListPrefix(line).let { prefix + it }
        }
        val replaced = newLines.joinToString("\n")
        val newText = text.substring(0, lineStart) + replaced + text.substring(lineEnd)
        val cursor = lineStart + replaced.length
        return v.copy(text = newText, selection = TextRange(if (lines.size == 1) cursor else lineStart, cursor))
    }

    private val listPrefix = Regex("""^(#{1,6} |[-*+] \[[ xX]\] |[-*+] |\d+[.)] |> )""")
    private fun stripListPrefix(line: String) = line.replaceFirst(listPrefix, "")

    /** Kod bloğu ekler (seçili metin varsa onu bloğa alır). */
    fun codeBlock(v: TextFieldValue): TextFieldValue {
        val text = v.text
        val start = v.selection.min
        val end = v.selection.max
        val before = if (start > 0 && text[start - 1] != '\n') "\n" else ""
        val selected = text.substring(start, end)
        val block = "$before```\n$selected\n```\n"
        val newText = text.substring(0, start) + block + text.substring(end)
        val cursor = start + before.length + 4 + selected.length
        return v.copy(text = newText, selection = TextRange(cursor))
    }

    /**
     * Enter'a basıldığında liste devam etsin: önceki satır "- " ya da "- [ ] " ile başlıyorsa
     * yeni satıra da aynı işareti ekler; boş madde satırında Enter listeyi bitirir.
     */
    fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        if (new.text.length != old.text.length + 1) return new
        val pos = new.selection.start
        if (pos <= 0 || new.text[pos - 1] != '\n' || !new.selection.collapsed) return new
        val prevStart = new.text.lastIndexOf('\n', pos - 2).let { if (it < 0) 0 else it + 1 }
        val prevLine = new.text.substring(prevStart, pos - 1)
        val m = Regex("""^(\s*)([-*+] \[[ xX]\] |[-*+] |(\d+)([.)]) )(.*)$""").find(prevLine) ?: return new
        val indent = m.groupValues[1]
        val rest = m.groupValues[5]
        if (rest.isBlank()) {
            // Boş madde: işareti sil, listeden çık
            val newText = new.text.substring(0, prevStart) + new.text.substring(pos)
            return new.copy(text = newText, selection = TextRange(prevStart))
        }
        val marker = when {
            m.groupValues[3].isNotEmpty() -> "${m.groupValues[3].toInt() + 1}${m.groupValues[4]} "
            m.groupValues[2].contains("[") -> m.groupValues[2].take(2) + "[ ] "
            else -> m.groupValues[2]
        }
        val insert = indent + marker
        val newText = new.text.substring(0, pos) + insert + new.text.substring(pos)
        return new.copy(text = newText, selection = TextRange(pos + insert.length))
    }
}
