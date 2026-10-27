package com.hakan.notdefteri.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Xml
import com.hakan.notdefteri.data.Item
import com.hakan.notdefteri.data.ItemType
import com.hakan.notdefteri.data.Repository
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Başka uygulamalardan not içe aktarma:
 * - Google Keep: Google Takeout'tan indirilen ZIP ya da içindeki .json dosyaları
 * - Evernote: .enex dışa aktarma dosyaları
 * - Markdown (.md) ve düz metin (.txt) dosyaları, ayrıca bu uygulamanın kendi ZIP yedeği
 */
object Importer {

    data class Result(val imported: Int, val skipped: Int)

    private class Draft(
        val title: String,
        val content: String,
        val createdAt: Long?,
        val pinned: Boolean = false,
        val photos: List<String> = emptyList(),
        val isTask: Boolean = false,
    )

    fun import(context: Context, uris: List<Uri>): Result {
        val drafts = mutableListOf<Draft>()
        var skipped = 0
        for (uri in uris) {
            val name = displayName(context, uri)
            try {
                val lower = name.lowercase()
                when {
                    lower.endsWith(".zip") -> {
                        val r = importZip(context, uri)
                        drafts += r.first; skipped += r.second
                    }
                    else -> {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: continue
                        val d = parseSingle(name, bytes, emptyMap())
                        if (d.isEmpty()) skipped++ else drafts += d
                    }
                }
            } catch (e: Exception) {
                skipped++
            }
        }
        var nextId = Repository.newId()
        val now = System.currentTimeMillis()
        val items = drafts.map { d ->
            Item(
                id = nextId++,
                type = if (d.isTask) ItemType.TASK else ItemType.NOTE,
                title = d.title.trim(),
                content = d.content.trim(),
                photos = d.photos,
                createdAt = d.createdAt ?: now,
                updatedAt = d.createdAt ?: now,
                pinned = d.pinned,
            )
        }
        if (items.isNotEmpty()) Repository.addAll(items)
        return Result(items.size, skipped)
    }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "dosya"

