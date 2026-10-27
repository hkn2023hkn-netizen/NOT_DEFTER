package com.hakan.notdefteri.data

/** Basit Markdown ayrıştırıcı: ekranda gösterim, HTML ve düz metin dışa aktarımı için ortak. */
sealed class MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
    data class Bullet(val text: String, val indent: Int) : MdBlock()
    data class Numbered(val number: String, val text: String, val indent: Int) : MdBlock()
    /** lineIndex: içerikteki satır numarası (işaretlemeyi değiştirmek için). */
    data class Check(val checked: Boolean, val text: String, val lineIndex: Int, val indent: Int) : MdBlock()
    data class Quote(val text: String) : MdBlock()
    data class Code(val language: String, val code: String) : MdBlock()
    object Rule : MdBlock()
    object Blank : MdBlock()
}

/** Satır içi parça: **kalın**, *italik*, ~~üstü çizili~~, `kod`, [bağlantı](adres). */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

object Markdown {
    private val checkRe = Regex("""^(\s*)[-*+] \[([ xX])\] ?(.*)$""")
    private val bulletRe = Regex("""^(\s*)[-*+] (.*)$""")
    private val numberRe = Regex("""^(\s*)(\d+)[.)] (.*)$""")
    private val headingRe = Regex("""^(#{1,6}) +(.*)$""")
    private val ruleRe = Regex("""^ {0,3}([-*_])( *\1){2,} *$""")

    fun parse(text: String): List<MdBlock> {
        val lines = text.split("\n")
        val out = mutableListOf<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                out += MdBlock.Code(lang, code.toString())
                i++ // kapanış ```
                continue
            }
            val check = checkRe.find(line)
            val heading = headingRe.find(line)
            val number = numberRe.find(line)
            val bullet = bulletRe.find(line)
            out += when {
                line.isBlank() -> MdBlock.Blank
                ruleRe.matches(line) -> MdBlock.Rule
                heading != null -> MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
                check != null -> MdBlock.Check(
                    check.groupValues[2].isNotBlank(), check.groupValues[3], i, check.groupValues[1].length / 2
                )
                bullet != null -> MdBlock.Bullet(bullet.groupValues[2], bullet.groupValues[1].length / 2)
                number != null -> MdBlock.Numbered(number.groupValues[2], number.groupValues[3], number.groupValues[1].length / 2)
                trimmed.startsWith(">") -> MdBlock.Quote(trimmed.removePrefix(">").trimStart())
                else -> MdBlock.Paragraph(line)
            }
            i++
        }
        return out
    }

    /** Satır içi biçimleri parçalara ayırır. */
    fun spans(text: String): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        val buf = StringBuilder()
        var bold = false
        var italic = false
        var strike = false
        var i = 0
        fun flush() {
            if (buf.isNotEmpty()) {
                out += MdSpan(buf.toString(), bold, italic, strike)
                buf.clear()
            }
        }
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> { buf.append(text[i + 1]); i += 2 }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        flush(); out += MdSpan(text.substring(i + 1, end), code = true); i = end + 1
                    } else { buf.append(c); i++ }
                }
                c == '[' -> {
                    val close = text.indexOf("](", i)
                    val end = if (close > 0) text.indexOf(')', close) else -1
                    if (close > i && end > close) {
                        flush()
                        out += MdSpan(text.substring(i + 1, close), bold, italic, strike, link = text.substring(close + 2, end))
                        i = end + 1
                    } else { buf.append(c); i++ }
                }
                text.startsWith("**", i) || text.startsWith("__", i) -> { flush(); bold = !bold; i += 2 }
                text.startsWith("~~", i) -> { flush(); strike = !strike; i += 2 }
                (c == '*' || c == '_') && isItalicMarker(text, i) -> { flush(); italic = !italic; i++ }
                else -> { buf.append(c); i++ }
            }
        }
        flush()
        return out
    }

    // "_" kelime içindeyse (dosya_adi gibi) italik sayılmaz
    private fun isItalicMarker(text: String, i: Int): Boolean {
        if (text[i] == '*') return true
        val before = if (i > 0) text[i - 1] else ' '
        val after = if (i + 1 < text.length) text[i + 1] else ' '
        return !(before.isLetterOrDigit() && after.isLetterOrDigit())
    }

    fun plainInline(text: String) = spans(text).joinToString("") { it.text }

    /** Biçim işaretleri olmadan düz metin. */
    fun toPlain(text: String): String = parse(text).joinToString("\n") { b ->
        when (b) {
            is MdBlock.Heading -> plainInline(b.text)
            is MdBlock.Paragraph -> plainInline(b.text)
            is MdBlock.Bullet -> "  ".repeat(b.indent) + "• " + plainInline(b.text)
            is MdBlock.Numbered -> "  ".repeat(b.indent) + b.number + ". " + plainInline(b.text)
            is MdBlock.Check -> "  ".repeat(b.indent) + (if (b.checked) "☑ " else "☐ ") + plainInline(b.text)
            is MdBlock.Quote -> "│ " + plainInline(b.text)
            is MdBlock.Code -> b.code
            MdBlock.Rule -> "────────"
            MdBlock.Blank -> ""
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun inlineHtml(text: String) = spans(text).joinToString("") { s ->
        var h = esc(s.text)
        if (s.code) h = "<code>$h</code>"
        if (s.bold) h = "<strong>$h</strong>"
        if (s.italic) h = "<em>$h</em>"
        if (s.strike) h = "<del>$h</del>"
        if (s.link != null) h = "<a href=\"${esc(s.link)}\">$h</a>"
        h
    }

    fun toHtml(text: String): String {
        val sb = StringBuilder()
        var list: String? = null
        fun closeList() { if (list != null) { sb.append("</$list>\n"); list = null } }
        fun openList(tag: String) { if (list != tag) { closeList(); sb.append("<$tag>\n"); list = tag } }
        for (b in parse(text)) {
            when (b) {
                is MdBlock.Bullet -> { openList("ul"); sb.append("<li>${inlineHtml(b.text)}</li>\n") }
                is MdBlock.Numbered -> { openList("ol"); sb.append("<li>${inlineHtml(b.text)}</li>\n") }
                is MdBlock.Check -> {
                    openList("ul")
                    sb.append("<li class=\"check\"><input type=\"checkbox\" disabled${if (b.checked) " checked" else ""}> ${inlineHtml(b.text)}</li>\n")
                }
                else -> {
                    closeList()
                    when (b) {
                        is MdBlock.Heading -> sb.append("<h${b.level}>${inlineHtml(b.text)}</h${b.level}>\n")
                        is MdBlock.Paragraph -> sb.append("<p>${inlineHtml(b.text)}</p>\n")
                        is MdBlock.Quote -> sb.append("<blockquote>${inlineHtml(b.text)}</blockquote>\n")
                        is MdBlock.Code -> sb.append("<pre><code>${esc(b.code)}</code></pre>\n")
                        MdBlock.Rule -> sb.append("<hr>\n")
                        else -> {}
                    }
                }
            }
        }
        closeList()
        return sb.toString()
    }

    /** Belirli satırdaki onay kutusunu işaretler/kaldırır. */
    fun toggleCheck(text: String, lineIndex: Int): String {
        val lines = text.split("\n").toMutableList()
        if (lineIndex !in lines.indices) return text
        val m = checkRe.find(lines[lineIndex]) ?: return text
        val checked = m.groupValues[2].isNotBlank()
        val range = m.groups[2]!!.range
        lines[lineIndex] = lines[lineIndex].replaceRange(range, if (checked) " " else "x")
        return lines.joinToString("\n")
    }
}