    /** ZIP: önce resimler dışındaki dosyalar okunur, ardından notların kullandığı resimler kopyalanır. */
    private fun importZip(context: Context, uri: Uri): Pair<List<Draft>, Int> {
        val images = mutableMapOf<String, String>() // zip içindeki dosya adı -> kaydedilen yol
        val texts = mutableListOf<Pair<String, ByteArray>>()
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    val n = e.name
                    val lower = n.lowercase()
                    when {
                        lower.endsWith(".json") || lower.endsWith(".enex") || lower.endsWith(".md") || lower.endsWith(".txt") ->
                            texts += n to zip.readBytes()
                        lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                            lower.endsWith(".webp") || lower.endsWith(".gif") -> {
                            val target = File(Repository.photoDir, "IMG_${UUID.randomUUID()}.${lower.substringAfterLast('.')}")
                            target.outputStream().use { zip.copyTo(it) }
                            images[n] = target.absolutePath
                        }
                    }
                }
            }
        }
        val drafts = mutableListOf<Draft>()
        var skipped = 0
        for ((n, bytes) in texts) {
            val d = try { parseSingle(n, bytes, images) } catch (e: Exception) { emptyList() }
            if (d.isEmpty() && !n.lowercase().endsWith(".json")) skipped++
            drafts += d
        }
        // Hiçbir notun kullanmadığı resimleri sil
        val used = drafts.flatMap { it.photos }.toSet()
        images.values.filterNot { it in used }.forEach { File(it).delete() }
        return drafts to skipped
    }

    /** images: aynı ZIP'teki resimler (yol -> kaydedilen dosya). */
    private fun parseSingle(name: String, bytes: ByteArray, images: Map<String, String>): List<Draft> {
        val lower = name.lowercase()
        val text = String(bytes, Charsets.UTF_8)
        return when {
            lower.endsWith(".enex") -> parseEnex(bytes)
            lower.endsWith(".json") -> listOfNotNull(parseKeep(text, name, images))
            lower.endsWith("labels.txt") && lower.contains("keep/") -> emptyList() // Keep etiket listesi, not değil
            lower.endsWith(".md") || lower.endsWith(".txt") -> listOf(parseText(name, text, images))
            else -> emptyList()
        }
    }

    private fun baseName(path: String) = path.substringAfterLast('/').substringBeforeLast('.')
    private fun folderOf(path: String) = if (path.contains('/')) path.substringBeforeLast('/') + "/" else ""

    /** Markdown/metin dosyası. "# Başlık" ile başlıyorsa o başlık kullanılır; ![](resim) bağlantıları fotoğrafa dönüşür. */
    private fun parseText(name: String, text: String, images: Map<String, String>): Draft {
        var body = text.replace("\r\n", "\n")
        var title = baseName(name)
        val first = body.lineSequence().firstOrNull()
        if (first != null && first.startsWith("# ")) {
            title = first.removePrefix("# ").trim()
            body = body.substringAfter('\n', "")
        }
        // Bu uygulamanın kendi yedeğindeki bilgi satırını at
        body = body.replace(Regex("""^\s*_(Görev|Oluşturma)[^\n]*_\n"""), "")
        val photos = mutableListOf<String>()
        val imgRe = Regex("""!\[[^\]]*]\(([^)]+)\)""")
        body = imgRe.replace(body) { m ->
            val ref = m.groupValues[1]
            val path = images[folderOf(name) + ref] ?: images[ref]
            if (path != null) { photos += path; "" } else m.value
        }
        val isTask = name.contains("Gorevler/")
        return Draft(title, body.trim(), null, photos = photos, isTask = isTask)
    }

    /** Google Keep (Takeout) JSON notu. */
    private fun parseKeep(text: String, name: String, images: Map<String, String>): Draft? {
        val o = try { JSONObject(text) } catch (e: Exception) { return null }
        if (!o.has("textContent") && !o.has("listContent") && !o.has("title")) return null
        if (o.optBoolean("isTrashed", false)) return null
        val content = StringBuilder(o.optString("textContent"))
        o.optJSONArray("listContent")?.let { list ->
            if (content.isNotEmpty()) content.append("\n\n")
            for (i in 0 until list.length()) {
                val li = list.getJSONObject(i)
                content.append(if (li.optBoolean("isChecked")) "- [x] " else "- [ ] ").append(li.optString("text")).append("\n")
            }
        }
        o.optJSONArray("labels")?.let { labels ->
            val names = (0 until labels.length()).mapNotNull { labels.getJSONObject(it).optString("name").ifBlank { null } }
            if (names.isNotEmpty()) content.append("\n\nEtiketler: ").append(names.joinToString(", "))
        }
        val photos = mutableListOf<String>()
        o.optJSONArray("attachments")?.let { att ->
            for (i in 0 until att.length()) {
                val ref = att.getJSONObject(i).optString("filePath")
                // Takeout bazen uzantıyı değiştirir (.jpg / .jpeg), bu yüzden adı uzantısız da dene
                val path = images[folderOf(name) + ref] ?: images.entries.firstOrNull { (k, _) ->
                    k.substringAfterLast('/').substringBeforeLast('.') == ref.substringBeforeLast('.')
                }?.value
                if (path != null) photos += path
            }
        }
        val usec = o.optLong("createdTimestampUsec", 0L).takeIf { it > 0 } ?: o.optLong("userEditedTimestampUsec", 0L)
        return Draft(
            title = o.optString("title"),
            content = content.toString(),
            createdAt = if (usec > 0) usec / 1000 else null,
            pinned = o.optBoolean("isPinned", false),
            photos = photos,
        )
    }

    /** Evernote .enex dosyası (birden çok not içerebilir). */
    private fun parseEnex(bytes: ByteArray): List<Draft> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        try { parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false) } catch (e: Exception) {}
        parser.setInput(ByteArrayInputStream(bytes) as InputStream, "UTF-8")
        val out = mutableListOf<Draft>()
        var title = ""
        var content = ""
        var created: Long? = null
        var photos = mutableListOf<String>()
        var resData: String? = null
        var resMime = ""
        val path = ArrayDeque<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    path.addLast(parser.name)
                    when (parser.name) {
                        "note" -> { title = ""; content = ""; created = null; photos = mutableListOf() }
                        "resource" -> { resData = null; resMime = "" }
                    }
                }
                XmlPullParser.TEXT -> {
                    val t = parser.text ?: ""
                    when (path.lastOrNull()) {
                        "title" -> if (path.contains("note") && !path.contains("resource")) title += t
                        "content" -> content += t
                        "created" -> if (!path.contains("resource")) created = parseEnexDate(t.trim())
                        "data" -> resData = (resData ?: "") + t
                        "mime" -> resMime += t.trim()
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "resource" -> {
                            val data = resData
                            if (data != null && resMime.startsWith("image/")) {
                                try {
                                    val ext = resMime.substringAfter('/').let { if (it == "jpeg") "jpg" else it }
                                    val f = File(Repository.photoDir, "IMG_${UUID.randomUUID()}.$ext")
                                    f.writeBytes(Base64.decode(data.replace(Regex("\\s"), ""), Base64.DEFAULT))
                                    photos.add(f.absolutePath)
                                } catch (e: Exception) {}
                            }
                        }
                        "note" -> out += Draft(title, enmlToMarkdown(content), created, photos = photos)
                    }
                    path.removeLastOrNull()
                }
            }
            event = parser.next()
        }
        return out
    }

    private fun parseEnexDate(s: String): Long? = try {
        SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time
    } catch (e: Exception) {
        null
    }

    /** Evernote'un HTML benzeri içeriğini sade Markdown'a çevirir. */
    fun enmlToMarkdown(html: String): String {
        var s = html
            .replace(Regex("""<\?xml[^>]*\?>"""), "")
            .replace(Regex("""<!DOCTYPE[^>]*>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\n\s*"""), " ")
        s = s.replace(Regex("""<en-todo[^>]*checked="true"[^>]*/?>""", RegexOption.IGNORE_CASE), "\n- [x] ")
            .replace(Regex("""<en-todo[^>]*/?>""", RegexOption.IGNORE_CASE), "\n- [ ] ")
            .replace(Regex("""<h1[^>]*>""", RegexOption.IGNORE_CASE), "\n# ")
            .replace(Regex("""<h2[^>]*>""", RegexOption.IGNORE_CASE), "\n## ")
            .replace(Regex("""<h[3-6][^>]*>""", RegexOption.IGNORE_CASE), "\n### ")
            .replace(Regex("""<(b|strong)(\s[^>]*)?>""", RegexOption.IGNORE_CASE), "**")
            .replace(Regex("""</(b|strong)>""", RegexOption.IGNORE_CASE), "**")
            .replace(Regex("""<(i|em)(\s[^>]*)?>""", RegexOption.IGNORE_CASE), "*")
            .replace(Regex("""</(i|em)>""", RegexOption.IGNORE_CASE), "*")
            .replace(Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE), "\n- ")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<hr\s*/?>""", RegexOption.IGNORE_CASE), "\n---\n")
            .replace(Regex("""<pre[^>]*>""", RegexOption.IGNORE_CASE), "\n```\n")
            .replace(Regex("""</pre>""", RegexOption.IGNORE_CASE), "\n```\n")
            .replace(Regex("""</(div|p|h[1-6]|ul|ol|tr|blockquote)>""", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("""<en-media[^>]*/?>""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""<[^>]+>"""), "")
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
        return s.lines().joinToString("\n") { it.trimEnd() }.replace(Regex("""\n{3,}"""), "\n\n").trim()
    }
}
